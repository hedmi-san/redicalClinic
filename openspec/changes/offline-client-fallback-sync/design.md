## Context

In the current LAN architecture (`DatabaseConfig`, `LoginController`, `ConnectionMonitor`), a `CLIENT` workstation has no local database and connects exclusively to the `HOST` workstation's H2 TCP server (`jdbc:h2:tcp://<hostIp>:9092/~/clinic/clinic`). If the host computer is off when the secretary starts work, the login screen blocks indefinitely (`⏳ En attente du poste du médecin...`). If the host disconnects mid-session, a warning banner is shown and database queries fail until TCP connectivity returns. Furthermore, `UserDAO` currently stores `Users.passWord` in plaintext.

We need the `CLIENT` workstation to operate normally when the `HOST` is absent by falling back to an encrypted local H2 replica (`~/clinic/client_replica.mv.db`), capturing all offline changes without rewriting the 12 existing DAO classes, and automatically flushing offline work to the host before refreshing the local replica when the host returns.

## Goals / Non-Goals

**Goals:**
- Upgrade `Users.passWord` to salted `PBKDF2WithHmacSHA256` hashes with transparent migration of existing plaintext passwords so replicated credentials on client workstations are never stored in plaintext.
- Maintain a local encrypted H2 replica (`~/clinic/client_replica.mv.db`) on `CLIENT` workstations that mirrors the host database while online.
- Split offline confirmation UX by moment:
  - **At Login**: Require an explicit click on a **"Travailler hors-ligne"** button (shown after 2 failed discovery cycles / ~6 seconds, displaying the last sync timestamp) so a slow Wi-Fi handshake never silently puts the user into offline mode.
  - **Mid-Session**: Automatically fail over to the local replica after **3 consecutive failed heartbeat probes** (~12 seconds at a 4-second interval) with an orange banner (`🟠 Mode Hors-Ligne — Modifications sauvegardées localement`).
- Capture offline `INSERT`, `UPDATE`, and `DELETE` operations on the local replica using H2 database triggers (`OfflineChangeTrigger`) writing to an `offline_change_log` table—requiring zero changes to the 12 DAO classes.
- Reconnect safely using a strict **"Flush First, Refresh Second"** pipeline:
  1. Replay append-only inserts (`patient`, `therapyPlan`, `session`, `payment`, `appointment`, `bill`, `billItem`, `sold`, `worker`, `paymentCheck`) in foreign-key dependency order while remapping local temporary IDs to canonical Host IDs.
  2. Apply updates/deletes to existing rows by comparing the current Host row against the replica baseline (`before_json`); if the Host also modified or deleted the row while disconnected, apply **"Host wins, flag the conflict"**.
  3. Recalculate derived financial totals (`patient.totalCost` and `patient.totalPaid`) on the Host for any affected patients.
  4. Pull a fresh snapshot from the Host into `client_replica.mv.db`, switch `DatabaseConfig` back to the Host TCP pool, refresh active UI views, and show a concise conflict report dialog if any concurrent edits conflicted.

**Non-Goals:**
- Peer-to-peer multi-master clustering or external cloud synchronization.
- Rewriting all 12 domain models and DAOs from integer `id` primary keys to UUIDs.
- Allowing offline initialization on a brand-new client workstation that has never connected to the host at least once.

## Decisions

### Decision 1: Salted `PBKDF2WithHmacSHA256` Password Hashing with In-Place Migration
- **Choice**: Introduce `util.PasswordHasher` using standard JDK `javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")` (65,536 iterations, 16-byte random salt, 256-bit key) formatted as `pbkdf2$65536$<base64Salt>$<base64Hash>`.
- **Rationale**: Requires zero external Maven dependencies, resists offline dictionary attacks on replicated client databases, and allows instant detection of legacy plaintext passwords (`!stored.startsWith("pbkdf2$")`).
- **Migration**: `DatabaseInitializer.initializeDatabase()` hashes any non-prefixed `Users.passWord` rows on startup, and `UserDAO.login()` verifies both hashed and legacy passwords (upgrading any legacy plaintext password on successful login).

### Decision 2: Split Connection State Machine (Login Explicit vs. Mid-Session 3-Strike Auto-Switch)
- **Choice**:
  - `DatabaseConfig` manages a `clientOfflineMode` boolean flag. When `AppConfig.isClient() && DatabaseConfig.isOfflineMode()`, `DatabaseConfig.getJdbcUrl()` returns `jdbc:h2:file:~/clinic/client_replica;CIPHER=AES;DATABASE_TO_UPPER=FALSE`.
  - **Login screen (`LoginController`)**: Runs UDP + TCP discovery every 3 seconds. After 2 consecutive failures (~6 seconds), if `OfflineSyncManager.hasInitializedReplica()` is true, displays a **"Travailler hors-ligne (Dernière synchro : ...)"** button. Clicking it activates `DatabaseConfig.setOfflineMode(true)` and enables login against the local replica.
  - **Mid-session (`ConnectionMonitor`)**: Tracks `consecutiveFailures`. A single or double failed probe is ignored to tolerate brief Wi-Fi blips. On the **3rd consecutive failure** (~12 seconds), `ConnectionMonitor` switches `DatabaseConfig.setOfflineMode(true)` (only if a valid local replica exists) and notifies `HomeController` to display the orange offline status banner.
  - **Re-detection (`ConnectionMonitor`)**: While in offline mode, `ConnectionMonitor` continues probing the Host TCP endpoint (and running UDP discovery if TCP fails) in the background every 4 seconds. Upon **2 consecutive successful Host probes**, it invokes `OfflineSyncManager.synchronizeWithHost()`, switches `DatabaseConfig.setOfflineMode(false)`, and notifies `HomeController` on the JavaFX thread with the `SyncResult` (synced count + any conflicts).
