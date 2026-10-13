# Repository Guidelines

## Project Structure

The Maven application lives in `myerp/`. Java 17 source is in `myerp/src/main/java/`, grouped into `controller`, `dao`, `model`, `service`, `config`, `network`, and `util` packages; `com/myerp` contains the JavaFX entry points. FXML views, CSS, and images are under `myerp/src/main/resources/`. Standalone verification programs are in `myerp/src/test/java/com/myerp/`. `openspec/` contains current and archived feature proposals, designs, tasks, and specifications; `docs/` has operational guides.

## Build and Run

Run Maven commands from `myerp/`:

- `mvn clean package` — compile and package the application JAR.
- `mvn javafx:run` — launch the JavaFX desktop application.
- `mvn test` — compile and run any Maven-discovered tests; the current verification classes are standalone `main` programs and are not a JUnit suite.

To run a verification program, use `mvn -Dexec.mainClass=com.myerp.UserManagementVerificationTest -Dexec.classpathScope=test exec:java` (replace the class name with `com.myerp.SalesProformaVerificationTest` as needed). These programs initialize and modify `~/clinic/clinic`; use a disposable home/data directory, never a real clinic database.

## Code and Resource Conventions

Follow the existing Java style: four-space indentation, `UpperCamelCase` class names, `lowerCamelCase` methods and fields, and descriptive package names. Keep database access in DAOs, business operations in services, and JavaFX event and view behavior in controllers. Keep FXML, CSS, and image assets in their matching resource directories; preserve existing FXML controller/resource paths when changing a view. No formatter or linter is configured in `pom.xml`.

## Tests and Changes

Name verification classes `*VerificationTest.java` and provide a `main` method with explicit assertions. Add focused checks for behavior changes, especially database safety, roles, LAN connectivity, and invoice output. Update or add OpenSpec artifacts in `openspec/changes/` when a change affects specified behavior; follow `openspec/config.yaml` and the existing proposal/design/tasks/spec format.

## Commits and Pull Requests

Recent commits use short, imperative summaries (for example, “Add user management, roles & DB safety”); keep commit subjects similarly direct and scoped. A pull request should explain the user-visible change, list validation performed, link the relevant issue or OpenSpec change, and include screenshots for UI changes. Call out database or configuration implications clearly.

## Configuration and Data Safety

The application stores its H2 database under `~/clinic/`. Do not commit database files, credentials, generated PDFs, or other local data. Treat schema and backup changes carefully, and verify database-related work against disposable data.
