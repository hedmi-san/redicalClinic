## Why

When the clinic operates across a local network (LAN), all persistent medical, financial, and administrative records reside exclusively on the Host (Doctor/Server) machine. If the clinic doctor formats their computer, experiences hardware failure, or upgrades to a new PC, all data is at risk of loss unless the software provides an intuitive, foolproof export and import mechanism. 

Currently, while automated local backups are created on disk, non-technical clinic staff cannot easily migrate or restore their database without developer assistance. Adding a dedicated, self-contained export and import feature—restricted to administrators on the Host machine—empowers the doctor to maintain external USB backups and independently restore their entire clinic on a new or freshly formatted workstation without external intervention.

## What Changes

- **Dedicated Archive Format (`.cni`)**: Introduce a specialized `.cni` (*Cabinet Nour El Islam*) archive packaging the encrypted H2 database (`clinic.mv.db`) alongside a structured `backup_manifest.properties` file containing clinic metadata, export timestamp, record counts, and SHA-256 integrity checksum.
- **Admin-Only In-App "Sauvegardes" Navigation Tab**: Add a dedicated "Sauvegardes" view to the left sidebar menu (restricted to `Admin` users on the `HOST` node) providing:
  - One-click **Exportation** to any selected destination (e.g. USB flash drive) with automatic timestamped file naming.
  - Safe **Restauration** from a `.cni` file with manifest pre-flight verification, entity count preview, and pre-restore rollback backup creation.
  - Local backup history browser showing existing automated backups with a one-click "Copier vers clé USB" action.
- **First-Run Workstation Setup Integration**: Enhance the initial workstation setup modal dialog (`workstation_setup.fxml`) to offer an immediate **"Restaurer depuis une sauvegarde (.cni)"** option. On a clean or newly formatted PC, the doctor can restore directly from a USB stick before first login, automatically configuring the PC as `HOST` and restoring all patient, session, and user credentials.
- **Hot-Swap & Safe Rollback Engine**: Safely close connection pools, stop the embedded H2 TCP server, verify archive integrity, create an emergency safety copy of current data, apply the restored database, and restart the server seamlessly or prompt for re-authentication.

## Capabilities

### New Capabilities
- `data-backup-export-import`: Covers `.cni` archive generation, metadata manifest verification, UI-driven export and import workflows for administrators on the Host workstation, and first-run setup wizard restoration.

### Modified Capabilities
- `database-safety`: Extends database safety guarantees to include manual `.cni` export, SHA-256 checksum verification, and automatic pre-restore safety snapshots before any database overwrite.

## Impact

- **UI & Navigation**:
  - `home.fxml` & `HomeController.java`: New "Sauvegardes" sidebar navigation item visible only to `Admin` on `HOST`.
  - New `backup.fxml` & `BackupController.java`: Dedicated view for export, restore, and local backup management.
  - `workstation_setup.fxml` & `WorkstationSetupController.java`: New action to restore an existing `.cni` backup file during initial post-format onboarding.
- **Configuration & Database**:
  - `config/BackupManager.java` & `config/DatabaseConfig.java`: Methods for online `.cni` packaging, manifest generation, safe database disconnect/reconnect, and rollback restoration.
- **Dependencies**: No external dependencies needed; utilizes built-in Java standard library (`java.util.zip`, `java.security.MessageDigest`) and existing H2 database engine.
