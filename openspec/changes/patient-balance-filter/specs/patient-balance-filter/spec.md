# Patient Balance Filter Specification

## Purpose
Enables clinic staff to filter and find patients on the patient page based on their financial balance status (in debt, in advance, or settled), combined with partial name or phone text search.

## Requirements

### Requirement: Patient Balance Filter Dropdown
The patient page SHALL provide a filter selector allowing the user to view patients by balance state.

#### Scenario: Default view displays all patients
- **WHEN** the patient page is opened
- **THEN** the balance filter defaults to "Tous les soldes" and lists all registered patients.

#### Scenario: Filtering patients in debt (En dette)
- **WHEN** the user selects "En dette"
- **THEN** the system displays only patients whose total cost exceeds their total paid by more than 0.01 DZD (`balance > 0.01`).

#### Scenario: Filtering patients in advance (En avance)
- **WHEN** the user selects "En avance"
- **THEN** the system displays only patients whose total paid exceeds their total cost by more than 0.01 DZD (`balance < -0.01`).

#### Scenario: Filtering settled patients (Réglé)
- **WHEN** the user selects "Réglé"
- **THEN** the system displays only patients whose balance is zero within a 0.01 DZD tolerance (`ABS(balance) <= 0.01`).

### Requirement: Multi-Criteria Search
The system SHALL support simultaneous filtering by balance state and searching by patient name or phone number.

#### Scenario: Combining balance filter and text search
- **GIVEN** the user has selected "En dette"
- **WHEN** the user enters a search term into the search input
- **THEN** the patient table displays only patients who match both the search query and the "En dette" condition.

#### Scenario: Space-insensitive phone search
- **WHEN** the user searches for a phone number with or without spaces
- **THEN** matching patients with that phone number are returned regardless of space formatting.

### Requirement: Visual Balance Indicators & Result Count
The patient page SHALL display visual feedback on match counts and patient balance statuses.

#### Scenario: Displaying result count
- **WHEN** the patient list updates following a search or filter change
- **THEN** a count label indicates the total number of matching patients displayed.

#### Scenario: Visual balance badges in table
- **WHEN** patients are displayed in the table
- **THEN** the balance column displays a color-coded badge indicating debt (red), advance credit (green), or settled status (neutral).

