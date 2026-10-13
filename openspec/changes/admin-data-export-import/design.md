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
- **Onboarding Restoration**: Allow direct restoration from `.cni` on the first-run workstation setup modal (`workstation_setup.fxml`), establishing the node as `HOST` and restoring historical accounts and patients before first login.
- **Admin-Only Host Enforcement**: Expose export/import features exclusively to authenticated `Admin` users running on the `HOST` machine.
- **Atomic Hot-Swap & Rollback**: Safely close active connection pools, stop the H2 TCP server, verify the archive, back up the active database to a safety copy, swap the database files, and reinitialize connections without triggering Windows file-lock errors (`FileSystemException`).

**Non-Goals:**
- Cloud-based or internet synchronization.
- Remote restore from a Client (Receptionist) PC to the Host PC across the network.
- Granular partial entity exports (e.g. exporting only a single patient).

## Decisions

### 1. Dedicated `.cni` Archive Format with Metadata Manifest
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
  - *SQL dump script (`SCRIPT TO` / `RUNSCRIPT FROM`)*: Rejected because SQL execution across large historical datasets is slower and prone to schema divergence and identity sequence conflicts compared to verified binary MVStore snapshots.

### 2. Multi-Stage Safe File Replacement & Hot-Swap Engine
- **Decision**: Implement a structured restore lifecycle in `BackupManager`:
  1. **Archive Validation**: Extract to a temporary directory, verify manifest presence and SHA-256 checksum of `clinic.mv.db`.
  2. **Pre-Restore Snapshot**: Automatically copy active `clinic.mv.db` to `~/clinic/backups/pre_restore_safety_<timestamp>.mv.db`.
  3. **Resource Teardown**: Call `DatabaseConfig.resetPool()`, issue `SHUTDOWN` command, and stop `tcpServer`.
  4. **Atomic File Swap**: Replace `~/clinic/clinic.mv.db` with the validated file using `Files.move(..., REPLACE_EXISTING)`.
  5. **Engine Restart**: Re-invoke `DatabaseConfig.startHostServer()`, refresh connection pool, and verify readability.
  6. **UI Refresh**: Clear controller view caches and redirect the user to login or reload the view. If any step fails, roll back to the safety snapshot immediately.
- **Rationale**: Windows strictly forbids overwriting open files. Teardown of the H2 TCP server and connection pool is required before file replacement.

### 3. First-Run Workstation Setup Integration
- **Decision**: Add a "Restaurer depuis une sauvegarde (.cni)" button on `workstation_setup.fxml`.
- **Rationale**: On a formatted or new PC, `config.properties` is absent and the first-run setup wizard appears before the login screen. Permitting restore at this step configures `app.node_role=HOST`, extracts `clinic.mv.db`, and launches directly to the login screen with existing clinic accounts.

### 4. Dedicated "Sauvegardes" Sidebar Navigation Tab
- **Decision**: Add a dedicated "Sauvegardes" menu item in `home.fxml` (visible only when `UserSession.isAdmin() && AppConfig.isHost()`) loading `backup.fxml` and `BackupController.java`.
- **View Features**:
  - Card 1: **Exporter les données** (file chooser with suggested name `Sauvegarde_Cabinet_<date>.cni`).
  - Card 2: **Restaurer les données** (file chooser with confirmation modal showing manifest stats).
  - Card 3: **Historique des sauvegardes automatiques** (table view of `~/clinic/backups/` with one-click "Copier vers clé USB").

## Risks / Trade-offs

| Risk | Mitigation |
| :--- | :--- |
| **Windows locks `clinic.mv.db` during restore** | `BackupManager` disposes `JdbcConnectionPool` and stops `tcpServer` prior to file operations, running GC if needed to release file handles. |
| **Corrupted or tampered `.cni` file** | Pre-flight validation checks zip structure, manifest keys, and verifies the SHA-256 hash before touching the active database. |
| **Restore aborted mid-transfer (e.g. power cut)** | Pre-restore snapshot is written before file replacement. If anything throws, the safety copy is restored before server restart. |
| **Unauthorized access / Employee data export** | Export and import options are guarded by `UserSession.isAdmin()` in controller logic and only rendered when running on the `HOST` machine. |
| **Client machine attempts backup/restore** | Backup tab is hidden on Client nodes. If accessed, displays an informational notice that database management is performed on the Host machine. |
