# Proposal: User Management & Role-Based Access Control

## Why

The clinic application was shipped to the client with a single default administrator account. As the clinic expands its team, additional staff members (employees) require access to handle patient registrations, session tracking, billing, and direct sales. However, sensitive clinic areas—specifically the financial dashboard, staff/payroll records, and user administration—must be strictly restricted to administrators. Furthermore, because the app has been running in production for six months, this enhancement must guarantee complete preservation of existing clinical and financial data with zero downtime or loss.

## What Changes

- **Role Separation**: Introduce distinct roles (`Admin` and `Employé`) with clear permission boundaries.
- **Dynamic Navigation & Restrictions**:
  - Automatically hide restricted navigation items (`Accueil` / Dashboard, `Employés` / Workers, and `Utilisateurs`) from the employee navigation menu.
  - Automatically route employees to `Patients` as their default landing page upon login.
- **Session Context & Indicator**:
  - Preserve the authenticated `User` in an application-wide `UserSession`.
  - Display the logged-in user's name and role badge (e.g., `Sara (Employé)`) directly above the logout button.
- **User Management Screen**:
  - Provide a dedicated UI (`user.fxml`) and dialog (`user_form.fxml`) allowing administrators to list, search, add, edit, and delete system users.
  - Enforce critical safety guardrails: prevent self-deletion of the active user, prevent deleting the last remaining administrator, and enforce unique usernames.
- **Database Zero-Risk Safety**:
  - Introduce an automatic pre-startup database snapshot routine that copies `clinic.db` to a timestamped backup before any operations.
  - Apply safe, non-destructive normalization for existing user records without altering table structures or dropping data.

## Capabilities

### New Capabilities
- `user-management`: Management of system accounts (listing, searching, creating, updating, deleting) with validation and administrator lockout safeguards.
- `role-based-access`: Session tracking and dynamic UI access control distinguishing administrator and employee privilege levels.
- `database-safety`: Pre-launch backup snapshots and non-destructive database verification preserving existing production clinic data.

### Modified Capabilities
*(None; no existing specs in `openspec/specs/`)*

## Impact

- **Database**: `~/clinic/clinic.db` schema remains intact; existing records in `Users` with null/empty `userType` are normalized to `Admin`. Automatic backup created in `~/clinic/backups/`.
- **Backend / Controllers**:
  - `LoginController.java` updates `UserSession` upon login.
  - `HomeController.java` adapts navigation buttons, routing, and user profile badge based on role.
  - New `UserController.java` and `UserFormController.java` manage the user administration lifecycle.
- **Data Access Layer**: `UserDAO.java` gains full CRUD, search, and administrator counting methods.
- **UI / Views**:
  - `home.fxml` & `home.css` updated for user badge and `Utilisateurs` navigation button.
  - New `user.fxml` and `user_form.fxml` layouts.
- **Dependencies**: No new external dependencies required; uses existing JavaFX 17 and SQLite JDBC.
