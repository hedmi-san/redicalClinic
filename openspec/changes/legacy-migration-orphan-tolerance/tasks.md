## 1. Startup Failure Diagnostics

- [x] 1.1 Create `util.StartupErrorReporter` writing fatal startup errors (stack trace, Java/OS details) to `~/clinic/logs/startup-error.log` with a `%TEMP%\RedicalClinic` fallback, and showing an error dialog with the root cause.
- [x] 1.2 Wrap `com.myerp.Main` startup to report fatal errors and exit with status 1; install a default uncaught-exception handler that logs to the same file.
- [x] 1.3 Add a `-Debug` switch to `build-installer.ps1` that passes `--win-console` to jpackage, and fail the script when jpackage returns a non-zero exit code.
- [x] 1.4 Add `StartupErrorReporterVerificationTest`.

## 2. Orphan-Tolerant Legacy Migration

- [x] 2.1 Add `DatabaseMigrator.OrphanReport` tracking skipped rows per table and detached links, writing `~/clinic/logs/migration-orphans_<timestamp>.log` after a successful commit.
- [x] 2.2 Validate foreign keys against ids already inserted into H2 for `paymentCheck.workerId`, `billItem.billId`, `therapyPlan.patientId`, `session.patientId`, and `session.therapyPlanId`, skipping orphan rows.
- [x] 2.3 Preserve legacy `NULL` foreign keys as `NULL` (not `0`) in `therapyPlan` and `session`.
- [x] 2.4 In `.db`/`.sql` clean-replace imports, skip payments of missing patients and clear `sessionId`/`reversalOf` links to missing rows (payments processed in id order).
- [x] 2.5 Update `verifyParity` to compare H2 counts against SQLite counts minus reported skipped orphans.
- [x] 2.6 Add `LegacyOrphanMigrationVerificationTest` reproducing the field failure (`billItem` → missing `bill` #1) and re-run `BackupExportImportVerificationTest`.

## 3. Deployment

- [ ] 3.1 Rebuild the installer and install it on the customer Host workstation; confirm the application starts and review `~/clinic/logs/migration-orphans_*.log` with the clinic.

