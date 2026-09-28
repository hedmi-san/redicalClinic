## Why

Currently, the sales management page allows tracking sold items and counter supplies, but lacks any mechanism to produce formal billing documents for customers. Users need to issue proforma invoices ("Facture Proforma") containing selected sold items, filtered by date for quick retrieval, and branded with the clinic's official logo and details.

## What Changes

- **Sale Page Action**: Add a dedicated "Facture proforma" action button in the sales management header (`sold.fxml` & `SoldController.java`).
- **Interactive Item Selection Dialog**: Provide a modal dialog (`sale_invoice_form.fxml` & `SaleInvoiceFormController.java`) allowing users to:
  - Filter sold items dynamically by date range (start date / end date) or specific date, and filter by item keyword.
  - Select or deselect specific items using individual checkboxes or a "Select All" toggle.
  - Optionally specify recipient/client information (name, address/notes) and invoice metadata (invoice date, optional quotation/proforma number).
- **Proforma Invoice PDF Generation**: Extend or introduce PDF generation functionality (using Apache PDFBox 3.0.5 and `ArabicTextHelper`) to output a formatted Proforma Invoice PDF ("Facture Proforma"):
  - Display the clinic logo (`/img/logo.png`) and clinic identity header (Cabinet Nour El Islam).
  - Include client/recipient section and document date/reference.
  - Format an itemized table with columns: Désignation (Article), Date, Prix Unitaire (DZD), Quantité, Total (DZD).
  - Compute total amounts (Total HT, Net à Payer en DZD) with formatted currency and signature/stamp placeholders.
  - Allow saving to disk via JavaFX `FileChooser`.

## Capabilities

### New Capabilities
- `sales-invoice-selection`: Interactive selection and date-based filtering interface for picking sold items to include in an invoice.
- `sales-proforma-pdf`: PDF document generation for branded proforma invoices ("Facture Proforma") incorporating the clinic logo, itemized table, and total summary.

### Modified Capabilities
*(None - no prior base specs exist for direct sales billing)*

## Impact

- **UI / Views**:
  - `myerp/src/main/resources/fxml/pages/sold.fxml` (add invoice button)
  - `myerp/src/main/resources/fxml/pages/sale_invoice_form.fxml` (new dialog)
  - `myerp/src/main/resources/css/pages/sold.css` (or dedicated dialog css)
- **Controllers**:
  - `myerp/src/main/java/controller/SoldController.java` (open invoice dialog and orchestrate export)
  - `myerp/src/main/java/controller/SaleInvoiceFormController.java` (new controller for item selection)
- **Services / DAOs**:
  - `myerp/src/main/java/service/InvoiceService.java` or `SalesInvoiceService.java` (PDFBox proforma invoice rendering)
  - `myerp/src/main/java/dao/SoldDAO.java` (helper queries for date-range retrieval if needed)
- **Dependencies**: No new external dependencies required; utilizes existing JavaFX 17 and Apache PDFBox 3.0.5.
