## 1. Database Schema & Settings Persistence

- [x] 1.1 Update `DatabaseInitializer.java` to create the `appointment` table (`id`, `patientId`, `guestName`, `guestPhone`, `workerId`, `date`, `startTime`, `durationMin`, `motif`, `status`, `sessionId`, `createdBy`, `createdAt`) with foreign keys (`patient(id) ON DELETE CASCADE`, `worker(id) ON DELETE SET NULL`, `session(id) ON DELETE SET NULL`) and indexes (`idx_appointment_date`, `idx_appointment_patientId`, `idx_appointment_sessionId`).
- [x] 1.2 Update `DatabaseInitializer.java` to create the `clinic_settings` table (`settingKey VARCHAR(64) PRIMARY KEY, settingValue TEXT NOT NULL`) and seed default values (`schedule.working_days = 'SAT,SUN,MON,TUE,WED,THU'`, `schedule.start_time = '08:00'`, `schedule.end_time = '17:00'`, `schedule.default_duration_min = '30'`).
- [x] 1.3 Create `dao.ClinicSettingsDAO` with helpers to read and update working days (`Set<DayOfWeek>`), opening time (`LocalTime`), closing time (`LocalTime`), and default duration (`int`).

## 2. Domain Model & Appointment DAO

- [x] 2.1 Create `model.Appointment` with fields, status constants (`STATUS_PREVU`, `STATUS_CONFIRME`, `STATUS_TERMINE`, `STATUS_ANNULE`, `STATUS_ABSENT`), display helpers (resolved patient/guest name, resolved phone, time range formatting, walk-in indicator), and getters/setters.
- [x] 2.2 Create `dao.AppointmentDAO` with CRUD operations (`addAppointment`, `updateAppointment`, `updateStatus`, `deleteAppointment`, `linkPatientToAppointment`, `markConvertedToSession`), date queries (`getAppointmentsByDate`, `getAppointmentsByDateRange`, `getNextUpcomingAppointmentForPatient`), and overlap detection (`findOverlappingAppointments`).

## 3. Appointment Form & Session Conversion Integration

- [x] 3.1 Create `fxml/pages/appointment_form.fxml`, `css/pages/appointment_form.css`, and `controller.AppointmentFormController` supporting registered patient search (debounced popup) or walk-in mode (`guestName` + `guestPhone` + inline quick patient creation), date picker, start time and duration selectors, optional practitioner (`Worker`) combo box, motif field, status selector, and non-blocking overlap conflict warning on save.
- [x] 3.2 Update `controller.SessionFormController` (and `PatientFormController` if needed) with pre-fill helper methods so converting an appointment into a session pre-selects the patient, date, and treatment (`motif`), and creating a patient from a walk-in pre-fills `guestName` and `guestPhone`.

## 4. Rendez-vous Schedule Views & Navigation

- [x] 4.1 Create `fxml/pages/rendez_vous.fxml`, `css/pages/rendez_vous.css`, and `controller.RendezVousController` (implementing `Refreshable`) with view-mode toggle (`Jour`, `Semaine`, `Mois`), date navigation (`◀`, period title, `▶`, `Aujourd'hui`), status/practitioner filters, summary counters, and a `⚙ Horaires` configuration dialog for clinic working days and hours.
- [x] 4.2 Implement the **Daily View (`Jour`)** in `RendezVousController`: chronological `TableView<Appointment>` with overlap warnings, walk-in `"Créer patient"` shortcut, quick status buttons (`Confirmer`, `Absent`, `Annuler`), and one-click `"Convertir en séance"` linking `appointment.sessionId`.
- [x] 4.3 Implement the **Weekly View (`Semaine`)** and **Monthly View (`Mois`)** in `RendezVousController`: 7-column weekly board with muted off-days and appointment cards, plus a monthly calendar grid with daily appointment count badges that switch to the Daily view when clicked.
- [x] 4.4 Update `fxml/home.fxml` and `controller.HomeController` to add the `Rendez-vous` sidebar button (`btnRendezVous`) visible and accessible to both `Admin` and `Employé` roles.

## 5. Patient Detail Integration & Verification

- [x] 5.1 Update `fxml/pages/patient_detail.fxml` and `controller.PatientDetailController` to display the patient's `"Prochain RDV"` badge/info and a `"+ Programmer RDV"` button that opens `AppointmentFormController` pre-filled with that patient.
- [x] 5.2 Create `myerp/src/test/java/com/myerp/AppointmentVerificationTest.java` verifying registered and walk-in appointment creation, financial balance isolation (zero impact prior to session conversion), overlap conflict detection, walk-in to patient linking, one-click session conversion (`sessionId` linkage and `ON DELETE SET NULL` behavior), `clinic_settings` persistence, and next appointment lookup.

