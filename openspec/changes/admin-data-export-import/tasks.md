## 1. Core Export & Archive Packaging (.cni)

- [ ] 1.1 Implement metadata manifest generator in `BackupManager` calculating entity record counts (patients, sessions, bills, users) and SHA-256 checksum of `clinic.mv.db`.
- [ ] 1.2 Implement `.cni` packaging method in `BackupManager` that creates a temporary online H2 backup and zips `clinic.mv.db` with `backup_manifest.properties` into a `.cni` file.
- [ ] 1.3 Add helper methods in `BackupManager` to list and inspect local automated backups in `~/clinic/backups/`.

## 2. Core Import, Validation & Safe Hot-Swap

- [ ] 2.1 Implement `.cni` archive inspector in `BackupManager` to extract and validate `backup_manifest.properties` and verify SHA-256 digest against `clinic.mv.db`.
- [ ] 2.2 Implement pre-restore safety snapshot creator in `BackupManager` saving `clinic_pre_restore_<timestamp>.mv.db` before any destructive file operations.
- [ ] 2.3 Implement atomic hot-swap restore in `BackupManager` that safely closes `DatabaseConfig` connection pool, shuts down H2 TCP server, swaps `clinic.mv.db`, and restarts the server with rollback on failure.

## 3. First-Run Workstation Setup Restoration

- [ ] 3.1 Update `workstation_setup.fxml` layout to add a dedicated "Restaurer depuis une sauvegarde (.cni)" option with clinic branding and explanation.
- [ ] 3.2 Update `WorkstationSetupController.java` to handle `.cni` file selection, validate the package, deploy `clinic.mv.db` into `~/clinic/`, write `app.node_role=HOST` to `config.properties`, and proceed directly to login.

## 4. Admin In-App Backups View & UI

- [ ] 4.1 Create `fxml/pages/backup.fxml` with modern responsive layout containing Export card, Import card, and Local Backup History table.
- [ ] 4.2 Create `controller/BackupController.java` with handlers for:
  - Exporting a new `.cni` backup via `FileChooser` with default timestamped filename.
  - Selecting and previewing a `.cni` backup in an inspection dialog before confirming restoration.
  - Executing restoration with progress indicator and redirecting to the login screen.
  - Listing automated backups in `~/clinic/backups/` and copying selected backup to external USB drive.

## 5. Sidebar Navigation & Access Control

- [ ] 5.1 Add "Sauvegardes" navigation button with database SVG icon to `fxml/home.fxml` sidebar menu below "Utilisateurs".
- [ ] 5.2 Update `HomeController.java` to restrict visibility of "Sauvegardes" button exclusively to `Admin` users running on the `HOST` workstation.
- [ ] 5.3 Wire navigation handler `handleNavBackups` in `HomeController.java` to load `/fxml/pages/backup.fxml`.

## 6. Verification & End-to-End Validation

- [ ] 6.1 Compile project and verify Maven build (`mvn clean test-compile`).
- [ ] 6.2 Test `.cni` export operation and verify manifest integrity and SHA-256 checksum.
- [ ] 6.3 Test `.cni` restore operation and confirm hot-swap replaces data without file locking errors.
- [ ] 6.4 Test invalid/corrupted archive rejection and verify database remains intact.
- [ ] 6.5 Verify first-run setup modal restoration flow and client-node access restrictions.
