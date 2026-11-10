## Why

The patient management page currently displays the entire patient roster or filters only by matching text (name or phone). Clinic staff and receptionists regularly need to quickly identify patients with specific financial statuses—specifically patients in debt (*en dette*), patients with advance credits (*en avance*), or patients with settled accounts (*réglé*). Without dedicated balance filtering, staff must visually scan hundreds of rows or manually calculate receivables.

Adding an instant balance filter to the patient page allows staff to quickly filter patient lists by balance status, combine it with real-time text search, view live match counts, and see clear color-coded balance badges in the table.

## What Changes

- **Balance Filter Enum (`model.BalanceFilter`)**:
  - Provides type-safe filter states: `ALL` ("Tous les soldes"), `DEBT` ("En dette"), `ADVANCE` ("En avance"), and `SETTLED` ("Réglé").
- **DAO Multi-Criteria Filtering (`PatientDAO.getPatients`)**:
  - Extends `PatientDAO` with combined text search (name, phone, space-insensitive phone) and SQL-level balance predicate:
    - `DEBT`: `(COALESCE(totalCost, 0.0) - COALESCE(totalPaid, 0.0)) > 0.01`
    - `ADVANCE`: `(COALESCE(totalCost, 0.0) - COALESCE(totalPaid, 0.0)) < -0.01`
    - `SETTLED`: `ABS(COALESCE(totalCost, 0.0) - COALESCE(totalPaid, 0.0)) <= 0.01`
    - `ALL`: no balance restriction.
- **Patient Controller & View Integration (`patient.fxml`, `PatientController`, `patient.css`)**:
  - Adds a modern `ComboBox<BalanceFilter>` alongside the existing search field.
  - Adds a live patient match count label showing the number of filtered results.
  - Debounced text search (200ms) and instant filter selection triggers seamless list reload.
  - Custom cell factory for `balanceColumn` displaying styled balance badges (`badge-none` for debt, `badge-full` for credit/advance, neutral for settled) with numeric sorting support.
- **Verification**:
  - `com.myerp.PatientBalanceFilterVerificationTest` testing all filter scenarios (all, debt, advance, settled, combined text search) against an isolated database.

## Capabilities

### New Capabilities
- `patient-balance-filter`: Filter and search patients on the patient management page by balance state (in debt, in advance, settled) combined with text query.

## Impact

- **Models**: New `model.BalanceFilter`.
- **DAOs**: Enhanced `PatientDAO` with `getPatients(String searchTerm, BalanceFilter filter)`.
- **Controllers & Views**: `PatientController`, `patient.fxml`, and `patient.css`.
- **Tests**: `PatientBalanceFilterVerificationTest`.

