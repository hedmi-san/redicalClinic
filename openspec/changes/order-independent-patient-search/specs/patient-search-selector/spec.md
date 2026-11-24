## MODIFIED Requirements

### Requirement: Real-Time Searchable Patient Selector
The system SHALL provide a fast, searchable patient selection component for session creation and scheduling dialogs.

#### Scenario: Searching by partial name or space-insensitive phone
- **WHEN** a user enters text or phone digits into the patient search field
- **THEN** the system filters matching patients in real time, matching either all whitespace-separated query tokens against the patient name in any order or the phone number with whitespace characters stripped.

#### Scenario: Searching multi-word names in reverse or arbitrary word order
- **WHEN** a patient is stored as `"العمري عبد الرحمان"` and the user searches for `"عبد الرحمان العمري"`
- **THEN** the patient appears in the filtered results and matched tokens are highlighted in the candidate display.
