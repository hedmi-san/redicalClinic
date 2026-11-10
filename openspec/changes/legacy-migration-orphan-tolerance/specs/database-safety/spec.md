## MODIFIED Requirements

### Requirement: Transactional Cross-Engine Database Migration
The system SHALL migrate all historical data from the legacy SQLite database to the encrypted H2 database inside a single atomic transaction, rolling back completely if any failure occurs. Because the legacy database did not enforce foreign keys, the system SHALL migrate rows as if the H2 foreign-key rules had always applied: rows whose owning parent no longer exists SHALL be skipped, optional references to missing rows SHALL be cleared, and every skipped or cleared row SHALL be recorded in an orphan report.

#### Scenario: Successful full-table migration
- **WHEN** a legacy SQLite database is detected and H2 is uninitialized
- **THEN** records across `Users`, `worker`, `patient`, `therapyPlan`, `session`, `bill`, `billItem`, `paymentCheck`, and `sold` are copied to H2, committed in a single transaction, and flagged as completed

#### Scenario: Legacy database contains orphan child rows
- **WHEN** the legacy SQLite database contains rows referencing a deleted parent (for example a `billItem` whose `billId` has no matching `bill`, a `paymentCheck` without its `worker`, a `therapyPlan` or `session` without its `patient`, or a `session` whose `therapyPlan` is missing)
- **THEN** those rows are not inserted into H2, all valid rows are migrated, the migration commits successfully, and the application starts normally

#### Scenario: Optional references to missing rows
- **WHEN** an imported `payment` references a `session` or an original payment (`reversalOf`) that does not exist
- **THEN** the payment is imported with that reference set to `NULL` and the cleared link is recorded in the orphan report

#### Scenario: Legacy NULL foreign keys
- **WHEN** a legacy row has a `NULL` value in a nullable foreign-key column such as `session.patientId` or `therapyPlan.patientId`
- **THEN** the value is stored as `NULL` in H2 rather than being converted to `0`

#### Scenario: Orphan report written
- **WHEN** the migration skips or detaches at least one orphan row
- **THEN** the system writes `~/clinic/logs/migration-orphans_<timestamp>.log` listing each affected table, row id, and identifying values, and leaves the original `clinic.db` and its pre-migration backup intact

#### Scenario: Migration rollback upon error
- **WHEN** an exception or type conversion failure occurs during data migration
- **THEN** the H2 transaction is rolled back, partially inserted data is cleared, and the application alerts the user while preserving the untouched SQLite database

### Requirement: Row Count Parity Verification
The system SHALL verify that the record count in every table in the H2 database matches the record count in the legacy SQLite database, minus the orphan rows deliberately skipped and recorded in the orphan report, before declaring the migration successful.

#### Scenario: Parity check passes
- **WHEN** all tables have been migrated to H2
- **THEN** the system queries `SELECT COUNT(*)` on both SQLite and H2 for each table; if every H2 count equals the SQLite count minus that table's reported skipped orphans, migration is marked complete

#### Scenario: Parity check fails
- **WHEN** any table in H2 has a count different from the SQLite count minus that table's reported skipped orphans
- **THEN** the system aborts the migration, rolls back the transaction, and logs the discrepancy

## ADDED Requirements

### Requirement: Visible Startup Failure Diagnostics
The system SHALL record any fatal exception raised during application startup to `~/clinic/logs/startup-error.log` (falling back to the system temporary directory) and SHALL display an error dialog describing the root cause, instead of terminating with only the native launcher's generic error.

#### Scenario: Startup fails on a packaged installation
- **WHEN** the packaged application throws an uncaught exception during startup (for example a migration or database initialization failure)
- **THEN** the full stack trace with Java and OS details is appended to `startup-error.log`, an error dialog shows the root cause and the log location, and the application exits with a non-zero status

