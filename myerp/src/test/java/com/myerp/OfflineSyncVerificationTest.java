package com.myerp;

import config.AppConfig;
import config.DatabaseConfig;
import config.DatabaseInitializer;
import config.OfflineSyncManager;
import dao.AppointmentDAO;
import dao.PatientDAO;
import dao.PaymentDAO;
import dao.SessionDAO;
import dao.TherapyPlanDAO;
import dao.UserDAO;
import model.Appointment;
import model.Patient;
import model.Payment;
import model.Session;
import model.TherapyPlan;
import model.User;
import network.ConnectionMonitor;
import util.PasswordHasher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * End-to-end verification program for Offline Client Fallback, Salted PBKDF2 Password Hashing,
 * 3-Strike Connection Monitor Failover, Trigger-Based Offline Journaling, Foreign-Key ID Remapping,
 * and Baseline-Compared Conflict Resolution ("Host wins, flag the conflict").
 */
public class OfflineSyncVerificationTest {

    public static void main(String[] args) throws Exception {
        Path tempHome = Files.createTempDirectory("clinic_offline_sync_test_");
        System.setProperty("user.home", tempHome.toAbsolutePath().toString());

        System.out.println("=======================================================================");
        System.out.println("  RUNNING OFFLINE CLIENT FALLBACK & AUTO-SYNC VERIFICATION SUITE       ");
        System.out.println("  Disposable Test Home: " + tempHome.toAbsolutePath());
        System.out.println("=======================================================================");

        try {
            // -----------------------------------------------------------------
            // Test 1: Salted PBKDF2 Password Hashing & Legacy Plaintext Migration
            // -----------------------------------------------------------------
            System.out.println("\n[Test 1] Verifying Salted PBKDF2 Password Hashing & Plaintext Migration...");
            AppConfig.setNodeRole(AppConfig.ROLE_HOST);
            AppConfig.setDbHost("localhost");
            AppConfig.setDbPort(19096);
            DatabaseInitializer.initializeDatabase();

            // Verify default admin password is stored as salted PBKDF2 hash, not plaintext
            try (Connection hostConn = DatabaseConfig.getHostConnection();
                 Statement stmt = hostConn.createStatement()) {
                try (ResultSet rs = stmt.executeQuery("SELECT passWord FROM Users WHERE userName = 'admin'")) {
                    if (!rs.next()) {
                        throw new AssertionError("Default admin user not found!");
                    }
                    String storedHash = rs.getString(1);
                    if (!PasswordHasher.isHashed(storedHash) || "admin123".equals(storedHash)) {
                        throw new AssertionError("Expected salted PBKDF2 hash for admin, but found: " + storedHash);
                    }
                    System.out.println("  ✓ Default admin password stored as salted PBKDF2 hash: " + storedHash.substring(0, 28) + "...");
                }

                // Insert a simulated legacy plaintext user and run DatabaseInitializer to verify migration
                stmt.execute("INSERT INTO Users (fullName, userName, passWord, userType) VALUES ('Secrétaire Legacy', 'sec_legacy', 'secretPlain99', 'Employé')");
            }

            DatabaseInitializer.initializeDatabase();
            try (Connection hostConn = DatabaseConfig.getHostConnection();
                 Statement stmt = hostConn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT passWord FROM Users WHERE userName = 'sec_legacy'")) {
                if (!rs.next() || !PasswordHasher.isHashed(rs.getString(1))) {
                    throw new AssertionError("Legacy plaintext password was not migrated to PBKDF2 hash!");
                }
                System.out.println("  ✓ Legacy plaintext password migrated in-place to salted PBKDF2 hash.");
            }

            User loggedInSec = UserDAO.login("sec_legacy", "secretPlain99");
            if (loggedInSec == null || !"sec_legacy".equals(loggedInSec.getUserName())) {
                throw new AssertionError("Failed to authenticate migrated user 'sec_legacy'!");
            }
            if (UserDAO.login("sec_legacy", "wrongPassword") != null) {
                throw new AssertionError("Authentication should fail for wrong password!");
            }
            System.out.println("  ✓ UserDAO.login verified against salted PBKDF2 hash.");

            // Seed baseline clinic data on Host
            PatientDAO patientDAO = new PatientDAO();
            SessionDAO sessionDAO = new SessionDAO();
            PaymentDAO paymentDAO = new PaymentDAO();
            TherapyPlanDAO therapyPlanDAO = new TherapyPlanDAO();
            AppointmentDAO appointmentDAO = new AppointmentDAO();

            Patient hostPatient1 = new Patient(0, "Karim Mansouri", "0550112233");
            hostPatient1.setGender("Homme");
            patientDAO.addPatient(hostPatient1);

            Patient hostPatient2 = new Patient(0, "Nadia Belkacem", "0661445566");
            hostPatient2.setGender("Femme");
            patientDAO.addPatient(hostPatient2);

            Session initialSession = new Session(0, hostPatient1.getId(), "2026-11-08", "Rééducation épaule", 2500.0, 1000.0);
            initialSession.setPaied("PARTIEL");
            sessionDAO.addSession(initialSession, 1000.0, "admin");

            // -----------------------------------------------------------------
            // Test 2: Client Initial Replica Population & 3-Strike ConnectionMonitor
            // -----------------------------------------------------------------
            System.out.println("\n[Test 2] Verifying Initial Replica Sync & 3-Strike ConnectionMonitor...");
            AppConfig.setNodeRole(AppConfig.ROLE_CLIENT);
            DatabaseConfig.setOfflineMode(false);
            DatabaseConfig.resetPool();

            if (OfflineSyncManager.hasInitializedReplica()) {
                throw new AssertionError("Replica should not be marked initialized before first sync!");
            }

            OfflineSyncManager.SyncResult initSync = OfflineSyncManager.synchronizeWithHost();
            if (!initSync.success() || !OfflineSyncManager.hasInitializedReplica()) {
                throw new AssertionError("Initial replica synchronization failed: " + initSync.errorMessage());
            }
            System.out.println("  ✓ Initial client replica synchronized (Last sync: " + AppConfig.getReplicaLastSync() + ").");

            // Verify 3-strike threshold: 1 or 2 failed probes do NOT switch to offline mode
            ConnectionMonitor.resetStateForTest();
            AtomicBoolean bannerOfflineNotified = new AtomicBoolean(false);
            ConnectionMonitor.processProbeResult(false, connected -> bannerOfflineNotified.set(!connected), null);
            if (DatabaseConfig.isOfflineMode() || bannerOfflineNotified.get()) {
                throw new AssertionError("1st failed probe should NOT switch to offline mode!");
            }
            ConnectionMonitor.processProbeResult(false, connected -> bannerOfflineNotified.set(!connected), null);
            if (DatabaseConfig.isOfflineMode() || bannerOfflineNotified.get()) {
                throw new AssertionError("2nd failed probe should NOT switch to offline mode!");
            }
            // A successful probe resets the failure counter
            ConnectionMonitor.processProbeResult(true, connected -> bannerOfflineNotified.set(!connected), null);
            if (ConnectionMonitor.getConsecutiveFailures() != 0) {
                throw new AssertionError("Successful probe should reset consecutive failure counter to 0!");
            }
            System.out.println("  ✓ Brief Wi-Fi blips (1–2 failed probes) ignored without flapping.");

            // Now trigger 3 consecutive failed probes -> must switch to offline mode automatically
            ConnectionMonitor.processProbeResult(false, connected -> bannerOfflineNotified.set(!connected), null);
            ConnectionMonitor.processProbeResult(false, connected -> bannerOfflineNotified.set(!connected), null);
            ConnectionMonitor.processProbeResult(false, connected -> bannerOfflineNotified.set(!connected), null);
            if (!DatabaseConfig.isOfflineMode() || !bannerOfflineNotified.get()) {
                throw new AssertionError("3rd consecutive failed probe MUST switch client to offline replica mode!");
            }
            System.out.println("  ✓ 3 consecutive failed probes switched client to Offline Mode and notified banner.");

            // -----------------------------------------------------------------
            // Test 3: Offline Login & Offline Work vs Concurrent Host Work (ID Collision & Remapping)
            // -----------------------------------------------------------------
            System.out.println("\n[Test 3] Verifying Offline Work, Replica Overwrite Protection & Concurrent Host Edits...");

            // Verify login works while in Offline Mode
            User offlineUser = UserDAO.login("sec_legacy", "secretPlain99");
            if (offlineUser == null) {
                throw new AssertionError("Offline login against local replica failed!");
            }
            System.out.println("  ✓ Offline login succeeded against salted hash in local replica.");

            // 3a. On Client (Offline): Create a brand new patient (will get localId = 3)
            Patient offlineNewPatient = new Patient(0, "Yacine Offline", "0770998877");
            offlineNewPatient.setGender("Homme");
            if (!patientDAO.addPatient(offlineNewPatient)) {
                throw new AssertionError("Failed to add patient in offline mode!");
            }
            int localPatientId = offlineNewPatient.getId();
            System.out.println("  ✓ Created new patient offline: 'Yacine Offline' with localId=" + localPatientId);

            // Add a TherapyPlan, a standalone Session + Payment, and an Appointment for the new offline patient
            TherapyPlan offlinePlan = new TherapyPlan(0, localPatientId, "2026-11-08", 8000.0);
            if (!therapyPlanDAO.addTherapyPlan(offlinePlan)) {
                throw new AssertionError("Failed to add therapy plan in offline mode!");
            }

            Session offlineSession = new Session(0, localPatientId, "2026-11-08", "Séance bilan moteur", 2000.0, 1500.0);
            offlineSession.setPaied("PARTIEL");
            if (!sessionDAO.addSession(offlineSession, 1500.0, "sec_legacy")) {
                throw new AssertionError("Failed to add session in offline mode!");
            }

            Appointment offlineAppt = new Appointment();
            offlineAppt.setPatientId(localPatientId);
            offlineAppt.setDate("2026-11-10");
            offlineAppt.setStartTime("10:00");
            offlineAppt.setDurationMin(30);
            offlineAppt.setMotif("Suivi rééducation");
            offlineAppt.setStatus(Appointment.STATUS_PREVU);
            offlineAppt.setCreatedBy("sec_legacy");
            if (!appointmentDAO.addAppointment(offlineAppt)) {
                throw new AssertionError("Failed to add appointment in offline mode!");
            }

            // 3b. On Client (Offline): Record a payment for existing patient #1 (Karim Mansouri)
            Payment offlinePaymentForExisting = new Payment();
            offlinePaymentForExisting.setPatientId(hostPatient1.getId());
            offlinePaymentForExisting.setPaymentDate("2026-11-08");
            offlinePaymentForExisting.setAmount(500.0);
            offlinePaymentForExisting.setPaymentType(Payment.TYPE_DEPOSIT);
            offlinePaymentForExisting.setReason("Versement reçu hors-ligne");
            offlinePaymentForExisting.setCreatedBy("sec_legacy");
            if (!paymentDAO.recordPayment(offlinePaymentForExisting)) {
                throw new AssertionError("Failed to record payment for existing patient in offline mode!");
            }

            // 3c. On Client (Offline): Edit existing patient #1 (non-conflicting) AND existing patient #2 (will conflict!)
            hostPatient1.setPhone("0550999999"); // Non-conflicting offline edit
            patientDAO.updatePatient(hostPatient1);

            hostPatient2.setPhone("0661000000"); // Client edits Nadia's phone offline
            patientDAO.updatePatient(hostPatient2);

            // Verify guardrail: refreshReplicaFromHost MUST refuse to overwrite replica while offline_change_log has pending items
            try (Connection hostConn = DatabaseConfig.getHostConnection();
                 Connection replicaConn = DatabaseConfig.getReplicaConnection()) {
                boolean refused = false;
                try {
                    OfflineSyncManager.refreshReplicaFromHost(hostConn, replicaConn);
                } catch (IllegalStateException expected) {
                    refused = true;
                    System.out.println("  ✓ Precondition verified: refreshReplicaFromHost refused to overwrite pending offline work (" + expected.getMessage() + ")");
                }
                if (!refused) {
                    throw new AssertionError("refreshReplicaFromHost should have thrown IllegalStateException when pending offline changes exist!");
                }
            }

            // 3d. Simulate concurrent activity on the HOST while Client is still offline:
            // - Host inserts a new patient ("Sara Host") which takes id = 3 on the Host (colliding with Yacine's localId = 3!)
            // - Host adds a new session (cost = 3000, paid = 0) for existing patient #1 (Karim Mansouri)
            // - Host modifies existing patient #2 (Nadia Belkacem)'s phone to "0661777777" (creating a concurrent edit conflict!)
            try (Connection hostConn = DatabaseConfig.getHostConnection();
                 Statement hostStmt = hostConn.createStatement()) {
                hostStmt.execute("INSERT INTO patient (name, gender, phone, totalCost, totalPaid) VALUES ('Sara Host', 'Femme', '0555123456', 0, 0)");
                hostStmt.execute("INSERT INTO session (patientId, sessionDate, treatment, paied, cost, paidAmount) VALUES ("
                        + hostPatient1.getId() + ", '2026-11-08', 'Séance ultrason (Host)', 'IMPAYÉ', 3000.0, 0.0)");
                new PatientDAO().updatePatientTotals(hostConn, hostPatient1.getId());
                hostStmt.execute("UPDATE patient SET phone = '0661777777' WHERE id = " + hostPatient2.getId());
            }
            System.out.println("  ✓ Simulated concurrent Host inserts (ID collision on patient id=3) and conflicting edit on Patient #2.");

            // -----------------------------------------------------------------
            // Test 4: Automatic Reconnection (2-Strike Recovery) & Flush-First Sync
            // -----------------------------------------------------------------
            System.out.println("\n[Test 4] Verifying Automatic Reconnection, FK ID Remapping & Conflict Resolution...");
            AtomicReference<OfflineSyncManager.SyncResult> capturedSyncResult = new AtomicReference<>();

            // 1st successful probe while offline -> should wait for 2nd confirmation probe
            ConnectionMonitor.processProbeResult(true, connected -> bannerOfflineNotified.set(!connected), capturedSyncResult::set);
            if (!DatabaseConfig.isOfflineMode() || capturedSyncResult.get() != null) {
                throw new AssertionError("Should wait for 2 consecutive successful probes before syncing and exiting offline mode!");
            }

            // 2nd successful probe -> triggers flushOfflineQueueToHost + refreshReplicaFromHost + switches back to Online mode
            ConnectionMonitor.processProbeResult(true, connected -> bannerOfflineNotified.set(!connected), capturedSyncResult::set);
            if (DatabaseConfig.isOfflineMode() || bannerOfflineNotified.get()) {
                throw new AssertionError("Client should have switched back to Online Mode after 2nd successful probe!");
            }

            OfflineSyncManager.SyncResult syncResult = capturedSyncResult.get();
            if (syncResult == null || !syncResult.success()) {
                throw new AssertionError("Expected successful SyncResult on reconnection!");
            }
            System.out.println("  ✓ Reconnection sync succeeded: " + syncResult.flushedCount() + " offline mutation(s) flushed to Host.");

            // Verify exactly 1 conflict was flagged (Patient #2 Nadia Belkacem) and Host won
            if (syncResult.conflicts().size() != 1) {
                throw new AssertionError("Expected exactly 1 conflict on Patient #2, but got: " + syncResult.conflicts());
            }
            System.out.println("  ✓ Conflict detected and resolved (Host wins): " + syncResult.conflicts().get(0).description());

            // Verify canonical state on Host (now active via DatabaseConfig.getConnection())
            List<Patient> allPatients = patientDAO.getAllPatients();
            if (allPatients.size() != 4) {
                throw new AssertionError("Expected 4 total patients after sync (2 initial + 1 Host + 1 Offline), found: " + allPatients.size());
            }

            Patient karimAfter = patientDAO.getPatientById(hostPatient1.getId());
            // Karim's non-conflicting offline phone edit ("0550999999") should be applied!
            if (!"0550999999".equals(karimAfter.getPhone())) {
                throw new AssertionError("Expected Karim's non-conflicting offline phone edit '0550999999', but got: " + karimAfter.getPhone());
            }
            // Karim's totals: initial session (2500 cost, 1000 paid) + Host session (3000 cost, 0 paid) + Client offline payment (500 paid)
            // Expected totalCost = 5500.0, totalPaid = 1500.0
            if (Math.abs(karimAfter.getTotalCost() - 5500.0) > 0.01 || Math.abs(karimAfter.getTotalPaid() - 1500.0) > 0.01) {
                throw new AssertionError("Expected Karim totals (cost=5500.0, paid=1500.0), but got cost="
                        + karimAfter.getTotalCost() + ", paid=" + karimAfter.getTotalPaid());
            }
            System.out.println("  ✓ Existing Patient #1 merged non-conflicting phone edit + concurrent Host session & Client payment (Cost=5500.0, Paid=1500.0).");

            Patient nadiaAfter = patientDAO.getPatientById(hostPatient2.getId());
            if (!"0661777777".equals(nadiaAfter.getPhone())) {
                throw new AssertionError("Expected Host version '0661777777' to win on Nadia (#2), but got: " + nadiaAfter.getPhone());
            }
            System.out.println("  ✓ Existing Patient #2 preserved Host version ('0661777777') on concurrent edit conflict.");

            // Find Yacine Offline (who was localId=3 offline, but remapped to hostId=4 because Sara Host took id=3!)
            Patient yacineHost = allPatients.stream()
                    .filter(p -> "Yacine Offline".equals(p.getName()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Yacine Offline not found on Host after sync!"));

            if (yacineHost.getId() == localPatientId) {
                throw new AssertionError("Expected Yacine Offline's ID to be remapped from " + localPatientId + " to 4!");
            }
            // Yacine's totals: therapyPlan (8000 cost) + standalone session (2000 cost, 1500 paid) -> cost=10000.0, paid=1500.0
            if (Math.abs(yacineHost.getTotalCost() - 10000.0) > 0.01 || Math.abs(yacineHost.getTotalPaid() - 1500.0) > 0.01) {
                throw new AssertionError("Expected Yacine Offline totals (cost=10000.0, paid=1500.0), but got cost="
                        + yacineHost.getTotalCost() + ", paid=" + yacineHost.getTotalPaid());
            }
            if (therapyPlanDAO.getTherapyPlansByPatientId(yacineHost.getId()).size() != 1) {
                throw new AssertionError("Expected Yacine's therapy plan FK to be remapped to new Host patientId=" + yacineHost.getId());
            }
            if (sessionDAO.getSessionsByPatientId(yacineHost.getId()).size() != 1) {
                throw new AssertionError("Expected Yacine's session FK to be remapped to new Host patientId=" + yacineHost.getId());
            }
            if (paymentDAO.getPaymentsByPatientId(yacineHost.getId()).size() != 1) {
                throw new AssertionError("Expected Yacine's payment FK to be remapped to new Host patientId=" + yacineHost.getId());
            }
            if (appointmentDAO.getAppointmentsByPatientId(yacineHost.getId()).size() != 1) {
                throw new AssertionError("Expected Yacine's appointment FK to be remapped to new Host patientId=" + yacineHost.getId());
            }
            System.out.println("  ✓ Offline-created patient 'Yacine Offline' remapped from localId=" + localPatientId
                    + " -> hostId=" + yacineHost.getId() + " with all child therapyPlan, session, payment & appointment FKs intact!");

            // Verify offline_change_log is now empty and replica matches Host
            if (OfflineSyncManager.hasPendingOfflineChanges()) {
                throw new AssertionError("offline_change_log should be empty after successful sync!");
            }
            System.out.println("  ✓ Local replica refreshed and offline_change_log cleared.");

            System.out.println("\n=======================================================================");
            System.out.println("  ALL OFFLINE CLIENT FALLBACK & SYNC VERIFICATION TESTS PASSED! ✓      ");
            System.out.println("=======================================================================");
        } finally {
            ConnectionMonitor.stop();
            network.LanDiscoveryHost.stop();
            DatabaseConfig.stopServerAndResetPool();
        }
    }
}