- **Alternatives Considered**:
  - *Auto-switching at login*: Rejected because a slow Wi-Fi association at boot would silently log the secretary into a stale replica without their awareness.
  - *Single-probe failover mid-session*: Rejected because a 1-second Wi-Fi hiccup would cause unnecessary mode flapping.

### Decision 3: H2 `Trigger` Journaling + Snapshot Max-ID Tracking on Local Replica
- **Choice**: On `client_replica.mv.db`, create two metadata tables (`offline_change_log` and `offline_snapshot_meta`) and attach an H2 trigger (`config.OfflineChangeTrigger implements org.h2.api.Trigger`) to the synced domain tables (`patient`, `therapyPlan`, `session`, `payment`, `appointment`, `bill`, `billItem`, `sold`, `worker`, `paymentCheck`, `clinic_settings`):
  - `offline_snapshot_meta(table_name VARCHAR(64) PRIMARY KEY, max_id INT)` records the maximum primary key `id` present in each table when the replica was last refreshed from the Host.
  - `offline_change_log(seq_id IDENTITY PRIMARY KEY, table_name VARCHAR(64), op_type VARCHAR(10), row_id VARCHAR(64), before_json TEXT, after_json TEXT, created_at TIMESTAMP)` captures every mutation while `OfflineChangeTrigger.isCaptureEnabled()` is true (disabled while `OfflineSyncManager` is refreshing the replica from the Host).
- **Rationale**: DAOs continue executing standard JDBC `INSERT`, `UPDATE`, and `DELETE` statements with `RETURN_GENERATED_KEYS` against `DatabaseConfig.getConnection()`. Multi-statement transactions (such as `SessionDAO.addSession` inserting a `session`, recording a `payment`, and updating `patient` totals) work identically offline and online.

### Decision 4: "Flush First, Refresh Second" Reconnection Pipeline & Conflict Resolution
- **Choice**: `OfflineSyncManager.synchronizeWithHost()` executes in two strict phases:
  1. **Phase 1 — Flush Offline Queue to Host**:
     - Consolidate `offline_change_log` entries by `(table_name, row_id)` so intermediate edits to a newly created offline row become a single final `INSERT` (or no-op if created and deleted offline), and multiple offline edits to an existing Host row keep the earliest `before_json` (the replica baseline) and the latest `after_json`.
     - **Append-Only New Rows (`id > snapshot_max_id` or `op_type = 'INSERT'`)**: Process in parent-first table order (`worker`, `patient`, `therapyPlan`, `session`, `payment`, `appointment`, `bill`, `billItem`, `sold`, `paymentCheck`). Before inserting into the Host, rewrite foreign-key columns (`patientId`, `therapyPlanId`, `sessionId`, `billId`, `workerId`, `reversalOf`) using an in-memory `idRemap` map (`(table, localId) -> hostId`), and rewrite embedded ID references in `payment.reason` (e.g., `"Paiement séance #" + localSessionId` -> `"Paiement séance #" + hostSessionId`). Record the newly generated Host `id` in `idRemap`.
     - **Edits/Deletes to Existing Rows (`id <= snapshot_max_id`)**: Query the current row on the Host and compare its business columns (excluding derived `patient.totalCost` and `patient.totalPaid`) with `before_json`.
       - If the Host row matches `before_json`, the Host did not modify the row while disconnected: apply the Client's `UPDATE` or `DELETE` on the Host.
       - If the Host row differs from `before_json` (or was deleted on the Host), a true concurrent edit occurred: **Host wins** (the Host's state is preserved) and a `SyncConflict` record (entity label, row ID, client attempted change, host current state) is added to `SyncResult.conflicts()`.
     - **Recalculate Derived Totals**: For every `patientId` touched during the flush, call `new PatientDAO().updatePatientTotals(hostConn, patientId)` on the Host so concurrent session/payment additions on both workstations sum accurately without false conflicts.
  2. **Phase 2 — Refresh Local Replica from Host**:
     - Only after Phase 1 commits on the Host and `offline_change_log` is cleared, `OfflineSyncManager.refreshReplicaFromHost()` suspends trigger capture, copies all canonical tables from the Host connection into `client_replica.mv.db` inside a transaction, updates `offline_snapshot_meta.max_id`, realigns local H2 identity sequences (`ALTER TABLE ... ALTER COLUMN id RESTART WITH ...`), updates `AppConfig` with the latest sync timestamp, and re-enables trigger capture.

## Risks / Trade-offs

- **[Risk] Refreshing the replica while offline changes are still pending would overwrite local work** → **Mitigation**: `refreshReplicaFromHost()` checks `SELECT COUNT(*) FROM offline_change_log` first and refuses to overwrite the replica unless `offline_change_log` is empty (i.e., `flushOfflineQueueToHost()` has already succeeded and committed).
- **[Risk] Clock skew between Host and Client PCs could break timestamp-based conflict detection** → **Mitigation**: Conflict detection compares the Host's current row values directly against `before_json` (the exact row state from the replica baseline before the offline edit) rather than comparing wall-clock timestamps.
- **[Risk] Derived `patient.totalCost` and `patient.totalPaid` columns would falsely conflict if Host adds a session and Client adds a payment for the same patient** → **Mitigation**: Ignore `totalCost` and `totalPaid` in `patient` baseline comparisons and always recalculate them on the Host via `PatientDAO.updatePatientTotals(hostConn, patientId)` after replaying sessions, therapy plans, and payments.
- **[Risk] Brand-new client PC starts offline before ever connecting to the Host** → **Mitigation**: Track `replica.initialized` in `AppConfig`; disable offline mode fallback until at least one initial online sync has populated `client_replica.mv.db`.

