## ADDED Requirements

### Requirement: Encrypted Local Client Database Replica
The system SHALL maintain a local AES-encrypted H2 replica database (`~/clinic/client_replica.mv.db`) on client workstations that mirrors the host database tables (`Users`, `worker`, `paymentCheck`, `bill`, `billItem`, `patient`, `therapyPlan`, `session`, `sold`, `payment`, `appointment`, `clinic_settings`) whenever connected to the host.

#### Scenario: Initial and periodic replica refresh while online
- **WHEN** a client workstation successfully connects to the host database and has no pending offline changes
- **THEN** the system copies the current state of all clinic tables from the host into `~/clinic/client_replica.mv.db`, realigns local auto-increment sequences, records the snapshot maximum IDs, and saves the last synchronization timestamp

#### Scenario: Refusing replica overwrite when un-flushed offline changes exist
- **WHEN** a replica refresh is requested while `offline_change_log` contains pending offline entries
- **THEN** the system flushes all pending offline changes to the host first before refreshing the local replica so offline work is never overwritten

### Requirement: Trigger-Based Offline Mutation Journaling
The system SHALL automatically record all `INSERT`, `UPDATE`, and `DELETE` operations performed on the client's local replica while in offline mode into an ordered `offline_change_log` table with `before_json` and `after_json` snapshots, without requiring modifications to individual DAO queries.

#### Scenario: Recording offline creation and linked records
- **WHEN** a user working in offline mode creates a new patient and adds a session and payment for that patient
- **THEN** the local replica commits the rows using local auto-increment IDs and records the ordered `INSERT` entries in `offline_change_log`

#### Scenario: Recording baseline state on offline update or delete
- **WHEN** a user working in offline mode updates or deletes an existing record that originated from the host
- **THEN** `offline_change_log` stores the pre-modification column values in `before_json` and the new column values in `after_json`

### Requirement: Flush-First Synchronization with Foreign-Key ID Remapping
The system SHALL flush all pending offline mutations to the host database in foreign-key dependency order before refreshing the local replica when the host becomes reachable again, remapping local temporary IDs to canonical host-generated IDs across all child records.

#### Scenario: Syncing offline-created parent and child records when host also created records
- **WHEN** a client creates a new patient (`localId = 50`), a therapy plan, a session, a payment, and an appointment while offline, and the host independently created another patient with `id = 50` during the disconnection
- **THEN** upon reconnection the system inserts the client's new patient into the host to obtain a new canonical `hostId` (e.g., `51`), remaps `patientId` (and any `therapyPlanId` or `sessionId`) on the dependent offline records to `51`, inserts them without ID collision, and recalculates `totalCost` and `totalPaid` on the host

#### Scenario: Merging concurrent append-only financial entries for the same existing patient
- **WHEN** the host records a new session for existing patient `#10` while the offline client records a payment for patient `#10`
- **THEN** upon reconnection the client's payment is inserted on the host for patient `#10` and `patient.totalCost` and `patient.totalPaid` are recalculated from the combined sessions, therapy plans, and payments without conflict

### Requirement: Baseline-Compared Conflict Resolution (Host Wins, Flag Conflict)
The system SHALL detect concurrent edits to existing records by comparing the current host row against the replica baseline (`before_json`) captured before the client's offline edit; if the host row was modified or deleted independently, the host version SHALL prevail and the conflict SHALL be reported to the user.

#### Scenario: Non-conflicting offline edit to existing row
- **WHEN** a client modifies an existing patient's phone number or an appointment's status while offline, and that row on the host still matches `before_json` upon reconnection
- **THEN** the system applies the client's update directly to the host row without flagging a conflict

#### Scenario: Conflicting concurrent edit to the same existing row
- **WHEN** a client edits an existing appointment or patient row while offline, and the same row was also modified or deleted on the host during the disconnection (`hostRow != before_json`)
- **THEN** the system preserves the host's version ("Host wins"), logs a conflict summary item, refreshes the local replica with the host's state, and displays a conflict report dialog listing the overridden local changes to the user

