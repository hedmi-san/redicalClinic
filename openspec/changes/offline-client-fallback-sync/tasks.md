## 1. Salted Password Hashing & Credential Migration

- [x] 1.1 Create `util.PasswordHasher` using `PBKDF2WithHmacSHA256` (65,536 iterations, 16-byte random salt, 256-bit key) with `hash(String)`, `verify(String, String)`, and `isHashed(String)` (`pbkdf2$...` prefix check).
- [x] 1.2 Update `dao.UserDAO` (`seedDefaultAdmin`, `addUser`, `updateUser`, `login`) to store salted PBKDF2 hashes and transparently upgrade any legacy plaintext password upon successful login.
- [x] 1.3 Update `config.DatabaseInitializer` to migrate any existing plaintext `Users.passWord` entries to salted PBKDF2 hashes during database initialization.

## 2. Local Replica Routing & Offline Trigger Journaling

- [x] 2.1 Update `config.AppConfig` to persist `replica.initialized` and `replica.last_sync` properties.
- [x] 2.2 Update `config.DatabaseConfig` to support client offline mode routing (`jdbc:h2:file:~/clinic/client_replica;CIPHER=AES;DATABASE_TO_UPPER=FALSE`), direct `getHostConnection()` and `getReplicaConnection()` helpers, and `testHostConnection()` independent of the currently active pool.
- [x] 2.3 Create `config.OfflineChangeTrigger` (`implements org.h2.api.Trigger`) to capture `INSERT`, `UPDATE`, and `DELETE` events on the local replica into `offline_change_log(seq_id, table_name, op_type, row_id, before_json, after_json, created_at)` whenever trigger capture is active.
- [x] 2.4 Initialize replica schema, `offline_change_log`, `offline_snapshot_meta(table_name, max_id)`, and table triggers when opening the client replica database.

## 3. Flush-First Synchronization & Conflict Resolution Engine

- [x] 3.1 Create `config.OfflineSyncManager` with `hasInitializedReplica()` and `hasPendingOfflineChanges()` checks.
- [x] 3.2 Implement `OfflineSyncManager.flushOfflineQueueToHost(Connection hostConn, Connection replicaConn)`:
  - Consolidate `offline_change_log` entries per `(table_name, row_id)`.
  - Replay append-only `INSERT` rows in foreign-key dependency order (`worker`, `patient`, `therapyPlan`, `session`, `payment`, `appointment`, `bill`, `billItem`, `sold`, `paymentCheck`), remapping local IDs (`id > max_id`) across foreign-key columns and payment reason strings.
  - Apply `UPDATE` and `DELETE` operations on existing rows (`id <= max_id`) by comparing current Host row values against `before_json`; if modified on Host, preserve Host state ("Host wins") and record a `SyncConflict` entry.
  - Recalculate `patient.totalCost` and `patient.totalPaid` on the Host for all affected patients and clear `offline_change_log`.
- [x] 3.3 Implement `OfflineSyncManager.refreshReplicaFromHost(Connection hostConn, Connection replicaConn)`:
  - Enforce precondition that `offline_change_log` is empty before overwriting local tables.
  - Suspend trigger capture, copy all clinic tables (`Users`, `worker`, `paymentCheck`, `bill`, `billItem`, `patient`, `therapyPlan`, `session`, `sold`, `payment`, `appointment`, `clinic_settings`), record `offline_snapshot_meta.max_id`, realign H2 identity sequences, update `AppConfig` sync metadata, and re-enable trigger capture.
- [x] 3.4 Implement `OfflineSyncManager.synchronizeWithHost()` to execute Phase 1 (Flush) followed by Phase 2 (Refresh) atomically and return a `SyncResult` summary.

## 4. Split UX: Login Explicit Offline Button & Mid-Session 3-Strike Monitor

- [x] 4.1 Update `login.fxml` and `controller.LoginController` to track consecutive failed discovery probes and reveal a **"Travailler hors-ligne"** button (with last sync timestamp) after 2 failed probes (~6 seconds) when `OfflineSyncManager.hasInitializedReplica()` is true; trigger initial replica sync upon successful online login.
- [x] 4.2 Update `network.ConnectionMonitor` to:
  - Require **3 consecutive failed Host probes** (~12 seconds) while online before switching `DatabaseConfig` to offline replica mode.
  - Continue probing the Host in the background while in offline mode (using UDP discovery + `DatabaseConfig.testHostConnection()`), and after **2 consecutive successful probes**, run `OfflineSyncManager.synchronizeWithHost()` and switch `DatabaseConfig` back to online mode.
- [x] 4.3 Update `home.fxml` and `controller.HomeController` to display an orange offline mode banner (`🟠 Mode Hors-Ligne — Modifications sauvegardées localement (synchronisation automatique dès le retour du serveur)`), refresh active views upon automatic reconnection, and show a summary dialog if `SyncResult` contains any resolved conflicts.

## 5. Verification & End-to-End Testing

- [x] 5.1 Create `myerp/src/test/java/com/myerp/OfflineSyncVerificationTest.java` verifying:
  - Salted PBKDF2 password hashing and legacy plaintext password migration.
  - Initial replica population and refusal to overwrite replica when un-flushed offline changes exist.
  - Offline creation of linked records (`patient` -> `therapyPlan` -> `session` -> `payment` -> `appointment`) with concurrent Host inserts causing ID collisions, verifying clean foreign-key ID remapping and accurate `patient.totalCost` / `totalPaid` totals.
  - Concurrent edit conflict detection on an existing row ("Host wins, flag the conflict") vs. non-conflicting offline edit application.
  - 3-strike consecutive failure threshold before automatic mid-session failover.
- [x] 5.2 Run `mvn test-compile` and execute `OfflineSyncVerificationTest` against a disposable temporary directory to confirm all assertions pass.

