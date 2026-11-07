# Patient Session Invoice Specification

## Purpose
Defines the 3-column prestation table, ledger-synchronized financial breakdown footer, and interactive session selection modal for patient session invoices.

## Requirements

### Requirement: 3-Column Prestation Table in Patient Invoice PDF
The system SHALL generate patient session invoice PDFs with a clean 3-column table format (`Date`, `Prestation / Traitement`, `Montant (DZD)`) removing the misleading raw paid-amount column.

#### Scenario: Rendering individual session line items
- **WHEN** the user generates an invoice for selected patient sessions
- **THEN** each table row displays the session date, shaped treatment description (supporting Arabic and Latin), and session cost formatted in DZD currency.

#### Scenario: Invoice metadata and header
- **WHEN** the invoice PDF is rendered
- **THEN** the document header displays the clinic branding, invoice title "Facture des séances", issuance date (`dd/MM/yyyy`), patient full name, and patient contact phone number.

### Requirement: Ledger-Synchronized Financial Breakdown Footer
The system SHALL compute and display an accurate financial breakdown footer reflecting the selected prestations and the patient's actual account balance from the payment ledger.

#### Scenario: Patient with outstanding debt (Balance > 0)
- **WHEN** an invoice is generated for a patient whose total charges exceed total payments
- **THEN** the footer displays "Total des prestations", "Montant réglé", and an explicit "Reste à payer" row showing the remaining balance due.

#### Scenario: Patient with advance credit (Balance < 0)
- **WHEN** an invoice is generated for a patient with sufficient advance credit from prior deposits
- **THEN** the footer displays "Total des prestations" as covered, "Montant réglé", and an "Avance disponible" row highlighting the remaining credit balance.

#### Scenario: Patient with settled account (Balance == 0)
- **WHEN** an invoice is generated for a patient whose payments exactly equal total charges
- **THEN** the footer displays "Total des prestations", "Montant réglé", and "Compte soldé (0,00 DZD)".

### Requirement: Interactive Session Selection Modal with Balance Badge & Live Totals
The system SHALL present an interactive modal dialog allowing the user to select sessions for the invoice with live account context and real-time total calculation.

#### Scenario: Viewing patient account context in modal
- **WHEN** the user clicks "Imprimer La Facture" from the patient detail page
- **THEN** the dialog displays the patient's name and a color-coded account balance badge matching the patient profile (`Avance`, `Reste`, or `Soldé`).

#### Scenario: Live dynamic selection counter
- **WHEN** the user toggles individual session checkboxes or clicks "Tout sélectionner"
- **THEN** the dialog footer immediately recalculates and updates the count of selected sessions and their cumulative cost (e.g., "3 séance(s) sélectionnée(s) — Total : 4 500,00 DZD").

#### Scenario: Coverage status badge in table
- **WHEN** sessions are listed in the modal selection table
- **THEN** each session displays its date, treatment note, cost, and a derived coverage status badge (`RÉGLÉ`, `COUVERT (AVANCE)`, or `IMPAYÉ`) reflecting actual ledger coverage rather than unallocated cash digits.

