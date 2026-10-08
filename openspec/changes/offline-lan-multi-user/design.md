## Context

Cabinet Nour El Islam is expanding operations from a single workstation to a two-workstation local area workflow:
- The **Doctor's Laptop (Host)** hosting 2 administrative profiles, full clinic oversight, accounting, and employee payroll.
- The **Receptionist's PC (Client)** running with an employee account for scheduling, intake, and treatment tracking.

The clinic operates in an isolated environment with a local WiFi router and **no active internet connection**. Data in the existing SQLite database (`~/clinic/clinic.db`) represents real clinical and financial history that must be preserved with zero data loss. The solution must run as an embedded pure-Java system without requiring third-party server software (e.g., MySQL, XAMPP) to be installed or administered on the doctor's machine.

## Goals / Non-Goals

**Goals:**
- **Zero External Software**: Embed the database server directly into the Java application runtime using H2 Database in TCP Server mode.
- **Zero-Friction Discovery**: Enable automatic behind-the-scenes discovery between the receptionist PC and the doctor's laptop over offline WiFi without requiring manual IP entry.
- **100% Data Preservation**: Seamlessly migrate existing SQLite tables, records, foreign keys, and sequences into H2 within a single atomic transaction and verify complete row parity before finalizing.
- **Split-Brain Prevention**: Introduce an explicit first-run workstation configuration screen to definitively designate the Host vs Client machine.
- **Connection Resilience**: Detect network drops, laptop sleep, or lid closure gracefully, displaying non-blocking reconnection alerts across the UI.
- **Zero-Config Firewall Integration**: Automatically inject Windows Defender Firewall rules for TCP 9092 and UDP 8888 during installer execution.

**Non-Goals:**
- Cloud synchronization or external internet-based multi-site replication.
- Server-side REST / microservice refactoring (retaining direct JDBC model while recognizing the client-side RBAC trade-off).
- Multi-receptionist concurrency beyond the 2-workstation clinic scope.

## Decisions

### 1. Embedded H2 Database over MySQL/MariaDB
- **Decision**: Adopt H2 Database 2.x in TCP Server mode (`-tcpAllowOthers`, `-tcpSSL`, `CIPHER=AES`).
- **Rationale**: Installing MySQL or MariaDB requires manual daemon setup, root password management, and Windows service maintenance that non-technical clinic staff cannot support. H2 runs completely in-process within the Java runtime.
- **Alternatives Considered**:
  - *Raw SQLite over Windows SMB Share*: Rejected due to high risk of `SQLITE_BUSY` file locking errors, latency, and database corruption during WiFi dropouts.
  - *Embedded HTTP REST server*: Rejected due to the excessive overhead of rewriting all existing JDBC DAOs into REST HTTP controllers.

### 2. Multi-Interface UDP Discovery with Local Cache
- **Decision**: Receptionist client broadcasts directed UDP packets to each active local IPv4 interface's broadcast address on port 8888, with automatic fallback to the last-known IP cached in `~/clinic/config.properties`.
- **Rationale**: A generic broadcast to `255.255.255.255` on Windows often only routes out a single adapter (failing if VPNs, virtual switches, or multiple network adapters exist). Querying `NetworkInterface.getNetworkInterfaces()` ensures transmission across the clinic WiFi subnet.
- **Alternatives Considered**:
  - *Static IP configuration*: Requires manual router intervention that fails when clinic equipment or routers are replaced.
  - *Manual IP entry dialog*: Violates the zero-configuration requirement.

### 3. First-Run Workstation Setup Modal
- **Decision**: Present an explicit first-run setup modal dialog on first launch when `~/clinic/config.properties` has no configured role, prompting the user to choose:
  - *Poste Principal (Médecin / Serveur)*
  - *Poste Secondaire (Réception / Client)*
- **Rationale**: Auto-detecting roles by inspecting database file presence is fragile (e.g., reinstallation on doctor's laptop would cause it to silently become a client; early launch on reception PC would spawn a rogue local database). Storing the selection permanently prevents split-brain scenarios.

### 4. Transactional SQLite-to-H2 Migration with Identity Resets
- **Decision**: Perform migration within a single H2 transaction (`setAutoCommit(false)`), copying table by table, resetting identity sequences, and performing a strict row count check (`COUNT(*)`) for every entity before committing.
- **Identity Reset**: Explicitly execute `ALTER TABLE <table> ALTER COLUMN id RESTART WITH (SELECT COALESCE(MAX(id), 0) + 1 FROM <table>)` on all tables to prevent primary key collisions on subsequent inserts.
- **Rollback Guarantee**: If any table count fails to match, the transaction rolls back, H2 files are purged, and the untouched SQLite database remains active.

### 5. Application-Wide Reconnection & Health Heartbeat
- **Decision**: Implement a centralized `ConnectionManager` that executes periodic health pings. When the host becomes unreachable (e.g., laptop sleep, WiFi glitch), a global non-blocking status banner is displayed across JavaFX views: *"Connexion avec le poste principal perdue. Tentative de reconnexion en cours..."*.

### 6. Security & Credential Protection
- **Decision**: Configure H2 with AES file encryption (`CIPHER=AES`) and TLS transport (`-tcpSSL`). Database credentials are encapsulated internally rather than using default `sa` credentials.
- **Security Boundary**: Acknowledge that in a direct JDBC architecture, business permission checks (`isAdmin()`, `isEmployee()`) remain enforced within the JavaFX UI layer. For a closed, physical clinic LAN, this trade-off is accepted against the complexity of building a full application server.

## Risks / Trade-offs

| Risk | Mitigation |
| :--- | :--- |
| **Windows Defender Firewall blocks ports 9092 or 8888** | `build-installer.ps1` runs `netsh advfirewall` commands during MSI installation to whitelist the binary for private networks; setup documentation includes verification steps. |
| **WiFi Router AP Isolation enabled** | Clinic router may block device-to-device traffic. Documentation provides explicit instructions for turning off "Client Isolation" on the clinic router. |
| **Doctor laptop lid closes mid-session** | Global reconnect banner alerts the receptionist; operations retry cleanly upon wake without crashing the application. |
| **H2 SQL dialect divergence (`strftime`, `AUTOINCREMENT`)** | Update DAO queries to ANSI / H2 standard (`FORMATDATETIME`, `EXTRACT(MONTH FROM ...)`), and test all 10 DAOs. |
| **Host hardware failure (Single Point of Failure)** | Integrate automated daily timestamped backups (`BACKUP TO '...'`) stored in `~/clinic/backups/`, with manual export to external USB. |

## Migration Plan

1. **Pre-Migration Safety**: Create a verified backup copy of `~/clinic/clinic.db` to `~/clinic/backups/clinic_pre_migration.db`.
2. **Schema Creation**: Initialize encrypted H2 schema with identical constraints, foreign keys, and indexes.
3. **Data Ingestion**: Stream records from SQLite into H2 within a single transaction across all tables (`Users`, `worker`, `patient`, `therapyPlan`, `session`, `bill`, `billItem`, `paymentCheck`, `sold`).
4. **Sequence Realignment**: Execute `ALTER TABLE ... RESTART WITH` for all auto-increment primary keys.
5. **Validation**: Execute row-count comparisons across all 9 tables. If identical, set `migration.completed=true` in `config.properties`. If disparate, roll back and report error.
6. **Rollback Strategy**: If migration encounters errors, the application reverts to local SQLite mode instantly without loss.
