## Context

In October 2026, the application transitioned to an append-only payment ledger (`payment` table) where patient balance is dynamically computed as:
$$\text{Balance} = \text{totalCost} - \text{totalPaid}$$
Sessions covered by advances are recorded with `session.paidAmount = 0.00` (cash received = 0) and derive their `COUVERT (AVANCE)` status dynamically from account coverage. Standalone deposits ("Versements") are recorded directly into the ledger without a session ID.

However, `InvoiceService.java` and `PatientSessionInvoiceFormController.java` were never synchronized with this architecture. They still display a 4-column table (`Date | Traitement | Coût | Payé`) where the "Payé" column reads raw `session.paidAmount`. This creates severe discrepancies:
1. Sessions covered by patient advances show "0,00 DA" paid.
2. The total row shows "0,00 DA" paid even when the patient has paid thousands of dinars in advance.
3. If a patient owes debt or paid via profile deposits, the document does not display "Reste à payer", "Avance", or the current account balance.
4. The selection dialog gives no visual feedback about the patient's balance or the cumulative total of selected sessions.

## Goals / Non-Goals

**Goals:**
- Replace the confusing "Payé" column on the patient invoice PDF with a clean, professional 3-column table: `Date | Prestation / Acte | Montant (DZD)`.
- Introduce an account-aligned Financial Breakdown Box in the PDF footer showing:
  - Total des prestations sélectionnées
  - Montant réglé / Versements pris en compte
  - Net statement: `Reste à payer` (if debt), `Avance disponible` (if credit), or `Compte soldé` (if zero).
- Add invoice metadata: issuance date (`Date: dd/MM/yyyy`) and patient phone number.
- Upgrade the session selection dialog (`patient_session_invoice_form.fxml` & `PatientSessionInvoiceFormController`):
  - Patient header with color-coded balance badge matching `patient_detail.fxml`.
  - Dynamic live selection counter in the footer (*X séances sélectionnées — Total : Y DZD*).
  - Table coverage status badge column (`RÉGLÉ`, `COUVERT (AVANCE)`, `IMPAYÉ`).

**Non-Goals:**
- Altering the database schema or `payment` table.
- Modifying sales proforma invoices (`SoldController.java`).
- Multi-page invoice pagination (existing clinic invoices are limited to standard session batches; single-page layout remains standard).

## Decisions

### Decision 1: 3-Column Table on Invoice PDF instead of Raw Cash Column
- **Choice**: Display `Date (100pt) | Prestation / Traitement (280pt) | Montant (DZD) (115pt)`.
- **Rationale**: In professional medical billing and ERP standards, invoice line items list the services rendered and their fees. Cash inflows occur through the payment ledger and apply to the patient's account as a whole. Removing the line-by-line "Payé" column prevents confusing lines where advance-covered treatments show 0,00 DA or debt payments show inflated numbers.
- **Alternatives Considered**: 
  - *Dynamic line-level payment allocation*: Splitting payments across line items was considered, but creates confusion when patients make partial advance deposits or profile settlements. Grouping payments in the invoice footer is cleaner and standard.

### Decision 2: Dual Financial Summary (Invoice Total & Account Balance)
- **Choice**: The invoice footer displays:
  1. `TOTAL PRESTATIONS` (sum of selected session costs).
  2. `Montant réglé / Couvert` (amount covered by ledger payments).
  3. `Reste à payer` (remaining balance due on account if balance > 0), `Avance restante` (if credit balance < 0), or `Compte soldé` (if balance == 0).
- **Rationale**: Gives both the patient and clinic full clarity: the patient sees the cost of these specific sessions alongside the exact status of their account.

### Decision 3: Enriched Selection Modal with Live Observable Total
- **Choice**: Pass the full `Patient` domain model into `PatientSessionInvoiceFormController`. Add an `ObservableList` listener / check change listener that updates a footer label dynamically:
  $$\text{count} = \sum [\text{selected}], \quad \text{sum} = \sum_{\text{selected}} \text{session.getCost()}$$
- **Rationale**: Workers previously had no way of knowing how much the selected sessions amounted to before generating the PDF.

## Risks / Trade-offs

- **[Risk] Confusion when selecting a subset of sessions while patient has global debt** → **Mitigation**: Clearly separate "Total de la facture" from "Solde global du compte (Reste dû)".
- **[Risk] Arabic text shaping in patient name or treatment** → **Mitigation**: Route all strings through `ArabicTextHelper.processForPdf()` with Arial/Calibri TrueType font loaders.
- **[Risk] Vertical space overflow on PDF with many sessions** → **Mitigation**: Standardize row height to 22pt, keep header compact (70pt), and reserve 90pt for footer summary, comfortably accommodating up to 20 sessions on a single A4 page.

