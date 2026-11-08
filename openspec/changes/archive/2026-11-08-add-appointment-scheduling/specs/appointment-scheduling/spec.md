## ADDED Requirements

### Requirement: Appointment Data Persistence and Walk-In Support
The system SHALL persist appointments in an `appointment` table supporting either a registered patient (`patientId`) or a walk-in/phone caller (`guestName` and `guestPhone`), an optional practitioner (`workerId`), a date (`yyyy-MM-dd`), a start time (`HH:mm`), a duration in minutes (defaulting to the clinic's configured default duration, initially 30 minutes), a short `motif`, a lifecycle `status` (`PREVU`, `CONFIRME`, `TERMINE`, `ANNULE`, `ABSENT`), an optional linked `sessionId` (`ON DELETE SET NULL`), `createdBy`, and `createdAt`. Creating, updating, or canceling an appointment SHALL NEVER modify `patient.totalCost`, `patient.totalPaid`, or the `payment` ledger.

#### Scenario: Booking an appointment for a registered patient
- **WHEN** a user creates an appointment linked to an existing `Patient` with date `2026-11-10`, start time `09:30`, duration `30`, motif `Rééducation épaule`, and optional `workerId`
- **THEN** the system stores the appointment with `patientId` populated, `status = 'PREVU'`, `createdBy` set to the active user's username, and leaves the patient's financial balance unchanged

#### Scenario: Booking a phone/walk-in appointment for an unregistered caller
- **WHEN** a user creates an appointment without selecting a registered patient and provides `guestName` and `guestPhone`
- **THEN** the system stores the appointment with `patientId = NULL`, `guestName`, and `guestPhone` populated, and displays an `Invité` indicator in the schedule views

#### Scenario: Creating a registered patient from a walk-in appointment
- **WHEN** a user triggers the "Créer patient" shortcut on a walk-in appointment (`patientId IS NULL`)
- **THEN** the patient creation form opens pre-filled with `guestName` and `guestPhone`, and upon saving, the new `Patient` is persisted and linked to the appointment via `appointment.patientId`

### Requirement: Non-Blocking Overlap Conflict Detection and Multi-Capacity Support
The system SHALL support multiple simultaneous appointments up to the clinic's configured concurrent capacity (`schedule.max_concurrent`, defaulting to 5). Overlap conflicts SHALL only be flagged when:
1. Two active appointments overlap for the exact same assigned practitioner (`workerId`).
2. Two active appointments overlap for the exact same registered patient (`patientId`).
3. Total active concurrent appointments in a time slot exceed the clinic's maximum concurrent capacity.
Multiple appointments for different practitioners or unassigned slots within clinic capacity SHALL NOT trigger conflict warnings. When a genuine conflict occurs, the system SHALL warn the user and allow them to confirm or cancel, rather than blocking the booking.

#### Scenario: Simultaneous appointments across different practitioners or rooms
- **WHEN** multiple users book appointments during the same time interval for different practitioners or within clinic simultaneous capacity (up to 5 concurrent patients)
- **THEN** the system saves all appointments without displaying an overlap warning or blocking dialog

#### Scenario: Warning on same-practitioner double booking
- **WHEN** a user attempts to book an appointment with a practitioner who is already booked for another active appointment on an overlapping time slot
- **THEN** the system displays a confirmation warning identifying the practitioner's conflict and allows proceeding if confirmed

#### Scenario: Warning on clinic concurrent capacity overload
- **WHEN** a user attempts to book an appointment when the number of concurrent active appointments in that time slot has reached or exceeded `max_concurrent`
- **THEN** the system displays a capacity warning and allows proceeding if confirmed

#### Scenario: Ignoring canceled or absent appointments during conflict check
- **WHEN** a user books an appointment during a time slot occupied only by appointments with `status = 'ANNULE'` or `status = 'ABSENT'`
- **THEN** the system saves the appointment without displaying an overlap warning

### Requirement: Daily, Weekly, and Monthly Schedule Views
The system SHALL provide a `Rendez-vous` view implementing `Refreshable` with three switchable modes:
- **Daily View (`Jour`)**: A chronological table ordered by `startTime` displaying time/duration, patient or walk-in guest name, phone number, motif, optional practitioner, status badge, overlap indicator, and quick action buttons (`Confirmer`, `Convertir en séance`, `Absent`, `Annuler`, `Modifier`).
- **Weekly View (`Semaine`)**: A 7-column layout representing the week's days, visually distinguishing non-working days, and displaying chronological appointment cards per day.
- **Monthly View (`Mois`)**: A calendar grid displaying appointment count badges per day, where clicking any day cell navigates to the Daily View for that date.

#### Scenario: Managing daily appointments and quick status transitions
- **WHEN** a user views the Daily schedule for a selected date
- **THEN** all appointments for that date are listed chronologically by `startTime`, and clicking a quick status action updates the appointment status (`PREVU` → `CONFIRME` → `TERMINE`, `ANNULE`, or `ABSENT`) and refreshes the summary counters

#### Scenario: Navigating from Monthly view to Daily view
- **WHEN** a user views the Monthly calendar grid and clicks on a specific day cell
- **THEN** the schedule view switches to the Daily (`Jour`) mode loaded for that selected date

### Requirement: Configurable Clinic Working Days, Hours, and Capacity
The system SHALL store clinic working days (e.g., `SAT,SUN,MON,TUE,WED,THU`), opening time (`start_time`), closing time (`end_time`), default appointment duration (`default_duration_min`), and maximum concurrent patient capacity (`max_concurrent`) in a database-backed `clinic_settings` table so that all LAN workstations share the same configuration.

#### Scenario: Updating clinic working days, hours, and capacity
- **WHEN** a user updates the working days, opening hours, default duration, or simultaneous capacity in the schedule settings dialog
- **THEN** the settings are persisted in `clinic_settings` and reflected across the schedule views and conflict detection on all workstations

### Requirement: One-Click Appointment to Session Conversion
The system SHALL provide a "Convertir en séance" action on unconverted appointments (`sessionId IS NULL`) that opens the session creation dialog pre-filled with the patient and the appointment's `motif` as the treatment description. Saving the session SHALL link `appointment.sessionId` to the newly created `session.id` and update the appointment `status` to `TERMINE`.

#### Scenario: Converting a registered patient's appointment into a session
- **WHEN** a user clicks "Convertir en séance" on an appointment with `patientId > 0` and `sessionId IS NULL`, enters the session cost and cash received in the pre-filled session form, and saves
- **THEN** a new `Session` is created (updating the patient's financial ledger as normal), `appointment.sessionId` is set to the new `session.id`, `appointment.status` is set to `TERMINE`, and the "Convertir en séance" action is disabled for that appointment

#### Scenario: Preventing duplicate conversion of an already-converted appointment
- **WHEN** an appointment already has a non-null `sessionId` pointing to an existing `Session`
- **THEN** the UI displays a linked session indicator and prevents converting the same appointment into a second `Session`

#### Scenario: Re-enabling conversion if linked session is deleted
- **WHEN** a `Session` linked by `appointment.sessionId` is deleted from the system
- **THEN** `appointment.sessionId` is automatically set to `NULL` (`ON DELETE SET NULL`), allowing the appointment to be converted again if needed

### Requirement: Next Appointment Display and Booking in Patient Detail
The system SHALL display the patient's next upcoming active appointment ("Prochain RDV") in the patient detail view (`patient_detail.fxml`) and provide a shortcut button to schedule a new appointment pre-filled with that patient.

#### Scenario: Viewing and scheduling an appointment from Patient Detail
- **WHEN** a user opens the detail view for a patient
- **THEN** the view displays the date and time of the patient's next upcoming appointment (`status` in `PREVU` or `CONFIRME` on or after today, or `"Aucun RDV prévu"` if none exists) and allows clicking "+ Programmer RDV" to book a new appointment for that patient

