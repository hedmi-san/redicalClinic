# Role-Based Access Control Specification

## Purpose
Enforces role-based view visibility, navigation restrictions, session tracking, and routing between Administrator and Employee accounts.

## Requirements

### Requirement: Application User Session
The system SHALL maintain the authenticated user profile in a global session context upon successful login and clear this context upon logout.

#### Scenario: Storing session on login
- **WHEN** valid credentials are submitted on the login screen
- **THEN** the authenticated user object is stored in `UserSession` and the main application window opens

#### Scenario: Clearing session on logout
- **WHEN** the user clicks the logout button
- **THEN** the active session context is cleared, the main window closes, and the login window is displayed

### Requirement: Role-Based Navigation Visibility
The system SHALL dynamically configure sidebar navigation buttons according to the authenticated user's role, completely hiding restricted sections from employees.

#### Scenario: Administrator navigation display
- **WHEN** an administrator logs in
- **THEN** all navigation buttons (`Accueil`, `Patients`, `Séances`, `Employés`, `Factures`, `Ventes`, and `Utilisateurs`) are visible and accessible

#### Scenario: Employee navigation restriction
- **WHEN** an employee logs in
- **THEN** the `Accueil` (Dashboard), `Employés` (Workers/Payroll), and `Utilisateurs` (User Management) buttons are hidden and unmanaged, leaving only `Patients`, `Séances`, `Factures`, and `Ventes` visible

### Requirement: Default Landing Page Routing
The system SHALL automatically direct the user to an authorized landing view upon startup according to their role.

#### Scenario: Administrator landing page
- **WHEN** an administrator logs in
- **THEN** the application automatically loads the `Accueil` (Dashboard) view as the active screen

#### Scenario: Employee landing page
- **WHEN** an employee logs in
- **THEN** the application automatically loads the `Patients` view as the active screen

### Requirement: Session Indicator Display
The system SHALL display the authenticated user's full name and role badge in the sidebar directly above the logout button.

#### Scenario: Displaying user identity
- **WHEN** any user logs into the application
- **THEN** the sidebar displays their full name and formatted role badge (e.g., `Admin` or `Employé`)
