## ADDED Requirements

### Requirement: Immutable Append-Only Payment Ledger
The system SHALL record all patient cash inflows and outflows in an append-only `payment` table without physical updates or deletions of existing records.

#### Scenario: Recording cash payment for a session
- **WHEN** a worker records a session with actual cash received
- **THEN** the system creates a `payment` row referencing the session with `paymentType = 'SESSION'`, business date, timestamp, and the authenticated worker's username.

#### Scenario: Recording a session covered by advance credit
- **WHEN** a worker records a session for a patient with sufficient advance credit and 0 cash received
- **THEN** the system saves the session with its treatment cost and does not create an unnecessary 0-cash payment row, automatically reducing the patient's advance credit.

### Requirement: Patient Balance and Daily Cash Register Derivation
The system SHALL derive patient account balances from the difference between total treatment charges and total payment entries, and calculate daily cash registers solely from actual payments on that date.

#### Scenario: Calculating patient balance
- **WHEN** a patient has session and therapy plan charges totaling 3,000 DA and payment entries totaling 4,500 DA
- **THEN** the system calculates a balance of -1,500 DA indicating 1,500 DA in advance credit.

#### Scenario: Daily cash drawer reconciliation
- **WHEN** an administrator views the daily cash register for a given date
- **THEN** the system displays the net sum of all payment rows dated on that day, itemized by payment type (Session, Deposit, Settlement, Refund, Reversal).

### Requirement: Free Deposits and Debt Settlements
The system SHALL allow recording standalone payments directly from the patient profile without requiring the creation of a treatment session.

#### Scenario: Adding an advance deposit
- **WHEN** a user records a deposit of 5,000 DA from the patient profile
- **THEN** the system creates an unlinked `payment` row with `paymentType = 'DEPOSIT'`, immediately credits the patient's balance, and includes the amount in today's cash register.

### Requirement: Audited Reversals and Refunds
The system SHALL provide audited reversal and refund operations guarded by administrator privileges, creating explicit compensating entries.

#### Scenario: Reversing an erroneous payment
- **WHEN** an administrator reverses an existing payment entry with a mandatory reason
- **THEN** the system generates a negative compensating `payment` row referencing the original payment id (`reversalOf`) dated today, and marks the original entry as reversed.

#### Scenario: Preventing duplicate reversals
- **WHEN** a user attempts to reverse a payment that has already been reversed
- **THEN** the system rejects the operation and displays an error message.

#### Scenario: Issuing a cash refund
- **WHEN** an administrator issues a cash refund to a patient who has available advance credit
- **THEN** the system verifies the refund does not exceed the available credit, creates a negative `payment` row with `paymentType = 'REFUND'`, and decreases both the credit balance and today's cash register.
