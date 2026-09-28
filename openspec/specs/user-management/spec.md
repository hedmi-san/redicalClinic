# User Management Specification

## Purpose
Enables administrators to create, list, filter, update, and safely remove user accounts while preventing lockout scenarios.

## Requirements

### Requirement: List and Search Users
The system SHALL provide a user administration interface where administrators can view all registered system users in a table and search or filter them in real-time by full name or username.

#### Scenario: Listing users
- **WHEN** an administrator navigates to the user management view
- **THEN** the system displays all registered users showing their full name, username, and role, while hiding plain-text passwords

#### Scenario: Searching users by keyword
- **WHEN** an administrator enters a search string into the user search field
- **THEN** the user list updates dynamically to show only users whose full name or username contains the search term

### Requirement: Create New User
The system SHALL allow an administrator to create a new user by specifying full name, username, password, and selecting a role (`Admin` or `Employé`).

#### Scenario: Successful user creation
- **WHEN** the administrator fills in valid details with a unique username and clicks save
- **THEN** the new user is saved to the database and appears in the user table

#### Scenario: Duplicate username rejection
- **WHEN** the administrator attempts to save a new user with a username that already exists in the database
- **THEN** the system rejects the creation and displays a validation error message indicating the username is already taken

#### Scenario: Missing required fields
- **WHEN** the administrator submits the form with an empty full name, username, or password
- **THEN** the system prevents submission and alerts the administrator to fill in all required fields

### Requirement: Edit Existing User
The system SHALL allow an administrator to modify an existing user's details, including full name, username, password, and role.

#### Scenario: Successful user edit
- **WHEN** the administrator selects a user, edits their information, and submits the form
- **THEN** the user record is updated in the database and the table reflects the updated data

#### Scenario: Preserving password on edit
- **WHEN** the administrator edits an existing user and leaves the password field empty
- **THEN** the system preserves the existing password without overwriting it

### Requirement: Safe Deletion and Lockout Prevention
The system SHALL allow administrators to delete user accounts while strictly preventing the deletion of the currently logged-in administrator and the last remaining administrator in the system.

#### Scenario: Successful employee deletion
- **WHEN** an administrator confirms deletion of an employee account
- **THEN** the selected account is removed from the database and disappears from the user table

#### Scenario: Preventing self-deletion
- **WHEN** an administrator attempts to delete their own currently active user account
- **THEN** the system blocks the action and displays a warning that an administrator cannot delete their own account

#### Scenario: Preventing last administrator deletion
- **WHEN** an administrator attempts to delete the only remaining administrator account in the system
- **THEN** the system blocks the action and informs the user that at least one administrator account must exist

#### Scenario: Preventing last administrator demotion
- **WHEN** an administrator attempts to change the role of the only remaining administrator account to employee
- **THEN** the system blocks the update and displays a warning that the system must retain at least one administrator
