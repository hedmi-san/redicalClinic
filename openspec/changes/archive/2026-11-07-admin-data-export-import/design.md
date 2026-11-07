## Context

Cabinet Nour El Islam operates in a dual-workstation offline LAN setup:
- The **Doctor's Laptop (Host)** holds the primary database file `~/clinic/clinic.mv.db`, runs the embedded H2 TCP Server (port 9092), and manages discovery.
- The **Receptionist's PC (Client)** connects remotely over LAN.

When the doctor formats their host computer, experiences operating system corruption, or purchases a new laptop, all local data is lost unless an external backup exists. Currently, automated local backups exist in `~/clinic/backups/`, but non-technical clinic staff cannot easily move them to external storage or restore them without developer intervention.

This design introduces a self-contained, admin-only Export & Import system using a dedicated `.cni` (*Cabinet Nour El Islam*) archive format. It provides both an in-app management interface and a first-launch restore option to ensure smooth onboarding on freshly formatted machines.

## Goals / Non-Goals

**Goals:**
- **Zero-Friction Doctor Self-Service**: Allow the doctor to back up data to a USB flash drive and restore it on any new/formatted computer without technical assistance.
- **Dedicated `.cni` Package Format**: Encapsulate the H2 database (`clinic.mv.db`) and a metadata manifest (`backup_manifest.properties`) with entity counts and a SHA-256 checksum to prevent corruption and provide pre-import inspection.
- **Legacy `.sql` Dump and `.db` SQLite Import**: Allow seamless migration from older clinic software versions by accepting `.sql` dump scripts and raw `.db` SQLite database files, normalizing older table schemas, backfilling the payment ledger, recalculating patient totals, and resetting H2 identity sequences.
- **Onboarding Restoration**: Allow direct restoration/import from `.cni`, `.sql`, or `.db` on the first-run workstation setup modal (`workstation_setup.fxml`), establishing the node as `HOST` and restoring historical accounts and patients before first login.
- **Admin-Only Host Enforcement**: Expose export/import features exclusively to authenticated `Admin` users running on the `HOST` machine.
- **Atomic Hot-Swap & Rollback**: Safely close active connection pools, stop the H2 TCP server, verify the archive or legacy dataset, back up the active database to a safety copy, swap/migrate the database, and reinitialize connections without triggering Windows file-lock errors (`FileSystemException`).

**Non-Goals:**
- Cloud-based or internet synchronization.
- Remote restore from a Client (Receptionist) PC to the Host PC across the network.
- Granular partial entity exports (e.g. exporting only a single patient).

## Decisions

### 1. Dedicated `.cni` Archive Format with Metadata Manifest (Primary Export/Restore Format)
- **Decision**: Package the encrypted H2 database file (`clinic.mv.db`) along with `backup_manifest.properties` into a ZIP container with a `.cni` file extension.
- **Manifest Details**:
  - `clinic.name=Cabinet Nour El Islam`
  - `app.version=1.6`
  - `backup.timestamp=<ISO-8601>`
  - `backup.sha256=<hex-digest>`
  - `counts.patients=<int>`
  - `counts.sessions=<int>`
  - `counts.bills=<int>`
  - `counts.users=<int>`
- **Rationale**: An opaque `.cni` extension prevents users from inadvertently modifying or extracting files with standard zip tools. The manifest enables instant pre-restore validation and user confidence without decrypting or loading the full database into memory.
- **Alternatives Considered**:
  - *Raw `.zip` with only `clinic.mv.db`*: Rejected because it lacks descriptive metadata, allowing users to accidentally import invalid or corrupted zip archives.

### 2. Staged Legacy `.sql` Dump and `.db` (SQLite) Import Pipeline
- **Decision**: Support importing legacy `.sql` dump files and `.db` SQLite files by staging them through a temporary SQLite database, normalizing legacy schema differences, and executing a clean-replace migration into H2 with payment ledger backfill.
- **Pipeline Steps**:
  1. **Staging & SQL Execution**:
     - If a `.sql` file is selected, create a temporary SQLite staging database (`temp_import_staging.db`).
     - Parse SQL statements (handling multi-line statements, quoted literals, comments, and ignoring SQLite/MySQL transactional or engine wrappers such as `BEGIN TRANSACTION`, `COMMIT`, `PRAGMA`, `sqlite_sequence`).
     - If the `.sql` file contains `INSERT` statements without preceding `CREATE TABLE` statements, pre-initialize the legacy baseline tables so positional inserts (e.g. 7-column `session` or 4-column `Users`) succeed cleanly.
     - If a `.db` file is selected, copy it to `temp_import_staging.db` and verify SQLite header/readability.
  2. **Legacy Schema Normalization & Pre-Import Inspection**:
     - Ensure all expected tables exist in the staging DB and add any columns introduced in newer versions if absent (`Users.userType` defaulting to `'Admin'`, `session.therapyPlanId` defaulting to `NULL`, `therapyPlan`, `payment`).
     - Query entity counts (`patients`, `sessions`, `bills`, `users`) from the staging DB and present the confirmation preview dialog to the administrator before touching the active H2 database.
  3. **Clean-Replace Migration & Payment Ledger Backfill**:
     - Create a pre-restore safety snapshot of the active `clinic.mv.db` (`~/clinic/backups/clinic_pre_restore_<timestamp>.mv.db`).
     - Within an atomic H2 transaction, clear existing H2 tables in foreign-key-safe order, stream all staged records via `DatabaseMigrator` (including `payment` rows if present in the SQL file), seed default admin if `Users` is empty, backfill the `payment` ledger from historical `session.paidAmount` and `therapyPlan.cost` records, recalculate `patient.totalCost` and `patient.totalPaid`, reset all H2 `IDENTITY` sequences to `MAX(id) + 1`, and verify row-count parity.
     - If any error occurs during migration, roll back the transaction (or restore the safety snapshot) so the existing database remains intact.
