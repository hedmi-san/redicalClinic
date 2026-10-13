## 1. Database Schema & Idempotent Migration

- [x] 1.1 Add `payment` table DDL with constraints (`uq_reversal`, `CHECK(amount <> 0)`) and indexes in `config/DatabaseInitializer.java`.
- [x] 1.2 Implement idempotent backfill migration in `config/DatabaseInitializer.java` converting legacy `session.paidAmount` and `therapyPlan.cost` records into `payment` rows tagged with `createdBy = 'MIGRATION'`.
- [x] 1.3 Update `PatientDAO.updatePatientTotals` to synchronize `patient.totalCost` and `patient.totalPaid` directly from `session`, `therapyPlan`, and the `payment` ledger.

## 2. Domain Model & Data Access Layer

- [x] 2.1 Create `model/Payment.java` domain model with payment types (`SESSION`, `DEPOSIT`, `SETTLEMENT`, `REFUND`, `REVERSAL`, `THERAPY_PLAN`).
- [x] 2.2 Create `dao/PaymentDAO.java` with methods for recording payments, finding by patient ID, calculating daily cash register breakdowns, and executing reversals and refunds in single transactions.
- [x] 2.3 Update `dao/SessionDAO.java` to support atomic session and payment insertion within a single database transaction.

## 3. Searchable Patient Selector Component

- [x] 3.1 Implement reusable `ui/PatientSearchField.java` (or custom search popup controller) featuring name and space-insensitive phone number filtering using `FilteredList<Patient>`.
- [x] 3.2 Add color-coded balance badge and phone number formatting in candidate search result rows.
- [x] 3.3 Implement keyboard navigation (Down/Up arrow selection, Enter to commit, Escape to dismiss) and integrate into `session_form.fxml`.

## 4. Session Form & Dynamic Balance Feedback

- [x] 4.1 Update `session_form.fxml` replacing the static patient ComboBox with the searchable selector and relabeling payment input to "Espèces reçues aujourd'hui (DZD)".
- [x] 4.2 Update `controller/SessionFormController.java` to dynamically calculate default cash required based on patient advance credit or outstanding balance.
- [x] 4.3 Add real-time live preview label in `SessionFormController.java` describing the exact financial outcome (e.g., covered by advance, surplus retained, or debt incurred).
- [x] 4.4 Save session cost and actual cash received atomically via `SessionDAO` and `PaymentDAO`.

## 5. Patient Profile Payments Tab, Deposits & Refunds

- [x] 5.1 Update `patient_detail.fxml` to add a dedicated "Paiements & Versements" section with history table and action buttons (`+ Nouveau Versement`, `Rembourser`).
- [x] 5.2 Implement free deposit dialog in `controller/PatientDetailController.java` allowing standalone payments (`DEPOSIT` / `SETTLEMENT`) without session linkage.
- [x] 5.3 Implement reversal and correction dialog in `controller/PatientDetailController.java` prompting for mandatory reason and appending compensating `REVERSAL` rows.
- [x] 5.4 Implement cash refund dialog in `controller/PatientDetailController.java` validating amount against available credit and recording `REFUND` rows.

## 6. Cash Register & Dashboard Integration

- [x] 6.1 Update `controller/SessionController.java` to dynamically derive session payment status badges (`RÉGLÉ`, `COUVERT PAR AVANCE`, `PARTIEL`, `IMPAYÉ`) and display exact physical cash collected today.
- [x] 6.2 Update `controller/DashboardController.java` to calculate monthly collected revenue from the `payment` ledger rather than static session sums.

## 7. Verification & End-to-End Testing

- [x] 7.1 Verify compilation and package build (`mvn clean test-compile`).
- [x] 7.2 Create verification test `myerp/src/test/java/com/myerp/PaymentLedgerVerificationTest.java` verifying backfill parity, advance deduction, cash drawer accuracy, and reversal constraints.
- [x] 7.3 Run standalone verification test (`mvn -Dexec.mainClass=com.myerp.PaymentLedgerVerificationTest -Dexec.classpathScope=test exec:java`) on a disposable database.
