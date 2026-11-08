## MODIFIED Requirements

### Requirement: Startup Discovery Waiting State
The system SHALL display an interactive non-blocking waiting indicator on the client login screen if the host workstation is not immediately detected, SHALL retry discovery continuously, and SHALL reveal an explicit "Travailler hors-ligne" action after a short discovery timeout (~6 seconds) when an initialized local replica is available.

#### Scenario: Host is temporarily unreachable at client launch
- **WHEN** the client starts and fails to locate the host within the first discovery attempt
- **THEN** the login screen displays a status message indicating waiting for the host computer with a periodic 3-second retry loop

#### Scenario: Explicit offline login option after discovery timeout
- **WHEN** the client fails to locate the host after 2 consecutive discovery probes (~6 seconds) and a synchronized local replica exists on the workstation
- **THEN** the login screen displays a "Travailler hors-ligne" button showing the last synchronization timestamp; clicking it switches the client to offline replica mode and unlocks the login credentials form

#### Scenario: Offline login blocked when no local replica exists
- **WHEN** a brand-new client workstation that has never synchronized with the host fails to locate the host at startup
- **THEN** the login screen keeps offline mode unavailable and informs the user that an initial connection to the main server is required

#### Scenario: Host becomes available during client waiting
- **WHEN** the host connects to the clinic WiFi while the client is waiting on the login screen before the user enters offline mode
- **THEN** the client automatically discovers the host, hides the offline fallback prompt, resolves the connection status, and unlocks the login credentials form against the host

### Requirement: Mid-Session Connection Loss Detection
The system SHALL monitor connection health to the host database during active client usage, automatically switch to the local offline replica after 3 consecutive failed heartbeat probes (~12 seconds) with an orange offline status banner, and automatically synchronize and restore online operations when the host returns.

#### Scenario: Brief Wi-Fi blip does not trigger offline mode
- **WHEN** 1 or 2 consecutive heartbeat probes fail on a client workstation followed by a successful probe
- **THEN** the client remains in online mode without switching to the local replica or displaying the offline banner

#### Scenario: Host disconnects for 3 consecutive probes mid-session
- **WHEN** 3 consecutive heartbeat probes (~12 seconds) fail during active client operation and a local replica is available
- **THEN** the application automatically switches database routing to the local replica, displays a top-level orange banner indicating that the workstation is working in offline mode with local saving, and allows the user to continue working normally

#### Scenario: Automatic host re-detection triggers flush-first synchronization
- **WHEN** the background connection monitor detects 2 consecutive successful probes to the host while the client is in offline mode
- **THEN** the application automatically flushes all pending offline changes to the host, refreshes the local replica from the host, switches database routing back to the host TCP pool, clears the offline banner, refreshes active views, and displays any conflict report to the user

