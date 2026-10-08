# Workstation Role Configuration Specification

## ADDED Requirements

### Requirement: First-Run Workstation Role Selection
The system SHALL display an explicit first-run modal dialog upon startup if no workstation role is configured in `~/clinic/config.properties`, prompting the user to declare whether the workstation is the Main Post or a Secondary Post.

#### Scenario: First launch on unconfigured workstation
- **WHEN** the application starts and discovers no `app.node_role` property in `~/clinic/config.properties`
- **THEN** it presents a modal window displaying options for "Poste Principal (Médecin / Serveur)" and "Poste Secondaire (Réception / Client)" before proceeding to login or database initialization

#### Scenario: Confirming workstation role
- **WHEN** the user selects a role and clicks the confirmation button
- **THEN** the selection is written to `~/clinic/config.properties` under `app.node_role` (`HOST` or `CLIENT`), the modal closes, and appropriate startup initialization begins

### Requirement: Split-Brain Database Prevention
The system SHALL prohibit a workstation configured as `CLIENT` from ever initializing or writing to a local database file, ensuring all client operations target the host server.

#### Scenario: Client launch without host database
- **WHEN** an application instance configured with role `CLIENT` is launched
- **THEN** it bypasses local database creation routines, does not create `~/clinic/clinic.mv.db`, and initiates network discovery to connect to the host

#### Scenario: Role modification protection
- **WHEN** a workstation role is saved in configuration
- **THEN** it cannot be overwritten by routine startup operations and requires explicit administrative action to reset
