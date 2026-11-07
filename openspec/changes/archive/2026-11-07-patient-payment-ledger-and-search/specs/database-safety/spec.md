## ADDED Requirements

### Requirement: Idempotent Payment Ledger Backfill
The system SHALL safely backfill existing session and therapy plan payments into the new payment ledger upon application startup without duplicate entries or data loss.

#### Scenario: Backfilling legacy session payments
- **WHEN** the application starts up on a database containing legacy sessions with `paidAmount > 0`
- **THEN** the system inserts corresponding rows into `payment` with `paymentType = 'SESSION'` and `createdBy = 'MIGRATION'` without duplicating any already-migrated entries.

#### Scenario: Backfilling legacy therapy plan payments
- **WHEN** the application starts up on a database containing legacy therapy plans with `cost > 0`
- **THEN** the system inserts corresponding rows into `payment` with `paymentType = 'THERAPY_PLAN'` and `createdBy = 'MIGRATION'`, ensuring patient balances remain identical to legacy balances.
