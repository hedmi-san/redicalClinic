## Why

In the current system, payments are tightly coupled to individual sessions via `session.paidAmount`. When a patient pays in advance (e.g., paying 2,000 DA for a 500 DA session to leave 1,500 DA as credit for future visits), subsequent sessions require workers to either enter 0 DA (which flags the session as red "IMPAYÉ") or re-enter 500 DA (which corrupts the daily cash register and inflates patient payment totals). Furthermore, selecting patients in the session form uses a plain, non-searchable dropdown that is difficult to navigate as the clinic roster grows.

Decoupling session treatment costs from cash payments into an append-only payment ledger and replacing the static dropdown with a keyboard-navigable, space-insensitive patient search selector solves both worker frustration and calculation discrepancies while preserving 100% of historical financial data.

## What Changes

- **Append-Only Payment Ledger (`payment` table)**: Introduce an immutable financial ledger tracking all cash inflows and outflows (`SESSION`, `DEPOSIT`, `SETTLEMENT`, `REFUND`, `REVERSAL`, `THERAPY_PLAN`) with audit metadata (`createdBy`, `createdAt`, `paymentDate`, `reason`, `reversalOf`).
- **Mathematical Separation of Balance vs. Daily Cash**:
  - Patient Balance is computed as $\sum(\text{Session Costs}) + \sum(\text{Therapy Plan Costs}) - \sum(\text{Payments})$. Negative represents patient credit/advance; positive represents outstanding debt.
  - Daily Cash Register is computed purely from payments entered on that business date ($\sum_{\text{paymentDate} = \text{Date}} \text{amount}$).
- **Streamlined Session Form UX**:
  - Replaces "Montant Payé" with "Espèces reçues aujourd'hui" defaulting to 0 DA if covered by advance credit, or the remaining amount owed.
  - Live feedback banner dynamically previews the financial impact (e.g. *"Séance couverte par l'avance. Nouveau solde : 1 000 DA en avance"*).
  - Session status badge is derived dynamically from patient coverage rather than stored as a static string.
- **Fast Searchable Patient Selector**:
  - Filters in real time as the user types name or phone digits (ignoring spaces).
  - Displays patient phone number and color-coded balance badge directly in search results.
  - Fully navigable via keyboard (Up/Down arrows, Enter).
- **Free Deposits, Refunds & Reversals in Patient Profile**:
  - Add `[+ Nouveau Versement]` for recording advances or debt payments without creating dummy sessions.
  - Add audited `[Extourner / Corriger]` and `[Rembourser]` actions with mandatory reasons.
- **Idempotent Zero-Loss Migration**:
  - Automatic pre-migration backup of `clinic.mv.db`.
  - Backfill existing `session.paidAmount > 0` and legacy `therapyPlan.cost > 0` records into `payment` rows tagged with `createdBy = 'MIGRATION'`.
  - Recalculate and synchronize `patient.totalCost` and `patient.totalPaid` with zero discrepancy.

## Capabilities

### New Capabilities
- `patient-payment-ledger`: Covers append-only financial ledger operations, balance calculations, free deposits, credit refunds, and audited reversals.
- `patient-search-selector`: Covers the fast, keyboard-navigable patient search component with space-insensitive phone lookup and live balance badges.

### Modified Capabilities
- `database-safety`: Extends database safety guarantees with automated, idempotent backfill migration of historical payments and row-parity validation.

## Impact

- **Database**: Adds `payment` table; preserves existing `session` and `therapyPlan` schemas without destructive alterations.
- **DAOs & Services**:
  - New `PaymentDAO` for ledger transactions, reversals, and cash breakdowns.
  - `SessionDAO` updated to record sessions and optional cash payments atomically within a single transaction.
  - `PatientDAO` updated to calculate totals from `payment` and load search candidates with precomputed balances.
- **Controllers & Views**:
  - `SessionFormController` & `session_form.fxml`: Searchable patient selector and cash-received live summary.
  - `SessionController` & `session.fxml`: Derived session payment badges and accurate daily cash register summary.
  - `PatientDetailController` & `patient_detail.fxml`: New Payments & Deposits tab with reversal/refund actions.
  - `DashboardController`: Monthly actual profit calculated from the payment ledger.
