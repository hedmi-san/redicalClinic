## 1. Search Helper & Data Access

- [x] 1.1 Create `util.PatientSearchHelper` with whitespace tokenization, word-order-independent matching, SQL `LIKE` escaping, and token highlight range computation.
- [x] 1.2 Update `dao.PatientDAO.getPatients` to match all whitespace-separated query tokens in any order against `name` while preserving space-insensitive phone matching and balance filtering.

## 2. Selector Controllers

- [x] 2.1 Update `AppointmentFormController.performPatientFilter` to filter candidates via `PatientSearchHelper.matches`.
- [x] 2.2 Update `SessionFormController.performPatientFilter` and `createHighlightedTextFlow` to filter candidates via `PatientSearchHelper.matches` and highlight matched tokens in any order.

## 3. Verification

- [x] 3.1 Add `com.myerp.PatientSearchVerificationTest` using a disposable `user.home` directory to verify Arabic/Latin reversed-order search, partial tokens, space-collapsed tokens, phone search, wildcard safety, and highlight ranges.
- [x] 3.2 Run Maven compilation and verification tests.
