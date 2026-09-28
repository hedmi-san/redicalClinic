# Sales Invoice Selection Specification

## Purpose
Provides an interactive selection dialog to filter sold items by date range and keyword, select items via checkboxes, and configure recipient details for proforma invoice generation.

## Requirements

### Requirement: Open Sales Invoice Selection Dialog
The sales management view SHALL provide an action button that opens an invoice item selection dialog when clicked.

#### Scenario: User clicks proforma invoice button
- **WHEN** the user clicks the "Facture proforma" button on the sales management page
- **THEN** the system SHALL display a modal dialog displaying available sold items for invoice generation

### Requirement: Filter Sold Items by Date
The selection dialog SHALL provide date filtering controls allowing users to filter items by date range or specific dates.

#### Scenario: User filters items by start and end date
- **WHEN** the user selects a start date and/or end date in the date filter controls
- **THEN** the items table SHALL dynamically display only sold items with sale dates falling within the specified date range

#### Scenario: User clears date filter
- **WHEN** the user resets or clears the date filter controls
- **THEN** the items table SHALL display all available sold items

### Requirement: Search Sold Items by Name
The selection dialog SHALL provide a search field allowing users to filter items by article name.

#### Scenario: User searches for an item by keyword
- **WHEN** the user enters search text in the item search field
- **THEN** the table SHALL display only items whose name contains the search text, case-insensitively

### Requirement: Interactive Item Selection
The dialog SHALL allow users to select individual items via checkboxes and provide a "Select All" toggle.

#### Scenario: User selects individual items
- **WHEN** the user clicks the checkbox for one or more items
- **THEN** those items SHALL be marked as selected for inclusion in the invoice

#### Scenario: User toggles Select All
- **WHEN** the user checks the "Select All" checkbox
- **THEN** all currently visible items in the table SHALL be selected

### Requirement: Capture Client Name and Invoice Date
The dialog SHALL provide inputs for recipient client name and invoice date before confirming generation.

#### Scenario: User enters client name and validates
- **WHEN** the user specifies a client name and confirms the dialog with selected items
- **THEN** the system SHALL proceed to invoice PDF generation with the specified client details and selected items

#### Scenario: User attempts to confirm with no items selected
- **WHEN** the user clicks the generate button without selecting any item
- **THEN** the system SHALL prevent proceeding and display a warning message prompting the user to select at least one item
