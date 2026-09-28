# Sales Proforma PDF Invoice Specification

## Purpose
Generates branded, elegant proforma invoice PDF documents using Apache PDFBox 3.0.5 with clinic header details, official logo, itemized table, total summary in DZD, and Arabic/Latin text shaping.

## Requirements

### Requirement: Branded Clinic Header and Logo
The proforma invoice PDF document SHALL include the clinic logo from `/img/logo.png` and official clinic identification details.

#### Scenario: Rendering clinic header
- **WHEN** the proforma invoice PDF is generated
- **THEN** the top section of the page SHALL display the clinic logo image on the left and clinic name/specialty ("CABINET NOUR EL ISLAM - REEDUCATION FONCTIONNELLE ET MOTRICE") on the right, separated by a teal horizontal rule

### Requirement: Proforma Invoice Metadata
The generated document SHALL explicitly display the title "FACTURE PROFORMA", the invoice date, and the recipient client name.

#### Scenario: Displaying invoice title and client information
- **WHEN** the document is created with a client name and invoice date
- **THEN** the document SHALL display "FACTURE PROFORMA" prominently in the header area, along with the invoice date and client name

### Requirement: Itemized Line Items Table
The document SHALL contain a structured table displaying all selected sold items with quantities, unit prices, and line totals.

#### Scenario: Generating line item rows
- **WHEN** selected items are rendered into the table
- **THEN** the table SHALL display columns for Date, Désignation (Article), Prix Unitaire (DZD), Quantité, and Total (DZD) with alternating row shading and right-aligned numeric amounts

### Requirement: Total Financial Summary
The document SHALL compute and display the sum total of all selected items in DZD.

#### Scenario: Displaying grand total
- **WHEN** all item rows have been rendered
- **THEN** the table footer SHALL display the calculated grand total in Algerian Dinars (DZD) styled with bold text and accent background

### Requirement: Bilingual Character Support
The invoice generation engine SHALL correctly shape and render Arabic text as well as Latin text in item descriptions and client names.

#### Scenario: Item name contains Arabic text
- **WHEN** an item name contains Arabic characters
- **THEN** the text SHALL be shaped and rendered visually right-to-left using `ArabicTextHelper` without reversed or disconnected glyphs

### Requirement: Save and Open Dialog
The system SHALL prompt the user to choose an export location and confirm successful export.

#### Scenario: User saves proforma invoice
- **WHEN** the user confirms generation
- **THEN** a `FileChooser` SHALL prompt the user to choose a `.pdf` destination path, save the document, and notify the user upon success
