## Why

When the append-only payment ledger was introduced to decouple session treatment costs from actual cash inflows, `InvoiceService` and `PatientSessionInvoiceFormController` were not updated. Consequently, the patient session invoice ("Facture des séances") continues to read raw `session.paidAmount` per line item. 

When a patient's balance is different than 0 (they hold advance credit from earlier deposits, or carry outstanding debt), or when they settle their balance via profile deposits, the invoice generates incorrect output: sessions covered by advances show "0,00 DA" paid, total invoice amounts falsely claim zero payments, and the document completely lacks a net balance breakdown ("Reste à payer", "Avance", or "Soldé"). Redesigning the invoice to a clean 3-column service table with a ledger-synchronized financial summary, along with an enriched selection modal, restores billing accuracy and client trust.

## What Changes

- **3-Column Invoice PDF Line Items**: Replaces the misleading 4th column ("Payé") with a professional, standard prestation format: `Date | Prestation / Acte | Montant (DZD)`.
- **Ledger-Aware Financial Summary on PDF**:
  - Computes and displays the sum of selected prestations ("Total Prestations").
  - Displays the client's payment and account status: "Montant réglé / Versements", "Reste à payer" (if debt remains), "Avance disponible" (if credit remains), or "Compte soldé" (if zero balance).
- **Invoice Metadata & Header Polish**: Adds invoice issuance date (`Date: dd/MM/yyyy`) and patient phone contact info alongside the clinic header and Arabic/Latin patient name.
- **Enhanced Session Selection Dialog (`patient_session_invoice_form.fxml`)**:
  - Displays a patient header with name and live, color-coded account balance badge (e.g., `Avance: 2 000,00 DA` in teal, `Reste: 1 500,00 DA` in rose, or `0,00 DA (Réglé)` in emerald).
  - Displays a live dynamic counter in the dialog footer updating as sessions are checked/unchecked (e.g. `2 séance(s) sélectionnée(s) — Total : 3 000,00 DZD`).
  - Updates table columns to show coverage status badges (`RÉGLÉ`, `COUVERT (AVANCE)`, `IMPAYÉ`) reflecting actual ledger coverage rather than unallocated cash digits.

## Capabilities

### New Capabilities
- `patient-session-invoice`: Covers the redesigned session billing dialog and the PDF invoice layout aligning with the patient payment ledger.

### Modified Capabilities
<!-- None -->

## Impact

- **Controllers & Views**:
  - `myerp/src/main/resources/fxml/pages/patient_session_invoice_form.fxml`: Patient badge header, live selection summary footer, refined table columns.
  - `myerp/src/main/java/controller/PatientSessionInvoiceFormController.java`: Patient context binding, dynamic selection sum calculation, coverage status mapping.
  - `myerp/src/main/java/controller/PatientDetailController.java`: Pass full `Patient` object and refreshed balance into the selection controller.
- **Services**:
  - `myerp/src/main/java/service/InvoiceService.java`: 3-column session table layout, financial footer breakdown with account balance, invoice date rendering.
- **Verification Tests**:
  - New verification test `PatientInvoiceVerificationTest.java` validating PDF output structure, balance calculations, and dialog selection behavior.

