# Design: User Management & Role-Based Access Control

## Context

The application is a JavaFX 17 desktop ERP backed by an embedded SQLite database (`~/clinic/clinic.db`) that has been in active clinical use for six months. The system originally seeded a single default administrator (`admin / admin123`). The clinic now requires multiple user accounts with two privilege levels:
1. **Administrators (`Admin`)**: Have unrestricted access to all modules, including clinical operations, financial dashboards, worker payroll, and user administration.
2. **Employees (`Employé`)**: Routine clinical operators who manage patient records, therapy sessions, operational bills, and direct sales, but are strictly prohibited from viewing clinic financial summaries (`Accueil`), worker payroll details (`Employés`), or managing user credentials (`Utilisateurs`).

Crucially, zero loss or corruption of existing clinical and financial data can occur during rollout.

## Goals / Non-Goals

**Goals:**
- Provide a clean, robust user management interface (`user.fxml` and `user_form.fxml`) allowing administrators to list, filter, add, edit, and delete user accounts.
- Enforce strict role-based access control where restricted navigation buttons are completely hidden from employees, and their default post-login landing screen is `Patients`.
- Track the authenticated user across the application lifecycle in `UserSession`, showing an informative profile badge with their full name and role above the logout button.
- Enforce safety invariants preventing administrator lockout (no deleting the last administrator, no demoting the last administrator, no self-deletion).
- Implement an automated pre-startup SQLite backup routine and non-destructive schema normalization ensuring existing data is completely preserved.

**Non-Goals:**
- Remote multi-tenant cloud authentication or network SSO (the app is an on-premise local desktop SQLite application).
- Granular per-action permission matrices (permissions are cleanly delineated into two roles: `Admin` and `Employé`).
- Changing the password hashing scheme at this stage (plain-text passwords in `Users.passWord` remain for 100% backward-compatibility with the client's current credentials).

## Decisions

### 1. In-Memory Session Context via `UserSession`
- **Choice**: Introduce a lightweight static session holder `util.UserSession`.
- **Rationale**: JavaFX desktop applications are single-process, client-side GUI applications. A static session holder is standard, thread-safe on the JavaFX Application Thread, and easily accessible by all controllers without complex dependency injection frameworks.
- **Alternatives Considered**: Passing `User` manually across every `FXMLLoader` instance. This creates tight coupling and requires modifying constructor/factory parameters across every controller.

### 2. UI Restriction Strategy: Button Hiding (`visible=false, managed=false`)
- **Choice**: In `HomeController`, when the logged-in user is an employee, set `visible=false` and `managed=false` on `btnAccueil`, `btnWorkers`, and `btnUsers`.
- **Rationale**: Setting both properties ensures that JavaFX `VBox` layout does not leave empty visual gaps, keeping the sidebar compact, clean, and intuitive.
- **Alternatives Considered**: Disabling buttons (`disabled=true`) with a lock icon. Rejected because it clutters the UI for employees with options they can never use.

### 3. Default Landing Page Redirection
- **Choice**: If the logged-in user is an `Admin`, default navigation calls `handleNavAccueil(null)` (`dashboard.fxml`). If an `Employé`, default navigation calls `handleNavPatients(null)` (`patient.fxml`).
- **Rationale**: Employees cannot access the dashboard, so loading it on login would violate access restrictions or cause errors.

### 4. Non-Destructive Database Initialization & Pre-Launch Backup
- **Choice**: In `DatabaseInitializer.java`, before any SQL statements run:
  1. Check if `~/clinic/clinic.db` exists. If so, copy to `~/clinic/backups/clinic_backup_<timestamp>.db`.
  2. Maintain `CREATE TABLE IF NOT EXISTS Users`.
  3. Execute `UPDATE Users SET userType = 'Admin' WHERE userType IS NULL OR TRIM(userType) = '';`.
- **Rationale**: Existing tables and rows are never dropped or recreated. The pre-launch backup provides an absolute safety net in case of external operational failure.
- **Alternatives Considered**: Running an external migration script. Rejected because the client runs a packaged standalone desktop application.

### 5. Lockout Prevention Safeguards
- **Choice**: In `UserController` and `UserDAO`, guard against deleting the currently authenticated user (`UserSession.getCurrentUser().getId()`), and guard against deleting or demoting the last remaining `Admin`.
- **Rationale**: Prevents accidental administrative lockout where no admin exists to re-enable access.

## Risks / Trade-offs

- **[Risk] Existing user records lack `userType`** → **Mitigation**: Schema normalization runs `UPDATE Users SET userType = 'Admin' WHERE userType IS NULL OR TRIM(userType) = '';` on startup.
- **[Risk] Accidental deletion of the only Admin** → **Mitigation**: `UserDAO.countAdmins()` checks ensure an admin cannot be deleted or changed to employee if count is 1.
- **[Risk] Storage accumulation of backup files** → **Mitigation**: Backup files are timestamped and lightweight (SQLite DB is compact). We can retain the latest 10 backups and prune older ones if necessary.

## Migration Plan

1. The updated application jar/package is deployed to the client machine.
2. Upon first launch:
   - `DatabaseInitializer` detects `~/clinic/clinic.db` and creates an automatic snapshot in `~/clinic/backups/`.
   - Table initialization confirms all `IF NOT EXISTS` DDL statements.
   - Any existing admin account is normalized with `userType = 'Admin'`.
3. The client logs in with their existing credentials (`admin / admin123` or their custom credentials) and accesses the new `Utilisateurs` tab to create employee accounts.
4. Rollback: If rollback is ever required, the timestamped `.db` backup file in `~/clinic/backups/` can simply be restored to `~/clinic/clinic.db`.

## Open Questions

- *None*: Access scopes for Bills and Sales, UI hiding behavior, and sidebar badge placement were confirmed during exploration.
