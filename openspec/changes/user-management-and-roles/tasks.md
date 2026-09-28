## 1. Database Safety & Data Normalization

- [x] 1.1 Implement automated pre-launch database backup in `DatabaseInitializer` creating timestamped copies in `~/clinic/backups/`
- [x] 1.2 Implement safe legacy role normalization query (`UPDATE Users SET userType = 'Admin' ...`) in `DatabaseInitializer`

## 2. Session Context & UserDAO Enhancements

- [x] 2.1 Create `util.UserSession` to hold the authenticated user and helper methods (`isAdmin()`, `isEmployee()`, `clear()`)
- [x] 2.2 Extend `UserDAO` with CRUD methods: `getAllUsers()`, `searchUsers()`, `addUser()`, `updateUser()`, `deleteUser()`, `isUsernameTaken()`, and `countAdmins()`
- [x] 2.3 Update `LoginController` to set `UserSession.setCurrentUser()` upon successful login

## 3. UI Navigation & Role-Based Access Control

- [x] 3.1 Update `home.fxml` to add the `btnUsers` navigation button and user profile indicator badge above the logout button
- [x] 3.2 Update `home.css` with styling for the user profile badge and role pill
- [x] 3.3 Update `HomeController` to hide restricted buttons (`btnAccueil`, `btnWorkers`, `btnUsers`) when logged in as `Employé`
- [x] 3.4 Update `HomeController` to dynamically route default landing view (`dashboard.fxml` for Admin, `patient.fxml` for Employee)
- [x] 3.5 Update `HomeController` to display the active user's name and role in the profile badge, and clear `UserSession` on logout

## 4. User Management Screen & Dialog

- [x] 4.1 Create `user.fxml` layout with search field, table view (Nom complet, Nom d'utilisateur, Rôle), and action buttons (Ajouter, Modifier, Supprimer)
- [x] 4.2 Create `user_form.fxml` modal dialog with fields for full name, username, password (with show/hide toggle), and role dropdown
- [x] 4.3 Implement `UserController` handling table population, real-time search, add/edit modal launch, self-deletion guard, and last-admin deletion guard
- [x] 4.4 Implement `UserFormController` handling validation (required fields, username uniqueness, password persistence on edit) and database persistence

## 5. Verification & Testing

- [x] 5.1 Build the Maven project to verify compilation with zero errors
- [x] 5.2 Validate database backup creation and role normalization against SQLite
- [x] 5.3 Verify navigation hiding, landing page routing, and user management CRUD operations
