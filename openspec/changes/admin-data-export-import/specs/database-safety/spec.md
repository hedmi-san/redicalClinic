## ADDED Requirements

### Requirement: Pre-Restore Safety Snapshot Creation
The system SHALL create a verified pre-restore snapshot copy of the active database before executing any database file replacement or restoration operation.

#### Scenario: Pre-restore snapshot generation
- **WHEN** an administrator confirms a database restoration from a `.cni` backup file
- **THEN** the system creates a copy of `clinic.mv.db` named `clinic_pre_restore_<timestamp>.mv.db` in the `~/clinic/backups/` directory before modifying any active files.

#### Scenario: Aborting restore on snapshot failure
- **WHEN** the system fails to create the pre-restore safety copy (e.g. disk full or permissions error)
- **THEN** the restoration is immediately aborted and the active database remains untouched.
