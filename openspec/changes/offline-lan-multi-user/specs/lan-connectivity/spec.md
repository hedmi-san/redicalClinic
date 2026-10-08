# LAN Connectivity Specification

## ADDED Requirements

### Requirement: TCP Server Lifecycle on Host
The system SHALL start an embedded H2 TCP server with SSL and remote access enabled (`-tcpAllowOthers`, `-tcpSSL`) on port 9092 when running on a host workstation, and SHALL terminate the server cleanly upon application exit.

#### Scenario: Host starts TCP server on launch
- **WHEN** the application starts on a workstation configured with the `HOST` role
- **THEN** it initializes the embedded H2 TCP server on port 9092 bound to all local network interfaces and confirms readiness before showing the login view

#### Scenario: Host cleanly stops TCP server on exit
- **WHEN** the host application is closed or the user exits
- **THEN** active database connections are closed and the H2 TCP server is stopped without leaving dangling network sockets

### Requirement: Multi-Interface UDP Discovery
The system SHALL discover the host workstation IP address over the offline local WiFi LAN by sending directed UDP discovery beacons across all active non-loopback network interfaces without requiring manual user input.

#### Scenario: Client sends multi-interface UDP beacon
- **WHEN** a client workstation launches and initiates host discovery
- **THEN** it sends a discovery probe packet on UDP port 8888 to the directed broadcast address of every active non-loopback, non-virtual IPv4 interface

#### Scenario: Host responds to discovery beacon
- **WHEN** the host workstation receives a valid discovery probe on UDP port 8888
- **THEN** it replies immediately with its local IP address, service port, and application version string

#### Scenario: Client establishes connection from discovery
- **WHEN** the client receives the host's discovery response packet
- **THEN** it validates the application version, caches the host IP address in `~/clinic/config.properties`, and opens the JDBC connection to the host

### Requirement: Startup Discovery Waiting State
The system SHALL display an interactive non-blocking waiting indicator on the client login screen if the host workstation is not immediately detected, and SHALL retry discovery continuously until established or canceled.

#### Scenario: Host is temporarily unreachable at client launch
- **WHEN** the client starts and fails to locate the host within 2 seconds
- **THEN** the login screen displays a status message indicating waiting for the host computer with a periodic 3-second retry loop

#### Scenario: Host becomes available during client waiting
- **WHEN** the host connects to the clinic WiFi while the client is waiting
- **THEN** the client automatically discovers the host, resolves the connection status, and unlocks the login credentials form

### Requirement: Mid-Session Connection Loss Detection
The system SHALL monitor connection health to the host database during active usage and display a persistent alert banner across views when the connection is severed.

#### Scenario: Host closes lid or disconnects mid-session
- **WHEN** a network timeout or connection failure occurs during client operation
- **THEN** the application displays a top-level warning banner notifying the user that the main host is unreachable and disables data modification buttons until reconnected

#### Scenario: Automatic reconnection restores operations
- **WHEN** the host re-establishes network connectivity
- **THEN** the application clears the warning banner, refreshes active views, and re-enables interactive controls
