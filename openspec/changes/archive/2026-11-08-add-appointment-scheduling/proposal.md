## Why

The clinic currently records completed and billed therapy sessions (`Séances`), but lacks a dedicated system to schedule, view, and manage upcoming patient appointments (`Rendez-vous`). Receptionists and administrators need a shared, LAN-synchronized appointment calendar (Daily, Weekly, and Monthly views) with walk-in phone booking support, conflict warnings, and one-click conversion into completed sessions without prematurely affecting patient financial balances.

## What Changes

- **Database Schema (`appointment` & `clinic_settings` tables)**:
  - Add an `appointment` table supporting registered patients (`patientId` nullable) and phone/walk-in guests (`guestName`, `guestPhone`), optional practitioner assignment (`workerId` nullable), date (`yyyy-MM-dd`), start time (`HH:mm`), duration in minutes (default 30), short `motif`, lifecycle `status` (`PREVU`, `CONFIRME`, `TERMINE`, `ANNULE`, `ABSENT`), linked `sessionId` (nullable, `ON DELETE SET NULL`), `createdBy`, and `createdAt`.
  - Add a `clinic_settings` table in H2 to persist clinic-wide working days, opening/closing hours, and default appointment duration across all LAN workstations and `.cni` backups.
- **Sidebar Navigation (`Rendez-vous` tab)**:
  - Add a new `Rendez-vous` navigation tab in `home.fxml` and `HomeController` visible and fully accessible to both `Admin` and `Employé` accounts, implementing `Refreshable` for instant LAN synchronization.
- **Daily, Weekly, and Monthly Schedule Views (`RendezVousController`)**:
  - **Daily View**: Chronological table of the selected day's appointments with status badges, overlap indicators, quick status transition actions (`Prévu` → `Confirmé` → `Terminé`, `Annulé`, `Absent`), walk-in "Créer fiche patient" shortcut, and one-click "Convertir en séance".
  - **Weekly View**: 7-day column layout highlighting configured working days and displaying chronological appointment cards per day.
  - **Monthly View**: Calendar grid displaying daily appointment count badges; clicking any day navigates to the Daily view for that date.
  - **Working Hours Configuration**: Modal dialog accessible from the schedule header to configure clinic working days (e.g., Saturday–Thursday, Friday off) and working hours.
- **Booking & Conflict Detection (`AppointmentFormController`)**:
  - Support selecting an existing patient via debounced search or entering walk-in guest details (`guestName` + `guestPhone`) with an inline quick-create patient option.
  - Detect overlapping active appointments (`PREVU`, `CONFIRME`, `TERMINE`) on the same date (and practitioner, if specified) and display a non-blocking confirmation warning before saving.
- **Appointment-to-Session Conversion & Patient Detail Integration**:
  - One-click "Convertir en séance" pre-fills `SessionFormController` with the patient (prompting/creating the patient record first if the appointment is still a walk-in guest) and sets the appointment's `motif` as the session treatment. Saving the session links `appointment.sessionId` and sets status to `TERMINE`, preventing double conversion while ensuring appointments themselves never alter `patient.totalCost` or the payment ledger.
  - Add a "Prochain RDV" indicator and a "+ Programmer RDV" shortcut inside `patient_detail.fxml` / `PatientDetailController`.

## Capabilities

### New Capabilities
- `appointment-scheduling`: Manages patient and walk-in appointment booking, non-blocking overlap conflict detection, lifecycle status tracking (`PREVU`, `CONFIRME`, `TERMINE`, `ANNULE`, `ABSENT`), Daily/Weekly/Monthly schedule views, configurable clinic working days/hours, one-click conversion into a `Session`, and upcoming appointment integration in the patient detail view.

### Modified Capabilities
- `role-based-access`: Grants both `Admin` and `Employé` roles full visibility and operational access to the new `Rendez-vous` navigation tab (creating, rescheduling, updating status, converting to session, and canceling appointments) with `createdBy` attribution.

## Impact

- **Database & Configuration**: `DatabaseInitializer.java`, `DatabaseMigrator.java` (schema initialization and indexes for `appointment` and `clinic_settings`).
- **Models & DAOs**: New `model.Appointment`, `dao.AppointmentDAO`, and `dao.ClinicSettingsDAO`.
- **Controllers & Views**:
  - Updated `HomeController.java` and `home.fxml` (new `btnRendezVous` sidebar entry).
  - New `RendezVousController.java`, `AppointmentFormController.java`, `ClinicHoursDialogController.java` (or inline dialog), and corresponding FXML/CSS under `myerp/src/main/resources/fxml/pages/` and `css/pages/`.
  - Updated `SessionFormController.java` (helper method to pre-populate treatment/date from an appointment) and `PatientDetailController.java` / `patient_detail.fxml` ("Prochain RDV" display and booking trigger).
- **Verification**: New standalone verification test `AppointmentVerificationTest.java` under `myerp/src/test/java/com/myerp/`.

