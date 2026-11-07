## 1. Core Export & Archive Packaging (.cni)

- [x] 1.1 Implement metadata manifest generator in `BackupManager` calculating entity record counts (patients, sessions, bills, users) and SHA-256 checksum of `clinic.mv.db`.
- [x] 1.2 Implement `.cni` packaging method in `BackupManager` that creates a temporary online H2 backup and zips `clinic.mv.db` with `backup_manifest.properties` into a `.cni` file.
- [x] 1.3 Add helper methods in `BackupManager` to list and inspect local automated backups in `~/clinic/backups/`.

## 2. Core Import, Validation, Legacy SQL/DB Migration & Safe Hot-Swap

- [x] 2.1 Implement `.cni` archive inspector in `BackupManager` to extract and validate `backup_manifest.properties` and verify SHA-256 digest against `clinic.mv.db`.
- [x] 2.2 Implement legacy `.sql` dump and `.db` (SQLite) staging inspector in `BackupManager` / `DatabaseMigrator` that loads `.sql` statements or `.db` files into a temporary SQLite staging database, normalizes legacy table schemas (`Users.userType`, `session.therapyPlanId`, `therapyPlan`, `payment`), and extracts entity counts for pre-import preview.
- [x] 2.3 Implement pre-restore safety snapshot creator in `BackupManager` saving `clinic_pre_restore_<timestamp>.mv.db` before any destructive file or table operations.
- [x] 2.4 Implement atomic hot-swap restore (for `.cni`) and clean-replace transactional migration (for `.sql` and `.db`, including payment ledger backfill, patient total recalculation, and `IDENTITY` sequence reset to `MAX(id) + 1`) with automatic rollback on failure.

## 3. First-Run Workstation Setup Restoration

- [x] 3.1 Update `workstation_setup.fxml` layout to add a dedicated "Restaurer / Importer une base (.cni, .sql, .db)" option with clinic branding and explanation.
- [x] 3.2 Update `WorkstationSetupController.java` to handle `.cni`, `.sql`, and `.db` file selection, validate/stage the package, deploy or migrate into `~/clinic/`, write `app.node_role=HOST` to `config.properties`, and proceed directly to login.

## 4. Admin In-App Backups View & UI

- [x] 4.1 Create `fxml/pages/backup.fxml` with modern responsive layout containing Export card, Restore/Import (`.cni`, `.sql`, `.db`) card, and Local Backup History table.
- [x] 4.2 Create `controller/BackupController.java` with handlers for:
  - Exporting a new `.cni` backup via `FileChooser` with default timestamped filename.
  - Selecting and previewing a `.cni` archive, `.sql` dump, or `.db` SQLite file in an inspection dialog before confirming restoration/import.
  - Executing restoration or SQL/DB import with progress indicator and redirecting to the login screen.
  - Listing automated backups in `~/clinic/backups/` and copying selected backup to external USB drive.

## 5. Sidebar Navigation & Access Control

- [x] 5.1 Add "Sauvegardes" navigation button with database SVG icon to `fxml/home.fxml` sidebar menu below "Utilisateurs".
- [x] 5.2 Update `HomeController.java` to restrict visibility of "Sauvegardes" button exclusively to `Admin` users running on the `HOST` workstation.
- [x] 5.3 Wire navigation handler `handleNavBackups` in `HomeController.java` to load `/fxml/pages/backup.fxml`.

## 6. Verification & End-to-End Validation

- [x] 6.1 Compile project and verify Maven build (`mvn clean test-compile`).
- [x] 6.2 Test `.cni` export operation and verify manifest integrity and SHA-256 checksum.
- [x] 6.3 Test `.cni` restore operation and confirm hot-swap replaces data without file locking errors.
- [x] 6.4 Test legacy `.sql` dump and `.db` SQLite clean-replace import (including legacy 7-column `session` and 4-column `Users` schemas) and verify payment ledger backfill, patient balance recalculation, and H2 sequence reset.
- [x] 6.5 Test invalid/corrupted archive or SQL rejection and verify database remains intact.
- [x] 6.6 Verify first-run setup modal restoration/import flow and client-node access restrictions.
