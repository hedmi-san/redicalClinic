## Why

When the clinic operates across a local network (LAN), all persistent medical, financial, and administrative records reside exclusively on the Host (Doctor/Server) machine. If the clinic doctor formats their computer, experiences hardware failure, or upgrades to a new PC, all data is at risk of loss unless the software provides an intuitive, foolproof export and import mechanism. 

Currently, while automated local backups are created on disk, non-technical clinic staff cannot easily migrate or restore their database without developer assistance. Adding a dedicated, self-contained export and import feature—restricted to administrators on the Host machine—empowers the doctor to maintain external USB backups and independently restore their entire clinic on a new or freshly formatted workstation without external intervention.

## What Changes

- **Dedicated Archive Format (`.cni`)**: Introduce a specialized `.cni` (*Cabinet Nour El Islam*) archive packaging the encrypted H2 database (`clinic.mv.db`) alongside a structured `backup_manifest.properties` file containing clinic metadata, export timestamp, record counts, and SHA-256 integrity checksum.
- **Legacy `.sql` Dump and `.db` (SQLite) Import Support**: Allow administrators to import data directly from legacy `.sql` dump scripts or `.db` SQLite database files originating from older versions of the clinic application:
  - Parses and stages legacy `.sql` scripts (supporting both full DDL+DML dumps and data-only `INSERT` dumps, as well as older table schemas lacking `therapyPlanId`, `userType`, or `payment`).
  - Performs a clean-replace migration into H2 after taking an automatic pre-restore safety backup, automatically backfilling the `payment` ledger, recalculating patient balances, and resetting all H2 identity sequences (`MAX(id) + 1`).
- **Admin-Only In-App "Sauvegardes" Navigation Tab**: Add a dedicated "Sauvegardes" view to the left sidebar menu (restricted to `Admin` users on the `HOST` node) providing:
  - One-click **Exportation** to any selected destination (e.g. USB flash drive) with automatic timestamped file naming.
  - Safe **Restauration / Importation** from `.cni` archives, legacy `.sql` dump files, or legacy `.db` SQLite files with pre-flight inspection, entity count preview, and pre-restore rollback backup creation.
  - Local backup history browser showing existing automated backups with a one-click "Copier vers clé USB" action.
- **First-Run Workstation Setup Integration**: Enhance the initial workstation setup modal dialog (`workstation_setup.fxml`) to offer an immediate **"Restaurer / Importer une base (.cni, .sql, .db)"** option. On a clean or newly formatted PC, the doctor can restore directly from a `.cni` backup or import an old `.sql`/`.db` database before first login, automatically configuring the PC as `HOST` and restoring all patient, session, payment, and user records.
- **Hot-Swap & Safe Rollback Engine**: Safely close connection pools, stop the embedded H2 TCP server, verify archive/script integrity, create an emergency safety copy of current data, apply the restored or imported database, and restart the server seamlessly or prompt for re-authentication.

## Capabilities

### New Capabilities
- `data-backup-export-import`: Covers `.cni` archive generation, metadata manifest verification, legacy `.sql` and `.db` staging and migration with payment ledger backfill, UI-driven export and import workflows for administrators on the Host workstation, and first-run setup wizard restoration.

### Modified Capabilities
- `database-safety`: Extends database safety guarantees to include manual `.cni` export, SHA-256 checksum verification, legacy `.sql`/`.db` validation and sequence realignment, and automatic pre-restore safety snapshots before any database overwrite.

## Impact

- **UI & Navigation**:
  - `home.fxml` & `HomeController.java`: New "Sauvegardes" sidebar navigation item visible only to `Admin` on `HOST`.
  - New `backup.fxml` & `BackupController.java`: Dedicated view for `.cni` export, `.cni`/`.sql`/`.db` restore/import, and local backup management.
  - `workstation_setup.fxml` & `WorkstationSetupController.java`: New action to restore an existing `.cni` backup or import a legacy `.sql`/`.db` file during initial post-format onboarding.
- **Configuration & Database**:
  - `config/BackupManager.java`, `config/DatabaseMigrator.java`, & `config/DatabaseConfig.java`: Methods for online `.cni` packaging, manifest generation, `.sql` script staging and legacy schema normalization, safe database disconnect/reconnect, and rollback restoration.
- **Dependencies**: No new external dependencies needed; utilizes built-in Java standard library (`java.util.zip`, `java.security.MessageDigest`), existing `sqlite-jdbc` driver for legacy staging, and existing H2 database engine.
