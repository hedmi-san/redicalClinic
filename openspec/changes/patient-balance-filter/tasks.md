## 1. Model & Data Access

- [x] 1.1 Create `model.BalanceFilter` enum with values `ALL`, `DEBT`, `ADVANCE`, `SETTLED`, french display names, and helper mapping methods.
- [x] 1.2 Implement `PatientDAO.getPatients(String searchTerm, BalanceFilter filter)` supporting combined search and balance SQL conditions, while keeping backward compatibility for `getAllPatients()` and `searchPatients()`.

## 2. UI & Controller Integration

- [x] 2.1 Update `patient.fxml` with `balanceFilterCombo` and `patientCountLabel` next to the search input.
- [x] 2.2 Update `PatientController` to bind the balance filter dropdown, trigger immediate reloading on selection, update the count label, and render color-coded balance badges in `balanceColumn` with numeric sorting.
- [x] 2.3 Update `patient.css` to add `.filter-combo`, `.patient-count-label`, and balance badge styling (`.balance-badge-debt`, `.balance-badge-advance`, `.balance-badge-settled`).

## 3. Verification

- [x] 3.1 Create `PatientBalanceFilterVerificationTest` covering all filter modes (ALL, DEBT, ADVANCE, SETTLED) and combined text queries.
- [x] 3.2 Build and run tests to verify compilation and assertions.