- **Rationale**: Executing legacy `.sql` dumps directly against an H2 schema fails when old dumps use SQLite-specific syntax (`AUTOINCREMENT`, `sqlite_sequence`) or positional `INSERT INTO session VALUES (...)` statements with fewer columns than the modern schema. Staging in SQLite leverages the existing `sqlite-jdbc` driver and `DatabaseMigrator` parity guarantees.

### 3. Multi-Stage Safe File Replacement & Hot-Swap Engine (for `.cni`)
- **Decision**: Implement a structured restore lifecycle in `BackupManager`:
  1. **Archive Validation**: Extract to a temporary directory, verify manifest presence and SHA-256 checksum of `clinic.mv.db`.
  2. **Pre-Restore Snapshot**: Automatically copy active `clinic.mv.db` to `~/clinic/backups/clinic_pre_restore_<timestamp>.mv.db`.
  3. **Resource Teardown**: Call `DatabaseConfig.resetPool()`, issue `SHUTDOWN` command, and stop `tcpServer`.
  4. **Atomic File Swap**: Replace `~/clinic/clinic.mv.db` with the validated file using `Files.move(..., REPLACE_EXISTING)`.
  5. **Engine Restart**: Re-invoke `DatabaseConfig.startHostServer()`, refresh connection pool, and run `DatabaseInitializer.initializeDatabase()` to ensure all latest tables/migrations are applied.
  6. **UI Refresh**: Clear controller view caches and redirect the user to login or reload the view. If any step fails, roll back to the safety snapshot immediately.
- **Rationale**: Windows strictly forbids overwriting open files. Teardown of the H2 TCP server and connection pool is required before file replacement.

### 4. First-Run Workstation Setup Integration
- **Decision**: Add a "Restaurer / Importer une base (.cni, .sql, .db)" button on `workstation_setup.fxml`.
- **Rationale**: On a formatted or new PC, `config.properties` is absent and the first-run setup wizard appears before the login screen. Permitting restore/import at this step configures `app.node_role=HOST`, restores `.cni` or imports `.sql`/`.db` into `~/clinic/`, and launches directly to the login screen with existing clinic accounts.

### 5. Dedicated "Sauvegardes" Sidebar Navigation Tab
- **Decision**: Add a dedicated "Sauvegardes" menu item in `home.fxml` (visible only when `UserSession.isAdmin() && AppConfig.isHost()`) loading `backup.fxml` and `BackupController.java`.
- **View Features**:
  - Card 1: **Exporter les données (.cni)** (file chooser with suggested name `Sauvegarde_Cabinet_<date>.cni`).
  - Card 2: **Restaurer / Importer des données (.cni, .sql, .db)** (file chooser accepting `.cni`, `.sql`, and `.db` with confirmation modal showing entity stats).
  - Card 3: **Historique des sauvegardes automatiques** (table view of `~/clinic/backups/` with one-click "Copier vers clé USB").

## Risks / Trade-offs

| Risk | Mitigation |
| :--- | :--- |
| **Windows locks `clinic.mv.db` during restore** | `BackupManager` disposes `JdbcConnectionPool` and stops `tcpServer` prior to file operations, running GC if needed to release file handles. |
| **Corrupted or tampered `.cni` file** | Pre-flight validation checks zip structure, manifest keys, and verifies the SHA-256 hash before touching the active database. |
| **Legacy `.sql` dump has older column counts or SQLite syntax** | `.sql` statements are staged into a temporary SQLite database first, normalized with missing modern columns (`therapyPlanId`, `userType`), and then migrated into H2 with payment ledger backfill and sequence realignment. |
| **Restore aborted mid-transfer (e.g. power cut)** | Pre-restore snapshot is written before file replacement or SQL migration. If anything throws, the transaction rolls back or the safety copy is restored. |
| **Unauthorized access / Employee data export** | Export and import options are guarded by `UserSession.isAdmin()` in controller logic and only rendered when running on the `HOST` machine. |
| **Client machine attempts backup/restore** | Backup tab is hidden on Client nodes. If accessed, displays an informational notice that database management is performed on the Host machine. |
