## MODIFIED Requirements

### Requirement: Create New User
The system SHALL allow an administrator to create a new user by specifying full name, username, password, and selecting a role (`Admin` or `Employé`), storing the password exclusively as a salted cryptographic hash.

#### Scenario: Successful user creation
- **WHEN** the administrator fills in valid details with a unique username and clicks save
- **THEN** the new user is saved to the database with a salted `PBKDF2WithHmacSHA256` password hash and appears in the user table

#### Scenario: Duplicate username rejection
- **WHEN** the administrator attempts to save a new user with a username that already exists in the database
- **THEN** the system rejects the creation and displays a validation error message indicating the username is already taken

#### Scenario: Missing required fields
- **WHEN** the administrator submits the form with an empty full name, username, or password
- **THEN** the system prevents submission and alerts the administrator to fill in all required fields

### Requirement: Edit Existing User
The system SHALL allow an administrator to modify an existing user's details, including full name, username, password, and role, hashing any newly provided password with a fresh cryptographic salt.

#### Scenario: Successful user edit
- **WHEN** the administrator selects a user, edits their information, and submits the form
- **THEN** the user record is updated in the database (with a salted hash if a new password was provided) and the table reflects the updated data

#### Scenario: Preserving password on edit
- **WHEN** the administrator edits an existing user and leaves the password field empty
- **THEN** the system preserves the existing salted password hash without overwriting it

## ADDED Requirements

### Requirement: Salted Password Hashing and Legacy Credential Migration
The system SHALL store all user passwords in `Users.passWord` as salted `PBKDF2WithHmacSHA256` hashes and SHALL transparently migrate any legacy plaintext passwords in-place during database initialization or user login so that client database replicas never contain plaintext passwords.

#### Scenario: Startup migration of plaintext passwords
- **WHEN** `DatabaseInitializer.initializeDatabase()` runs on a database containing user rows whose `passWord` column is not yet in salted PBKDF2 format
- **THEN** the system hashes each plaintext password with a unique random 16-byte cryptographic salt and updates the `Users` table before any client replica synchronization occurs

#### Scenario: Authenticating against salted password hash online and offline
- **WHEN** a user submits their username and password on the login screen in either online mode or offline replica mode
- **THEN** `UserDAO.login` verifies the submitted password against the stored salted PBKDF2 hash in constant time and grants access only if the hash matches

