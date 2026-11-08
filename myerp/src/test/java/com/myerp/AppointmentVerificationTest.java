package com.myerp;

import config.AppConfig;
import config.DatabaseConfig;
import config.DatabaseInitializer;
import dao.AppointmentDAO;
import dao.ClinicSettingsDAO;
import dao.PatientDAO;
import dao.SessionDAO;
import dao.WorkerDAO;
import model.Appointment;
import model.Patient;
import model.Session;
import model.Worker;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class AppointmentVerificationTest {

    public static void main(String[] args) {
        System.out.println("================================================================");
        System.out.println("      RUNNING APPOINTMENT SCHEDULING VERIFICATION SUITE         ");
        System.out.println("================================================================");

        try {
            // Isolate verification database in a disposable temp directory
            Path tempHome = Files.createTempDirectory("clinic_rdv_verify_");
            System.setProperty("user.home", tempHome.toAbsolutePath().toString());
            System.out.println("Using disposable user.home: " + tempHome.toAbsolutePath());

            // 1. Initialize Database & Verify Default Clinic Settings
            System.out.println("\n[Test 1] Initializing Database & Verifying Default Clinic Settings...");
            AppConfig.setNodeRole(AppConfig.ROLE_HOST);
            AppConfig.setDbPort(19094);
            DatabaseInitializer.initializeDatabase();

            ClinicSettingsDAO settingsDAO = new ClinicSettingsDAO();
            Set<DayOfWeek> defaultDays = settingsDAO.getWorkingDays();
            if (defaultDays.contains(DayOfWeek.FRIDAY) || !defaultDays.contains(DayOfWeek.SATURDAY)) {
                throw new AssertionError("Expected Saturday working and Friday off by default, got: " + defaultDays);
            }
            if (!LocalTime.of(8, 0).equals(settingsDAO.getStartTime())
                    || !LocalTime.of(17, 0).equals(settingsDAO.getEndTime())
                    || settingsDAO.getDefaultDurationMin() != 30) {
                throw new AssertionError("Unexpected default clinic hours or duration!");
            }

            // Update settings and verify persistence
            Set<DayOfWeek> updatedDays = EnumSet.of(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY);
            settingsDAO.setWorkingDays(updatedDays);
            settingsDAO.setStartTime(LocalTime.of(8, 30));
            settingsDAO.setEndTime(LocalTime.of(16, 30));
            settingsDAO.setDefaultDurationMin(45);

            if (!settingsDAO.getWorkingDays().equals(updatedDays)
                    || !LocalTime.of(8, 30).equals(settingsDAO.getStartTime())
                    || settingsDAO.getDefaultDurationMin() != 45) {
                throw new AssertionError("Clinic settings update did not persist accurately!");
            }
            System.out.println("  ✓ Clinic working days, hours, and default duration persisted in H2.");

            PatientDAO patientDAO = new PatientDAO();
            WorkerDAO workerDAO = new WorkerDAO();
            AppointmentDAO appointmentDAO = new AppointmentDAO();
            SessionDAO sessionDAO = new SessionDAO();

            String today = LocalDate.now().toString();
            String tomorrow = LocalDate.now().plusDays(1).toString();

            // 2. Registered Patient Appointment & Financial Balance Isolation
            System.out.println("\n[Test 2] Booking Registered Patient Appointment & Verifying Zero Financial Impact...");
            Patient patient = new Patient();
            patient.setName("Amine Benali");
            patient.setGender("Male");
            patient.setPhone("0551234567");
            if (!patientDAO.addPatient(patient)) {
                throw new AssertionError("Failed to create test patient!");
            }

            Worker worker = new Worker();
            worker.setName("Dr. Merabet");
            worker.setBirthDate("1985-04-12");
            worker.setBirthPlace("Alger");
            worker.setPhoneNumber("0661112233");
            worker.setIdentityCardNumber("100200300");
            worker.setFunction("Kinésithérapeute");
            worker.setFamillySituation("Marié");
            workerDAO.addWorker(worker);
            int workerId = workerDAO.getAllWorkers().get(0).getId();

            Appointment rdv1 = new Appointment();
            rdv1.setPatientId(patient.getId());
            rdv1.setWorkerId(workerId);
            rdv1.setDate(today);
            rdv1.setStartTime("09:00");
            rdv1.setDurationMin(30);
            rdv1.setMotif("Rééducation épaule droite");
            rdv1.setStatus(Appointment.STATUS_PREVU);
            rdv1.setCreatedBy("employe_accueil");

            if (!appointmentDAO.addAppointment(rdv1) || rdv1.getId() <= 0) {
                throw new AssertionError("Failed to insert registered patient appointment!");
            }

            Patient pCheck = patientDAO.getPatientById(patient.getId());
            if (Math.abs(pCheck.getTotalCost()) > 0.001 || Math.abs(pCheck.getTotalPaid()) > 0.001 || Math.abs(pCheck.getBalance()) > 0.001) {
                throw new AssertionError("Appointment creation must NEVER alter patient financial balance!");
            }

            Appointment fetched = appointmentDAO.getAppointmentById(rdv1.getId());
            if (!"Male".equalsIgnoreCase(fetched.getPatientGender()) || fetched.isFemale()) {
                throw new AssertionError("Expected patient gender Male and isFemale=false, got: " + fetched.getPatientGender());
            }

            // Female patient badge test (Inscrite vs Inscrit)
            Patient femalePatient = new Patient();
            femalePatient.setName("Nadia Cherif");
            femalePatient.setGender("Female");
            femalePatient.setPhone("0661402918");
            patientDAO.addPatient(femalePatient);

            Appointment rdvFemale = new Appointment();
            rdvFemale.setPatientId(femalePatient.getId());
            rdvFemale.setDate(today);
            rdvFemale.setStartTime("10:00");
            rdvFemale.setDurationMin(45);
            rdvFemale.setMotif("Détartrage");
            rdvFemale.setStatus(Appointment.STATUS_TERMINE);
            appointmentDAO.addAppointment(rdvFemale);

            Appointment fetchedFemale = appointmentDAO.getAppointmentById(rdvFemale.getId());
            if (!fetchedFemale.isFemale()) {
                throw new AssertionError("Expected female patient isFemale=true!");
            }
            System.out.println("  ✓ Registered appointment created (ID=" + rdv1.getId() + ") with gender mapping and 0.00 DA balance impact.");

            // 3. Walk-in / Phone Booking & Linking to Newly Created Patient
            System.out.println("\n[Test 3] Booking Walk-In Caller & Linking to Newly Created Patient...");
            Appointment walkInRdv = new Appointment();
            walkInRdv.setPatientId(null);
            walkInRdv.setGuestName("Karim Invité");
            walkInRdv.setGuestPhone("0770998877");
            walkInRdv.setDate(today);
            walkInRdv.setStartTime("10:00");
            walkInRdv.setDurationMin(45);
            walkInRdv.setMotif("Consultation initiale");
            walkInRdv.setStatus(Appointment.STATUS_PREVU);
            walkInRdv.setCreatedBy("employe_accueil");

            if (!appointmentDAO.addAppointment(walkInRdv)) {
                throw new AssertionError("Failed to insert walk-in appointment!");
            }

            Appointment loadedWalkIn = appointmentDAO.getAppointmentById(walkInRdv.getId());
            if (!loadedWalkIn.isWalkIn() || !"Karim Invité".equals(loadedWalkIn.getDisplayName())) {
                throw new AssertionError("Walk-in display name or flag mismatch!");
            }

            // Simulate "Créer fiche patient" shortcut from walk-in
            Patient convertedWalkInPatient = new Patient();
            convertedWalkInPatient.setName(loadedWalkIn.getGuestName());
            convertedWalkInPatient.setPhone(loadedWalkIn.getGuestPhone());
            convertedWalkInPatient.setGender("Male");
            patientDAO.addPatient(convertedWalkInPatient);

            if (!appointmentDAO.linkPatientToAppointment(loadedWalkIn.getId(), convertedWalkInPatient.getId())) {
                throw new AssertionError("Failed to link new patient to walk-in appointment!");
            }

            Appointment afterLink = appointmentDAO.getAppointmentById(loadedWalkIn.getId());
            if (afterLink.isWalkIn() || afterLink.getPatientId() != convertedWalkInPatient.getId()) {
                throw new AssertionError("Walk-in appointment should now be linked to registered patient!");
            }
            System.out.println("  ✓ Walk-in caller booked and seamlessly upgraded to registered patient #" + convertedWalkInPatient.getId());

            // 4. Overlap Conflict Detection (Active vs Canceled/Absent)
            System.out.println("\n[Test 4] Verifying Non-Blocking Overlap Conflict Detection...");
            // rdv1 is at 09:00 - 09:30 for workerId
            List<Appointment> conflictsAt0915 = appointmentDAO.findOverlappingAppointments(today, "09:15", 30, workerId, -1);
            if (conflictsAt0915.size() != 1 || conflictsAt0915.get(0).getId() != rdv1.getId()) {
                throw new AssertionError("Expected overlap with rdv1 at 09:15, got: " + conflictsAt0915.size());
            }

            // Adjacent slot at 09:30 should NOT overlap 09:00-09:30
            List<Appointment> conflictsAt0930 = appointmentDAO.findOverlappingAppointments(today, "09:30", 30, workerId, -1);
            if (!conflictsAt0930.isEmpty()) {
                throw new AssertionError("09:30 should not overlap 09:00-09:30!");
            }

            // If rdv1 is canceled (ANNULE), it must no longer trigger an overlap warning
            appointmentDAO.updateStatus(rdv1.getId(), Appointment.STATUS_ANNULE);
            List<Appointment> conflictsAfterCancel = appointmentDAO.findOverlappingAppointments(today, "09:15", 30, workerId, -1);
            if (!conflictsAfterCancel.isEmpty()) {
                throw new AssertionError("Canceled appointment must not trigger overlap conflict!");
            }

            // Restore rdv1 to CONFIRME for conversion test
            appointmentDAO.updateStatus(rdv1.getId(), Appointment.STATUS_CONFIRME);
            System.out.println("  ✓ Overlap detection accurately catches intersecting active slots and ignores canceled/adjacent slots.");

            // 5. One-Click Conversion to Session & ON DELETE SET NULL Safety
            System.out.println("\n[Test 5] Testing One-Click Appointment -> Session Conversion & Foreign Key Reset...");
            Session convertedSession = new Session();
            convertedSession.setPatientId(patient.getId());
            convertedSession.setDate(rdv1.getDate());
            convertedSession.setTreatment(rdv1.getMotif());
            convertedSession.setCost(1500.0);
            convertedSession.setPaidAmount(1500.0);
            convertedSession.setPaied("FULL");

            if (!sessionDAO.addSession(convertedSession, 1500.0, "employe_accueil")) {
                throw new AssertionError("Failed to create session from appointment!");
            }
            if (!appointmentDAO.markConvertedToSession(rdv1.getId(), convertedSession.getId())) {
                throw new AssertionError("Failed to link sessionId on appointment!");
            }

            Appointment convertedRdv = appointmentDAO.getAppointmentById(rdv1.getId());
            if (!convertedRdv.isConvertedToSession()
                    || convertedRdv.getSessionId() != convertedSession.getId()
                    || !Appointment.STATUS_TERMINE.equals(convertedRdv.getStatus())) {
                throw new AssertionError("Appointment should have sessionId linked and status=TERMINE!");
            }

            Patient pAfterSession = patientDAO.getPatientById(patient.getId());
            if (Math.abs(pAfterSession.getTotalCost() - 1500.0) > 0.01 || Math.abs(pAfterSession.getTotalPaid() - 1500.0) > 0.01) {
                throw new AssertionError("Session conversion did not update patient totals as expected!");
            }

            // Delete the session and verify ON DELETE SET NULL clears appointment.sessionId
            sessionDAO.deleteSession(convertedSession.getId(), patient.getId());
            Appointment rdvAfterSessionDelete = appointmentDAO.getAppointmentById(rdv1.getId());
            if (rdvAfterSessionDelete.isConvertedToSession() || rdvAfterSessionDelete.getSessionId() != null) {
                throw new AssertionError("Expected appointment.sessionId to reset to NULL when linked session is deleted!");
            }
            System.out.println("  ✓ Appointment -> Session conversion and ON DELETE SET NULL verified.");

            // 6. Next Upcoming Appointment ("Prochain RDV") Lookup
            System.out.println("\n[Test 6] Verifying Next Upcoming Appointment ('Prochain RDV') Lookup...");
            Appointment futureRdv = new Appointment();
            futureRdv.setPatientId(patient.getId());
            futureRdv.setDate(tomorrow);
            futureRdv.setStartTime("11:15");
            futureRdv.setDurationMin(30);
            futureRdv.setMotif("Contrôle évolution");
            futureRdv.setStatus(Appointment.STATUS_PREVU);
            futureRdv.setCreatedBy("employe_accueil");
            appointmentDAO.addAppointment(futureRdv);

            Appointment nextUpcoming = appointmentDAO.getNextUpcomingAppointmentForPatient(patient.getId());
            if (nextUpcoming == null || nextUpcoming.getId() != futureRdv.getId()) {
                throw new AssertionError("Expected tomorrow's PREVU appointment as Prochain RDV!");
            }
            System.out.println("  ✓ Prochain RDV accurately resolved to " + nextUpcoming.getDate() + " at " + nextUpcoming.getStartTime());

            // 7. Multiple Simultaneous Appointments & Clinic Capacity Limit (e.g. 5 workers / 4 rooms)
            System.out.println("\n[Test 7] Verifying Multiple Simultaneous Appointments & Clinic Capacity Limit...");
            settingsDAO.setMaxConcurrentAppointments(5);
            if (settingsDAO.getMaxConcurrentAppointments() != 5) {
                throw new AssertionError("Failed to configure clinic max concurrent capacity to 5!");
            }

            // Create workers for simultaneous scheduling
            Worker w1 = new Worker();
            w1.setName("Dr. Yasmine");
            w1.setIdentityCardNumber("111111");
            w1.setFunction("Dentiste");
            workerDAO.addWorker(w1);

            Worker w2 = new Worker();
            w2.setName("Dr. Bilel");
            w2.setIdentityCardNumber("222222");
            w2.setFunction("Kinésithérapeute");
            workerDAO.addWorker(w2);

            // Add 4 simultaneous appointments at 14:00 on tomorrow (different doctors / unassigned)
            for (int i = 1; i <= 4; i++) {
                Appointment concurrentApt = new Appointment();
                concurrentApt.setGuestName("Patient Simultané " + i);
                concurrentApt.setGuestPhone("055500000" + i);
                concurrentApt.setDate(tomorrow);
                concurrentApt.setStartTime("14:00");
                concurrentApt.setDurationMin(30);
                concurrentApt.setMotif("Soin en salle " + i);
                concurrentApt.setStatus(Appointment.STATUS_PREVU);
                concurrentApt.setCreatedBy("employe");
                if (i == 1) concurrentApt.setWorkerId(w1.getId());
                if (i == 2) concurrentApt.setWorkerId(w2.getId());

                if (!appointmentDAO.addAppointment(concurrentApt) || concurrentApt.getId() <= 0) {
                    throw new AssertionError("Failed to save simultaneous appointment #" + i);
                }
            }

            // Check that adding a 5th appointment at 14:00 with another doctor is completely allowed (0 conflicts)
            List<Appointment> conflictsFor5th = appointmentDAO.findOverlappingAppointments(tomorrow, "14:00", 30, null, -1);
            if (!conflictsFor5th.isEmpty()) {
                throw new AssertionError("5th simultaneous appointment within capacity of 5 should have NO conflict, got: " + conflictsFor5th.size());
            }

            // Add the 5th appointment to reach full capacity
            Appointment fifthApt = new Appointment();
            fifthApt.setGuestName("Patient Simultané 5");
            fifthApt.setDate(tomorrow);
            fifthApt.setStartTime("14:00");
            fifthApt.setDurationMin(30);
            fifthApt.setMotif("Soin en salle 5");
            fifthApt.setStatus(Appointment.STATUS_PREVU);
            fifthApt.setCreatedBy("employe");
            appointmentDAO.addAppointment(fifthApt);

            // Now, with 5 appointments active at 14:00, attempting a 6th appointment MUST trigger capacity conflict
            List<Appointment> conflictsFor6th = appointmentDAO.findOverlappingAppointments(tomorrow, "14:10", 30, null, -1);
            if (conflictsFor6th.size() != 5) {
                throw new AssertionError("Expected 5 conflicting appointments for 6th concurrent slot, got: " + conflictsFor6th.size());
            }

            // Attempting to double-book Dr. Yasmine (w1) at 14:00 MUST trigger practitioner conflict
            List<Appointment> doctorDoubleBook = appointmentDAO.findOverlappingAppointments(tomorrow, "14:00", 30, w1.getId(), -1);
            if (doctorDoubleBook.isEmpty() || doctorDoubleBook.stream().noneMatch(c -> c.getWorkerId() != null && c.getWorkerId() == w1.getId())) {
                throw new AssertionError("Expected doctor double-booking conflict for Dr. Yasmine!");
            }

            System.out.println("  ✓ Multiple simultaneous appointments successfully saved (5 at the same time).");
            System.out.println("  ✓ Capacity overload (6th appointment) and same-doctor double booking properly caught.");

            System.out.println("\n================================================================");
            System.out.println("   ALL APPOINTMENT SCHEDULING VERIFICATIONS PASSED! ✓           ");
            System.out.println("================================================================");
            DatabaseConfig.shutdown();
            System.exit(0);

        } catch (Throwable t) {
            System.err.println("\n❌ APPOINTMENT VERIFICATION FAILED: " + t.getMessage());
            t.printStackTrace();
            DatabaseConfig.shutdown();
            System.exit(1);
        }
    }
}
