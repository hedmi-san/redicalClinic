## ADDED Requirements

### Requirement: Admin Export to .cni Package
The system SHALL allow an authenticated administrator on the Host workstation to export all clinic data into a standalone `.cni` compressed archive containing the encrypted database file and an integrity manifest.

#### Scenario: Successful export from backups view
- **WHEN** an administrator on the Host machine selects "Exporter la base de données" and chooses a target destination path
- **THEN** the system generates an online backup of `clinic.mv.db`, computes its SHA-256 hash and entity counts (patients, sessions, bills, users), bundles them with `backup_manifest.properties` into a `.cni` archive, and displays a success notification with the full destination path.

#### Scenario: Non-admin or client node restriction
- **WHEN** a user logged in as an employee or running on a Client workstation attempts to access the export feature
- **THEN** the system prevents access and does not allow export operations.

### Requirement: Manifest Inspection and Pre-Restore Validation
The system SHALL inspect and validate any selected `.cni` file prior to modifying or replacing the active clinic database.

#### Scenario: Valid archive preview
- **WHEN** an administrator selects a valid `.cni` file for restoration
- **THEN** the system extracts the manifest, verifies the SHA-256 hash against the bundled database file, and displays an inspection modal showing the backup timestamp and entity counts before asking for confirmation.

#### Scenario: Corrupted or invalid archive rejection
- **WHEN** a user selects an unreadable, altered, or non-CNI file for restoration
- **THEN** the system rejects the operation, displays an informative error dialog, and leaves the current database completely untouched.

### Requirement: Atomic Hot-Swap and Safe Database Restoration
The system SHALL perform database restoration safely by creating a pre-restore safety copy, stopping active database handles, replacing the database file, and restarting the database engine with automatic rollback on error.

#### Scenario: Successful restoration from in-app backups view
- **WHEN** an administrator confirms restoration of a verified `.cni` file
- **THEN** the system creates a safety backup of the active database in `~/clinic/backups/`, safely closes the connection pool, stops the H2 TCP server, atomically swaps `clinic.mv.db`, restarts the database engine, and redirects the administrator to the login screen with restored accounts.

#### Scenario: Rollback upon unexpected swap failure
- **WHEN** an error occurs during file replacement or engine restart
- **THEN** the system automatically restores the pre-restore safety copy, reinitializes the database engine, and alerts the user of the rollback.

### Requirement: First-Run Workstation Setup Restoration
The system SHALL allow restoring an existing `.cni` backup directly from the initial workstation setup screen before any login or fresh database initialization occurs.

#### Scenario: Restoring on a clean or formatted PC
- **WHEN** the application starts on an unconfigured system and the user clicks "Restaurer depuis une sauvegarde (.cni)" on the workstation setup dialog
- **THEN** the system validates the selected `.cni` file, deploys `clinic.mv.db` into `~/clinic/`, writes `app.node_role=HOST` into `~/clinic/config.properties`, closes the setup dialog, and launches the login screen with all restored clinic data and users.

### Requirement: Local Automated Backup Management
The system SHALL display the history of automated database backups stored in the clinic directory and allow one-click copying to external storage.

#### Scenario: Copying an automated backup to external drive
- **WHEN** an administrator clicks "Copier vers une clé USB" for a listed automated backup
- **THEN** the system opens a directory or file chooser and copies the selected backup file to the chosen destination.
