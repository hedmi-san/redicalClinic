## Why

The clinic currently operates as a single-PC standalone application using local SQLite storage. The clinic needs to expand to a two-computer workflow:
1. The **Doctor's Laptop** hosting administrative capabilities (two doctor/admin accounts) with full access to financial metrics, payroll, and clinic management.
2. The **Receptionist's PC** operating with an employee account for patient intake, scheduling, and session tracking.

Both machines are connected solely via a local offline WiFi LAN without internet access. The transition must ensure **zero data loss** of historical patient, session, and financial records, eliminate manual network configuration for the receptionist, and prevent split-brain database divergence.

## What Changes

- **Embedded Pure-Java Database Server**: Migrate from single-process SQLite to embedded H2 Database running in TCP Server mode (`-tcpAllowOthers`, `-tcpSSL`, `CIPHER=AES`). Zero external third-party software (such as MySQL or XAMPP) required on the doctor's machine.
- **Transactional SQLite-to-H2 Migration**: Automatically migrate existing `clinic.db` data into H2 inside a single transaction with strict type mapping, full row-count parity verification, sequence resets (`RESTART WITH max(id) + 1`), and retention of the original SQLite database as an untouched master backup.
- **First-Run Workstation Setup**: Introduce a first-run setup modal dialog prompting the user to declare whether the workstation is the **Main Post (Doctor / Server)** or **Secondary Post (Reception / Client)**, persisting this setting in `~/clinic/config.properties` to eliminate split-brain database creation.
- **Zero-Config LAN Auto-Discovery**: Implement multi-adapter directed UDP beacon broadcast and listener to automatically discover the host IP address behind the scenes, eliminating manual IP address input on the receptionist's PC.
- **Connection Resilience & Graceful Disconnection Handling**: Provide connection retry loops, startup waiting indicators, and mid-session disconnection banners when the doctor's laptop is unreachable, closed, or asleep.
- **Firewall Integration & Automated Backups**: Configure Windows Defender Firewall rules for TCP port 9092 and UDP discovery port 8888 via installer packaging, along with automated daily compressed database backups.

## Capabilities

### New Capabilities
- `lan-connectivity`: Zero-configuration multi-interface UDP beacon discovery, TCP server lifecycle management, and connection loss/reconnect handling across the local offline WiFi LAN.
- `workstation-role-configuration`: Explicit first-launch workstation role selection modal and persistent configuration management to reliably distinguish Host and Client nodes.

### Modified Capabilities
- `database-safety`: Upgrades existing single-file SQLite backup and initialization specifications to support transactional cross-engine migration, identity sequence resets, row-count parity validation, and encrypted H2 database operation.

## Impact

- **Dependencies**: Adds H2 Database engine (`com.h2database:h2`) to `myerp/pom.xml`. SQLite JDBC remains present for migration import.
- **Database Configuration & DAOs**: `config/DatabaseConfig.java` refactored to support host server bootstrap vs remote client connection; SQL statements updated to ANSI/H2 syntax (replacing SQLite-specific `strftime` with standard date functions and auto-increment identity syntax).
- **Installer & Packaging**: `build-installer.ps1` updated to register Windows Defender Firewall exceptions for TCP (9092) and UDP (8888).
- **Network & Storage**: Configuration saved in `~/clinic/config.properties`, database stored in `~/clinic/clinic.mv.db`, and automated backups stored in `~/clinic/backups/`.
