## MODIFIED Requirements

### Requirement: Role-Based Navigation Visibility
The system SHALL dynamically configure sidebar navigation buttons according to the authenticated user's role, completely hiding restricted sections from employees while granting both administrators and employees full access to patient, appointment (`Rendez-vous`), session, billing, and sales workflows.

#### Scenario: Administrator navigation display
- **WHEN** an administrator logs in
- **THEN** all navigation buttons (`Accueil`, `Patients`, `Rendez-vous`, `Séances`, `Employés`, `Factures`, `Ventes`, and `Utilisateurs`, plus `Sauvegardes` on the Host workstation) are visible and accessible

#### Scenario: Employee navigation restriction
- **WHEN** an employee logs in
- **THEN** the `Accueil` (Dashboard), `Employés` (Workers/Payroll), `Utilisateurs` (User Management), and `Sauvegardes` buttons are hidden and unmanaged, leaving `Patients`, `Rendez-vous`, `Séances`, `Factures`, and `Ventes` visible and fully operational

