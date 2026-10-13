## ADDED Requirements

### Requirement: Real-Time Searchable Patient Selector
The system SHALL provide a fast, searchable patient selection component for session creation and scheduling dialogs.

#### Scenario: Searching by partial name or space-insensitive phone
- **WHEN** a user enters text or phone digits into the patient search field
- **THEN** the system filters matching patients in real time, matching either patient name or phone number with whitespace characters stripped.

#### Scenario: Visual balance indicator in search results
- **WHEN** the patient search dropdown displays candidate patients
- **THEN** each candidate row clearly shows the patient name, phone number, and a color-coded badge indicating advance credit, debt, or up-to-date status.

#### Scenario: Keyboard-only navigation and selection
- **WHEN** a user uses the Up and Down arrow keys in the search input and presses Enter
- **THEN** the highlighted candidate patient is immediately selected and displayed in the active patient card.
