## Why

On a customer Host workstation the packaged application failed to start with only a generic jpackage "Failed to launch JVM" dialog. The underlying cause was the boot-time SQLite → H2 migration aborting with `Referential integrity constraint violation: billItem FOREIGN KEY(billId) REFERENCES bill(id) (1)`. The legacy application used SQLite without foreign-key enforcement, so deleting a bill (or worker, patient, therapy plan) left child rows pointing at a missing parent. H2 enforces these constraints, so the migration rolled back on every launch and the clinic could not use the application at all.

## What Changes

- **Orphan-tolerant migration**: `DatabaseMigrator` validates every foreign key against the rows already inserted into H2. Rows whose owning parent no longer exists are skipped (matching the schema's `ON DELETE CASCADE` semantics); optional links to missing rows (`payment.sessionId`, `payment.reversalOf`) are cleared (`ON DELETE SET NULL` semantics). Legacy SQL `NULL` foreign keys are preserved as `NULL` instead of being coerced to `0`.
- **Orphan report**: every skipped or detached row is written, with its identifying values, to `~/clinic/logs/migration-orphans_<timestamp>.log`. The original `clinic.db` and the `clinic_pre_migration_*.db` backup remain untouched.
- **Parity check accounts for reported orphans**: row-count parity now requires `H2 count = SQLite count − skipped orphans` per table; any other discrepancy still aborts and rolls back the migration.
- **Visible startup failures**: fatal startup exceptions are written to `~/clinic/logs/startup-error.log` and shown in an error dialog instead of the generic jpackage "Failed to launch JVM" message. `build-installer.ps1 -Debug` builds a console-enabled diagnostic installer.

## Capabilities

### Modified Capabilities
- `database-safety`: Cross-engine migration tolerates legacy orphan rows with a written report, and parity verification accounts for the reported orphans.

## Impact

- **Code**: `config.DatabaseMigrator` (boot migration and `.db`/`.sql` clean-replace import), new `util.StartupErrorReporter`, `com.myerp.Main`, `build-installer.ps1`.
- **Data**: Orphan rows that reference deleted parents are not imported into H2; they remain available in the legacy `clinic.db`, its pre-migration backup, and the orphan report. No schema changes.
- **Verification**: `LegacyOrphanMigrationVerificationTest`, `StartupErrorReporterVerificationTest`; existing `BackupExportImportVerificationTest` still passes.

