## Context

Cabinet Nour El Islam operates a desktop ERP built in Java 17 and JavaFX 17 with SQLite. The clinic manages direct counter sales of medical supplies, rehabilitation accessories, and consumables via the `Sold` entity and `SoldController`. While patient session billing exists via `InvoiceService` and `PatientSessionInvoiceFormController`, there is currently no facility to issue invoices for counter sales.

Clinics often need to produce a "Facture Proforma" (proforma invoice) for patients, insurance bodies, or corporate clients requesting a quote or formal billing statement for medical products sold. This document must display the clinic's official branding (including `myerp/src/main/resources/img/logo.png`), detailed line items, unit prices, quantities, total amounts in DZD, and customer metadata.

## Goals / Non-Goals

**Goals:**
- Provide a clear, prominent "Facture proforma" action button in `sold.fxml`.
- Implement a modal dialog (`SaleInvoiceFormController` and `sale_invoice_form.fxml`) to select items:
  - Date filtering (from date / to date date pickers) and text search filter (by item name) for rapid item lookup.
  - Interactive item selection table with individual checkboxes and a "Tout sélectionner" (Select All) toggle.
  - Customer information input field (Client name) and invoice issue date.
- Generate a proforma invoice PDF document using Apache PDFBox 3.0.5:
  - Embed the clinic logo from `/img/logo.png`.
  - Display clinic headers ("CABINET NOUR EL ISLAM - REEDUCATION FONCTIONNELLE ET MOTRICE").
  - Clear "FACTURE PROFORMA" title and reference/date section.
  - Structured line item table: Désignation (Article), Date, Prix Unitaire (DZD), Quantité, Total (DZD).
  - Summary block showing Total Général in DZD.
  - Cachet et signature (stamp and signature) block.
  - Robust handling of Arabic and Latin text via `ArabicTextHelper` and system fonts.
- Allow the user to choose where to save the generated PDF via `FileChooser`, and offer to open it upon completion.

**Non-Goals:**
- Database schema changes to `sold` table (existing table schema is sufficient; client name is captured at document generation time).
- Fiscal tax / VAT computation (the clinic operates in DZD with net amounts).
- Inventory stock decrementing (the system currently tracks sold records directly).

## Decisions

### 1. Dedicated Selection Modal vs. Inline Selection
- **Decision**: Create a dedicated modal dialog (`SaleInvoiceFormController` + `sale_invoice_form.fxml`) launched from a button on `sold.fxml`, rather than adding checkboxes into the main sales table.
- **Rationale**: Keeps the primary sales dashboard uncluttered for day-to-day CRUD operations. Follows the proven architecture of `PatientSessionInvoiceFormController`. Allows dedicated space for client name, invoice date, and advanced date range filtering.
- **Alternatives considered**:
  - *Inline checkboxes in main table*: Clutters the CRUD view and requires complex state management when filters or pagination change.

### 2. Date Filtering Strategy in the Modal
- **Decision**: Provide `DatePicker` controls for "Date début" and "Date fin" alongside a search `TextField`, driving a JavaFX `FilteredList`.
- **Rationale**: In-memory `FilteredList` over all sales or monthly sales is instantaneous, reactive, and does not require complex repeated database queries. Users can easily select a specific period (e.g. today, this week, custom date range) to quickly find and check the desired sold items.
- **Alternatives considered**:
  - *Restricting only to currently selected month*: Too limiting if a proforma spans items sold across month boundaries.

### 3. PDF Service Structure
- **Decision**: Add `generateSalesProformaInvoice(String clientName, String invoiceDate, List<Sold> items, File outputFile)` either as a method on `InvoiceService` or a specialized `SalesInvoiceService` sharing the PDFBox design system (colors, margins, table dimensions, logo handling).
- **Rationale**: Reuses existing Apache PDFBox 3.0.5 infrastructure, font loaders (`loadBoldFont`, `loadRegularFont`), and `ArabicTextHelper.processForPdf(...)` for consistency across all clinic documents.
- **Alternatives considered**:
  - *HTML to PDF conversion*: Adds external dependencies or headless browser overhead. PDFBox is already bundled and proven.

### 4. Client and Invoice Metadata Capture
- **Decision**: The modal dialog will include fields for "Nom du Client / Bénéficiaire" (defaulting to "Client au comptant" or empty) and "Date de la facture" (defaulting to current date).
- **Rationale**: Proforma invoices require a recipient name to be valid for insurance or reimbursement purposes.

## Risks / Trade-offs

- **[Risk] Long item lists overflowing a single page** → **Mitigation**: Calculate available page height before rendering each row and append new pages dynamically if the item count exceeds one page.
- **[Risk] Arabic text shaping in item or client names** → **Mitigation**: Route all string rendering through `ArabicTextHelper.processForPdf()` with Arial/Calibri font fallbacks, identical to `InvoiceService`.
- **[Risk] No items selected** → **Mitigation**: Disable the "Générer la facture" button or show a validation alert when zero items are selected.
- **[Risk] Missing or invalid logo resource** → **Mitigation**: Gracefully catch logo loading errors and render the text header properly if logo loading fails.
