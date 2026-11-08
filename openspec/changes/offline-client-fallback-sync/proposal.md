## Why

Currently, a `CLIENT` workstation (such as the secretary's desk) connects exclusively over TCP to the `HOST` workstation's H2 server and cannot log in or operate if the doctor's computer is turned off, asleep, or temporarily disconnected from the Wi-Fi LAN. Enabling the client to fall back to a local encrypted replica, record data normally while offline, and automatically synchronize changes back to the host when it returns ensures uninterrupted clinic operations at reception.

## What Changes

- **Salted Password Hashing (`PBKDF2WithHmacSHA256`)**: Upgrade `UserDAO` and `DatabaseInitializer` so `Users.passWord` stores salted PBKDF2 hashes (`pbkdf2$iterations$salt$hash`) instead of plaintext, transparently migrating existing plaintext passwords on startup and login before replicating `Users` to client workstations.
- **Encrypted Local Client Replica (`~/clinic/client_replica.mv.db`)**: Maintain a local AES-encrypted H2 replica on `CLIENT` workstations that mirrors the host database whenever online, enabling offline login and full lookup of patients, appointments, therapy plans, sessions, and payments.
- **Split Offline Confirmation UX (Login vs. Mid-Session)**:
  - **At Login**: After a short discovery timeout (~6 seconds / 2 failed probes), surface an explicit **"Travailler hors-ligne"** button (displaying the last sync timestamp) so the secretary makes a deliberate choice to log into the local replica rather than being silently routed offline by a slow Wi-Fi handshake. (Disabled if the client has never synced with the host before).
  - **Mid-Session**: Require **3 consecutive failed heartbeat probes** (~12 seconds at a 4-second interval) before automatically switching `DatabaseConfig` to the local replica and showing an orange offline status banner (`🟠 Mode Hors-Ligne — Modifications sauvegardées localement`), avoiding flapping on brief Wi-Fi blips.
- **Zero-DAO-Rewrite Offline Mutation Journaling**: Attach H2 database triggers (`OfflineChangeTrigger`) on the client's local replica tables to automatically capture `INSERT`, `UPDATE`, and `DELETE` operations in a local `offline_change_log` table with `before_json` and `after_json` snapshots.
- **"Flush First, Refresh Second" Automatic Reconnection Sync**:
  - When `ConnectionMonitor` detects the host is reachable again (2 consecutive successful probes), run `OfflineSyncManager` before switching back to online mode:
    1. **Flush Offline Queue to Host**: Replay append-only inserts (`patient`, `therapyPlan`, `session`, `payment`, `appointment`, `bill`, `billItem`, `sold`, `worker`, `paymentCheck`) in foreign-key dependency order while remapping local temporary IDs to canonical Host IDs, apply non-conflicting updates/deletes by comparing the Host row against `before_json`, resolve concurrent edit conflicts using **"Host wins, flag the conflict"**, and recompute derived `patient.totalCost` and `patient.totalPaid` on the Host.
    2. **Refresh Local Replica from Host**: Pull the canonical Host state into `client_replica.mv.db`, realign local sequences, and clear `offline_change_log`.
    3. **Switch Pool & Notify UI**: Switch `DatabaseConfig` back to the Host TCP connection pool, refresh the active view, hide the offline banner, and display a conflict report dialog if any concurrent edits were flagged.

## Capabilities

### New Capabilities
- `offline-client-sync`: Local client database replica lifecycle, trigger-based offline mutation journaling, foreign-key ID remapping for offline inserts, baseline-compared conflict detection ("Host wins, flag the conflict"), and "flush first, refresh second" automatic synchronization.

### Modified Capabilities
- `lan-connectivity`: Add explicit "Travailler hors-ligne" action on the login screen after discovery timeout, 3-strike consecutive failure threshold before automatic mid-session offline fallback, and automatic background host re-detection triggering data synchronization.
- `user-management`: Require salted password hashing (`PBKDF2WithHmacSHA256`) for user credentials with transparent in-place migration of legacy plaintext passwords so replicated user tables never expose plaintext passwords.

## Impact

- **Database & Configuration (`config`)**: `DatabaseConfig` (dual pool/routing between Host TCP URL and Client local replica URL, offline state flag), `DatabaseInitializer` (salted password migration, replica schema & sync metadata tables), `AppConfig` (tracking `replica.last_sync` and `replica.initialized`).
- **Sync & Network (`network`, `sync` / `config`)**: `ConnectionMonitor` (3-strike failure counter, background host probe while in offline mode, triggering sync on return), new `OfflineSyncManager` and `OfflineChangeTrigger` for change capture, ID remapping, conflict resolution, and replica refresh.
- **Authentication & UI (`dao.UserDAO`, `controller.LoginController`, `controller.HomeController`, `login.fxml`, `home.fxml`)**: `PasswordHasher` utility in `util`, `UserDAO` hash verification, "Travailler hors-ligne" button on `login.fxml`, orange offline/syncing banner and conflict report modal in `HomeController`.
- **Verification**: New standalone verification test `com.myerp.OfflineSyncVerificationTest` testing password hashing migration, offline inserts with foreign-key ID remapping, concurrent edit conflict detection ("Host wins"), and "flush first, refresh second" recovery against disposable H2 databases.

