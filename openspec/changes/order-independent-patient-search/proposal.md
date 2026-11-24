## Why

Clinic staff frequently search for patients using Arabic or Latin multi-word names where the given name and family name may be typed in either order (for example, searching `"عبد الرحمان العمري"` for a patient stored as `"العمري عبد الرحمان"`). Previously, patient search required the entered query to appear as a single contiguous substring in exact word order, and the appointment/session patient selectors stripped all spaces from the query without stripping spaces from the patient name, causing valid multi-word searches to return no results.

## What Changes

- **Shared Search Utility (`util.PatientSearchHelper`)**:
  - Splits search queries on whitespace into tokens and matches patients whose name contains every token regardless of word order (also supporting space-collapsed tokens such as `"عبدالرحمان"` matching `"عبد الرحمان"`).
  - Preserves space-insensitive phone number matching and provides SQL `LIKE` wildcard escaping.
  - Computes merged highlight character ranges for matched query tokens in any order.
- **DAO Search (`dao.PatientDAO.getPatients`)**:
  - Builds per-token `AND` predicates on `LOWER(name)` (and space-stripped `name`) alongside space-insensitive phone matching, using explicit `ESCAPE '!'` handling.
- **Form Patient Selectors (`AppointmentFormController`, `SessionFormController`)**:
  - Replaces the single-substring `cleanQuery` filter with `PatientSearchHelper.matches(...)` so real-time patient dropdowns work regardless of word order.
  - Updates `SessionFormController.createHighlightedTextFlow` to highlight all matched tokens regardless of order.
- **Verification**:
  - Adds `com.myerp.PatientSearchVerificationTest` covering Arabic and Latin word-order-independent searches, space-collapsed compound tokens, phone search, SQL wildcard escaping, and highlight ranges against a disposable temp database.

## Capabilities

### Modified Capabilities
- `patient-search-selector`: Supports word-order-independent multi-token name matching and multi-token highlighting in real-time patient search selectors.

## Impact

- **Utilities**: New `util.PatientSearchHelper`.
- **DAOs**: Updated `dao.PatientDAO`.
- **Controllers**: Updated `controller.AppointmentFormController` and `controller.SessionFormController`.
- **Tests**: New `com.myerp.PatientSearchVerificationTest`.
