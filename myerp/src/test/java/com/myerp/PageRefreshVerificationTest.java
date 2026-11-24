package com.myerp;

import config.AppConfig;
import config.DatabaseConfig;
import config.DatabaseInitializer;
import controller.BillController;
import controller.PatientController;
import controller.SessionController;
import controller.SoldController;
import dao.BillDAO;
import dao.PatientDAO;
import dao.SessionDAO;
import dao.SoldDAO;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import model.Bill;
import model.Patient;
import model.Session;
import model.Sold;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class PageRefreshVerificationTest {

    public static void main(String[] args) {
        System.out.println("================================================================");
        System.out.println("      RUNNING PAGE REFRESH BUTTON VERIFICATION TEST             ");
        System.out.println("================================================================");

        try {
            Path tempHome = Files.createTempDirectory("clinic_page_refresh_test_");
            System.setProperty("user.home", tempHome.toAbsolutePath().toString());

            CountDownLatch fxLatch = new CountDownLatch(1);
            try {
                Platform.startup(fxLatch::countDown);
            } catch (IllegalStateException e) {
                fxLatch.countDown();
            }
            if (!fxLatch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Failed to initialize JavaFX toolkit");
            }

            AppConfig.setNodeRole(AppConfig.ROLE_HOST);
            AppConfig.setDbPort(19098);
            DatabaseInitializer.initializeDatabase();

            String today = LocalDate.now().toString();
            PatientDAO patientDAO = new PatientDAO();
            Patient p = new Patient();
            p.setName("Patient Test Refresh");
            p.setGender("Male");
            p.setPhone("0555000001");
            patientDAO.addPatient(p);

            SessionDAO sessionDAO = new SessionDAO();
            BillDAO billDAO = new BillDAO();
            SoldDAO soldDAO = new SoldDAO();

            // 1. Verify Session page refresh button
            SessionController sessionController = loadFxml("/fxml/pages/session.fxml", SessionController.class);
            TableView<?> sessionTable = getField(sessionController, "sessionTable", TableView.class);
            Button sessionRefreshBtn = getField(sessionController, "btnRefresh", Button.class);
            Label dayTotalCostLabel = getField(sessionController, "dayTotalCostLabel", Label.class);
            Label dayTotalPaidLabel = getField(sessionController, "dayTotalPaidLabel", Label.class);
            assertNotNull(sessionRefreshBtn, "Session btnRefresh should be injected");
            int initialSessions = sessionTable.getItems().size();

            Session remoteSession = new Session(0, p.getId(), today, "Détartrage", 3500.0, 2000.0);
            remoteSession.setPaied("PARTIEL");
            if (!sessionDAO.addSession(remoteSession, 2000.0, "remote_pc")) {
                throw new AssertionError("Failed to insert remote session");
            }
            if (sessionTable.getItems().size() != initialSessions) {
                throw new AssertionError("Session table changed unexpectedly before refresh");
            }

            runOnFxAndWait(sessionRefreshBtn::fire);
            if (sessionTable.getItems().size() != initialSessions + 1) {
                throw new AssertionError("Expected session table size " + (initialSessions + 1) + ", got " + sessionTable.getItems().size());
            }
            if (!dayTotalCostLabel.getText().contains("3500") || !dayTotalPaidLabel.getText().contains("2000")) {
                throw new AssertionError("Session summary cards did not update: cost=" + dayTotalCostLabel.getText() + ", paid=" + dayTotalPaidLabel.getText());
            }
            System.out.println("  ✓ Session page refresh button updated table and summary cards.");

            // 2. Verify Bill page refresh button
            BillController billController = loadFxml("/fxml/pages/bill.fxml", BillController.class);
            TableView<?> billTable = getField(billController, "billTable", TableView.class);
            Button billRefreshBtn = getField(billController, "btnRefresh", Button.class);
            assertNotNull(billRefreshBtn, "Bill btnRefresh should be injected");
            int initialBills = billTable.getItems().size();

            Bill remoteBill = new Bill(0, today, 12500.0);
            if (billDAO.addBill(remoteBill) <= 0) {
                throw new AssertionError("Failed to insert remote bill");
            }

            runOnFxAndWait(billRefreshBtn::fire);
            if (billTable.getItems().size() != initialBills + 1) {
                throw new AssertionError("Expected bill table size " + (initialBills + 1) + ", got " + billTable.getItems().size());
            }
            System.out.println("  ✓ Bill page refresh button updated table.");

            // 3. Verify Sold page refresh button
            SoldController soldController = loadFxml("/fxml/pages/sold.fxml", SoldController.class);
            TableView<?> soldTable = getField(soldController, "soldTable", TableView.class);
            Button soldRefreshBtn = getField(soldController, "btnRefresh", Button.class);
            Label soldCountLabel = getField(soldController, "soldCountLabel", Label.class);
            Label totalRevenueLabel = getField(soldController, "totalRevenueLabel", Label.class);
            assertNotNull(soldRefreshBtn, "Sold btnRefresh should be injected");
            int initialSolds = soldTable.getItems().size();

            Sold remoteSold = new Sold(0, "Brosse Orthodontique", today, 1500.0, 2.0);
            if (!soldDAO.addSold(remoteSold)) {
                throw new AssertionError("Failed to insert remote sold");
            }

            runOnFxAndWait(soldRefreshBtn::fire);
            if (soldTable.getItems().size() != initialSolds + 1) {
                throw new AssertionError("Expected sold table size " + (initialSolds + 1) + ", got " + soldTable.getItems().size());
            }
            if (!"1".equals(soldCountLabel.getText().trim()) || !totalRevenueLabel.getText().contains("3")) {
                throw new AssertionError("Sold summary cards did not update: count=" + soldCountLabel.getText() + ", revenue=" + totalRevenueLabel.getText());
            }
            System.out.println("  ✓ Sold page refresh button updated table and summary cards.");

            // 4. Verify Patient page refresh button
            PatientController patientController = loadFxml("/fxml/pages/patient.fxml", PatientController.class);
            TableView<?> patientTable = getField(patientController, "patientTable", TableView.class);
            Button patientRefreshBtn = getField(patientController, "btnRefresh", Button.class);
            Label patientCountLabel = getField(patientController, "patientCountLabel", Label.class);
            assertNotNull(patientRefreshBtn, "Patient btnRefresh should be injected");
            int initialPatients = patientTable.getItems().size();

            Patient remotePatient = new Patient();
            remotePatient.setName("Patient Remote Sync");
            remotePatient.setGender("Female");
            remotePatient.setPhone("0555000002");
            patientDAO.addPatient(remotePatient);
            if (remotePatient.getId() <= 0) {
                throw new AssertionError("Failed to insert remote patient");
            }

            runOnFxAndWait(patientRefreshBtn::fire);
            if (patientTable.getItems().size() != initialPatients + 1) {
                throw new AssertionError("Expected patient table size " + (initialPatients + 1) + ", got " + patientTable.getItems().size());
            }
            if (!patientCountLabel.getText().contains(String.valueOf(initialPatients + 1))) {
                throw new AssertionError("Patient count label did not update: " + patientCountLabel.getText());
            }
            System.out.println("  ✓ Patient page refresh button updated table and count label.");

            System.out.println("\n================================================================");
            System.out.println("   PAGE REFRESH BUTTON VERIFICATION PASSED! ✓                   ");
            System.out.println("================================================================");

            DatabaseConfig.shutdown();
            Platform.exit();
            System.exit(0);
        } catch (Throwable t) {
            t.printStackTrace();
            DatabaseConfig.shutdown();
            System.exit(1);
        }
    }

    private static <T> T loadFxml(String path, Class<T> controllerClass) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> err = new AtomicReference<>();
        AtomicReference<T> ref = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(PageRefreshVerificationTest.class.getResource(path));
                Parent root = loader.load();
                assertNotNull(root, "Root should not be null for " + path);
                ref.set(controllerClass.cast(loader.getController()));
            } catch (Throwable t) {
                err.set(t);
            } finally {
                latch.countDown();
            }
        });

        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("Timeout loading " + path);
        }
        if (err.get() != null) {
            throw new AssertionError("Failed loading " + path, err.get());
        }
        return ref.get();
    }

    private static void runOnFxAndWait(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> err = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                err.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("Timeout running FX action");
        }
        if (err.get() != null) {
            throw new AssertionError("FX action threw error", err.get());
        }
    }

    private static <T> T getField(Object target, String fieldName, Class<T> type) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        return type.cast(f.get(target));
    }

    private static void assertNotNull(Object obj, String message) {
        if (obj == null) {
            throw new AssertionError(message);
        }
    }
}
