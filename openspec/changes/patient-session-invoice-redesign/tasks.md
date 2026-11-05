## 1. Invoice PDF Generation Redesign (`InvoiceService.java`)

- [x] 1.1 Redesign the PDF table layout from 4 columns to 3 columns (`Date`, `Prestation / Traitement`, `Montant (DZD)`) with proper column widths and right-aligned amounts.
- [x] 1.2 Implement the ledger-aware financial summary footer in PDF showing Total Prestations, Montant réglé, and the net account status (`Reste à payer`, `Avance disponible`, or `Compte soldé`).
- [x] 1.3 Add invoice issuance date (`Date: dd/MM/yyyy`) and patient phone number to the PDF metadata header.

## 2. Interactive Selection Dialog (`patient_session_invoice_form.fxml` & Controller)

- [x] 2.1 Update `patient_session_invoice_form.fxml` to add a patient account header with a balance badge and a dynamic selection counter footer.
- [x] 2.2 Update `PatientSessionInvoiceFormController.java` to accept `Patient`, derive coverage badges (`RÉGLÉ`, `COUVERT (AVANCE)`, `IMPAYÉ`), and dynamically update live selected count and total.
- [x] 2.3 Update `PatientDetailController.java` (`printPatientBill`) to pass the refreshed `Patient` object into both the dialog controller and `InvoiceService`.

## 3. Verification & Testing

- [x] 3.1 Create standalone verification test `PatientInvoiceVerificationTest.java` testing invoice PDF generation across advance credit, outstanding debt, and settled accounts.
- [x] 3.2 Execute test with Maven exec plugin to verify zero regressions and exact balance rendering.
