## ADDED Requirements

### Requirement: Automated Pre-Launch Database Backup
The system SHALL verify the existence of the production database file at application startup and create a timestamped backup before executing any database operations.

#### Scenario: Existing database backup creation
- **WHEN** the application starts and detects `clinic.db` in the clinic directory
- **THEN** it creates a copy named `clinic_backup_<timestamp>.db` inside a dedicated `backups` directory

#### Scenario: First launch with no existing database
- **WHEN** the application starts on a brand-new system where `clinic.db` does not yet exist
- **THEN** it skips the backup step and proceeds directly with initial database creation

### Requirement: Non-Destructive Schema Initialization
The system SHALL ensure all DDL operations preserve existing tables and records without dropping or re-creating tables.

#### Scenario: Startup with populated database
- **WHEN** the application initializes against an existing database populated with patients, sessions, workers, and bills
- **THEN** all existing records remain intact and untouched

### Requirement: User Role Data Normalization
The system SHALL normalize any legacy user records lacking a defined role to ensure uninterrupted administrative access.

#### Scenario: Normalizing legacy admin records
- **WHEN** the database contains user records where `userType` is NULL or empty
- **THEN** the system updates those records to have `userType = 'Admin'` without altering their passwords or identities
