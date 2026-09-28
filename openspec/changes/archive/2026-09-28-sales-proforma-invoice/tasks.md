## 1. Data Access & PDF Invoice Generation Service

- [x] 1.1 Add/verify query methods in `SoldDAO` to fetch all sales or filter sales across custom date ranges for invoice preparation
- [x] 1.2 Implement proforma PDF generation in `InvoiceService` (or dedicated `SalesInvoiceService`) utilizing Apache PDFBox 3.0.5, `/img/logo.png`, clinic header branding, proforma title, client details, line item table (Date, Article, Prix Unitaire, Quantité, Total), and Arabic font rendering via `ArabicTextHelper`

## 2. Selection Modal UI & Controller

- [x] 2.1 Create FXML layout `sale_invoice_form.fxml` featuring client name input, invoice date picker, date range filters (Start Date & End Date), keyword search field, and selection TableView with checkboxes
- [x] 2.2 Implement `SaleInvoiceFormController` with `FilteredList` filtering by date and keyword, "Select All" toggle, validation for non-empty selection, and getter methods for selected items and invoice metadata

## 3. Integration into Sales Page

- [x] 3.1 Add "Facture proforma" button to the header of `sold.fxml` styled consistently with the application design system
- [x] 3.2 Wire the button in `SoldController.java` to launch `SaleInvoiceFormController`, open `FileChooser` for saving the PDF, invoke the invoice generation service, and display a confirmation alert

## 4. Verification & Polish

- [x] 4.1 Verify Maven build compilation (`mvn clean compile` or `mvn test-compile`) with no errors or broken references
- [x] 4.2 Verify PDF generation visually, ensuring proper layout, logo placement, Arabic text shaping, and financial totals
