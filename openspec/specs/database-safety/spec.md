# Database Safety Specification

## Purpose
Guarantees database integrity, automated pre-launch and pre-restore backups, cross-engine migration verification, and safe non-destructive schema migrations for the clinic database.

## Requirements

### Requirement: Automated Pre-Launch Database Backup
The system SHALL verify the existence of the production database file at application startup and create a timestamped backup before executing any schema migrations or database modifications.

#### Scenario: Existing SQLite database backup before migration
- **WHEN** the host application starts and detects an existing `clinic.db` prior to H2 migration
- **THEN** it creates a copy named `clinic_backup_<timestamp>.db` inside `~/clinic/backups/` and retains the original SQLite file intact

#### Scenario: Automated H2 database scheduled backup
- **WHEN** the host application terminates or reaches a daily backup interval
- **THEN** it executes H2 `BACKUP TO` to export a compressed archive of `clinic.mv.db` into `~/clinic/backups/`

#### Scenario: First launch with no existing database
- **WHEN** the application starts on a brand-new system where no prior database exists
- **THEN** it skips the backup step and proceeds directly with initial encrypted H2 database creation

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

### Requirement: Transactional Cross-Engine Database Migration
The system SHALL migrate all historical data from the legacy SQLite database to the encrypted H2 database inside a single atomic transaction, rolling back completely if any failure occurs.

#### Scenario: Successful full-table migration
- **WHEN** a legacy SQLite database is detected and H2 is uninitialized
- **THEN** records across `Users`, `worker`, `patient`, `therapyPlan`, `session`, `bill`, `billItem`, `paymentCheck`, and `sold` are copied to H2, committed in a single transaction, and flagged as completed

#### Scenario: Migration rollback upon error
- **WHEN** an exception or type conversion failure occurs during data migration
- **THEN** the H2 transaction is rolled back, partially inserted data is cleared, and the application alerts the user while preserving the untouched SQLite database

### Requirement: Identity Sequence Realignment
The system SHALL explicitly realign all H2 auto-increment identity sequences following record insertion so that new insertions start at `MAX(id) + 1`.

#### Scenario: Auto-increment sequence update after data copy
- **WHEN** all historical rows with existing IDs are inserted into an H2 table
- **THEN** the system executes `ALTER TABLE <table> ALTER COLUMN id RESTART WITH (SELECT COALESCE(MAX(id), 0) + 1 FROM <table>)` for every identity column to prevent key collisions

### Requirement: Row Count Parity Verification
The system SHALL verify that the record count in every table in the H2 database matches the record count in the legacy SQLite database before declaring the migration successful.

#### Scenario: Parity check passes
- **WHEN** all tables have been migrated to H2
- **THEN** the system queries `SELECT COUNT(*)` on both SQLite and H2 for each table; if all counts match, migration is marked complete

#### Scenario: Parity check fails
- **WHEN** any table in H2 has a different count than SQLite
- **THEN** the system aborts the migration, rolls back the transaction, and logs the discrepancy

### Requirement: Idempotent Payment Ledger Backfill
The system SHALL safely backfill existing session and therapy plan payments into the new payment ledger upon application startup without duplicate entries or data loss.

#### Scenario: Backfilling legacy session payments
- **WHEN** the application starts up on a database containing legacy sessions with `paidAmount > 0`
- **THEN** the system inserts corresponding rows into `payment` with `paymentType = 'SESSION'` and `createdBy = 'MIGRATION'` without duplicating any already-migrated entries.

#### Scenario: Backfilling legacy therapy plan payments
- **WHEN** the application starts up on a database containing legacy therapy plans with `cost > 0`
- **THEN** the system inserts corresponding rows into `payment` with `paymentType = 'THERAPY_PLAN'` and `createdBy = 'MIGRATION'`, ensuring patient balances remain identical to legacy balances.

### Requirement: Pre-Restore Safety Snapshot Creation
The system SHALL create a verified pre-restore snapshot copy of the active database before executing any database file replacement, `.cni` restoration, or legacy `.sql`/`.db` import operation.

#### Scenario: Pre-restore snapshot generation
- **WHEN** an administrator confirms a database restoration or import from a `.cni`, `.sql`, or `.db` file
- **THEN** the system creates a copy of `clinic.mv.db` named `clinic_pre_restore_<timestamp>.mv.db` in the `~/clinic/backups/` directory before modifying any active files or tables.

#### Scenario: Aborting restore on snapshot failure
- **WHEN** the system fails to create the pre-restore safety copy (e.g. disk full or permissions error)
- **THEN** the restoration or import is immediately aborted and the active database remains untouched.
