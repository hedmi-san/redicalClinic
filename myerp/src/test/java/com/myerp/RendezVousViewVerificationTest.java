package com.myerp;

import config.AppConfig;
import config.DatabaseInitializer;
import controller.RendezVousController;
import dao.AppointmentDAO;
import dao.PatientDAO;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import model.Appointment;
import model.Patient;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class RendezVousViewVerificationTest {

    public static void main(String[] args) {
        System.out.println("================================================================");
        System.out.println("     RUNNING RENDEZ-VOUS VIEW & CONTROLLER VERIFICATION         ");
        System.out.println("================================================================");

        try {
            // Isolate verification database in a disposable temp directory
            Path tempHome = Files.createTempDirectory("clinic_rdv_view_test_");
            System.setProperty("user.home", tempHome.toAbsolutePath().toString());

            // 1. Initialize JavaFX Toolkit
            CountDownLatch fxLatch = new CountDownLatch(1);
            try {
                Platform.startup(fxLatch::countDown);
            } catch (IllegalStateException e) {
                fxLatch.countDown();
            }
            if (!fxLatch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Failed to initialize JavaFX toolkit");
            }

            // 2. Initialize Database
            AppConfig.setNodeRole(AppConfig.ROLE_HOST);
            AppConfig.setDbPort(19095);
            DatabaseInitializer.initializeDatabase();

            // 3. Seed test patient & appointment
            PatientDAO patientDAO = new PatientDAO();
            Patient p = new Patient();
            p.setName("بوكعبوب الصديق");
            p.setGender("Male");
            p.setPhone("0766154367");
            patientDAO.addPatient(p);

            AppointmentDAO appointmentDAO = new AppointmentDAO();
            Appointment a = new Appointment();
            a.setPatientId(p.getId());
            a.setDate(LocalDate.now().toString());
            a.setStartTime("08:00");
            a.setDurationMin(45);
            a.setMotif("حصة علاج و اقتلاع شعر");
            a.setStatus(Appointment.STATUS_PREVU);
            a.setCreatedBy("admin");
            appointmentDAO.addAppointment(a);

            // 4. Load FXML on JavaFX thread
            CountDownLatch loadLatch = new CountDownLatch(1);
            AtomicReference<Throwable> loadError = new AtomicReference<>();
            AtomicReference<RendezVousController> controllerRef = new AtomicReference<>();
            AtomicReference<Parent> rootRef = new AtomicReference<>();

            Platform.runLater(() -> {
                try {
                    FXMLLoader loader = new FXMLLoader(RendezVousViewVerificationTest.class.getResource("/fxml/pages/rendez_vous.fxml"));
                    Parent root = loader.load();
                    rootRef.set(root);
                    controllerRef.set(loader.getController());
                } catch (Throwable t) {
                    loadError.set(t);
                } finally {
                    loadLatch.countDown();
                }
            });

            if (!loadLatch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timeout loading rendez_vous.fxml");
            }
            if (loadError.get() != null) {
                throw new AssertionError("Failed to load rendez_vous.fxml", loadError.get());
            }

            RendezVousController controller = controllerRef.get();
            if (controller == null) {
                throw new AssertionError("Controller was null after loading rendez_vous.fxml");
            }

            // Verify TableView and periodTitleLabel via reflection
            Field tableField = RendezVousController.class.getDeclaredField("dailyTable");
            tableField.setAccessible(true);
            TableView<?> table = (TableView<?>) tableField.get(controller);
            if (table == null) {
                throw new AssertionError("dailyTable was not injected!");
            }
            if (table.getColumns().size() != 5) {
                throw new AssertionError("Expected 5 columns (Heure, Patient, Motif, Statut, Actions), got " + table.getColumns().size());
            }

            Field titleField = RendezVousController.class.getDeclaredField("periodTitleLabel");
            titleField.setAccessible(true);
            Label titleLbl = (Label) titleField.get(controller);
            if (titleLbl == null || titleLbl.getText() == null || titleLbl.getText().isBlank()) {
                throw new AssertionError("periodTitleLabel was not populated!");
            }
            System.out.println("  ✓ periodTitleLabel populated: " + titleLbl.getText());

            Field subtitleField = RendezVousController.class.getDeclaredField("clinicHoursSubtitle");
            subtitleField.setAccessible(true);
            Label subtitleLbl = (Label) subtitleField.get(controller);
            if (subtitleLbl == null || !subtitleLbl.getText().contains("Ouvert")) {
                throw new AssertionError("clinicHoursSubtitle unexpected: " + (subtitleLbl != null ? subtitleLbl.getText() : "null"));
            }
            System.out.println("  ✓ clinicHoursSubtitle populated: " + subtitleLbl.getText());

            // Check that daily appointments list has our seeded appointment
            if (table.getItems().isEmpty()) {
                throw new AssertionError("Expected seeded appointment in table items, but table was empty!");
            }
            System.out.println("  ✓ dailyTable loaded " + table.getItems().size() + " appointment(s) successfully.");

            System.out.println("\n================================================================");
            System.out.println("   RENDEZ-VOUS FXML & CONTROLLER VERIFICATION PASSED! ✓         ");
            System.out.println("================================================================");

            Platform.exit();
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
