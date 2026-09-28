package controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import util.UserSession;

import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

public class HomeController implements Initializable {

    @FXML
    private StackPane contentArea;
    @FXML
    private HBox titleBar;

    @FXML
    private Button btnAccueil;
    @FXML
    private Button btnPatients;
    @FXML
    private Button btnSessions;
    @FXML
    private Button btnWorkers;
    @FXML
    private Button btnBills;
    @FXML
    private Button btnSolds;
    @FXML
    private Button btnUsers;

    @FXML
    private Label lblUserFullName;
    @FXML
    private Label lblUserRole;
    @FXML
    private Label lblUserInitial;
    @FXML
    private Button btnLogout;

    private Button activeButton;
    private double x = 0;
    private double y = 0;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Setup window dragging on the title bar
        if (titleBar != null) {
            titleBar.setOnMousePressed((MouseEvent event) -> {
                x = event.getSceneX();
                y = event.getSceneY();
            });
            titleBar.setOnMouseDragged((MouseEvent event) -> {
                Stage stage = (Stage) titleBar.getScene().getWindow();
                stage.setX(event.getScreenX() - x);
                stage.setY(event.getScreenY() - y);
            });
        }

        // Configure role-based UI access and route to initial authorized page
        setupRoleBasedAccess();
    }

    private void setupRoleBasedAccess() {
        // Populate profile indicator badge
        if (UserSession.isLoggedIn()) {
            if (lblUserFullName != null) {
                lblUserFullName.setText(UserSession.getCurrentUser().getFullName());
            }
            if (lblUserRole != null) {
                if (UserSession.isAdmin()) {
                    lblUserRole.setText("Compte admin");
                } else if (UserSession.isEmployee()) {
                    lblUserRole.setText("Employé");
                } else {
                    lblUserRole.setText(UserSession.getDisplayRole());
                }
            }
            if (lblUserInitial != null) {
                String name = UserSession.getCurrentUser().getFullName();
                lblUserInitial.setText(name != null && !name.trim().isEmpty() ? name.trim().substring(0, 1).toUpperCase() : "A");
            }
        } else {
            if (lblUserFullName != null) lblUserFullName.setText("Administrateur");
            if (lblUserRole != null) lblUserRole.setText("Compte admin");
            if (lblUserInitial != null) lblUserInitial.setText("A");
        }

        if (UserSession.isEmployee()) {
            // Hide restricted buttons from employee
            if (btnAccueil != null) {
                btnAccueil.setVisible(false);
                btnAccueil.setManaged(false);
            }
            if (btnWorkers != null) {
                btnWorkers.setVisible(false);
                btnWorkers.setManaged(false);
            }
            if (btnUsers != null) {
                btnUsers.setVisible(false);
                btnUsers.setManaged(false);
            }

            // Default landing page for employees is Patients
            handleNavPatients(null);
        } else {
            // Default landing page for Admin is Accueil (Dashboard)
            handleNavAccueil(null);
        }
    }

    @FXML
    private void handleMinimize(ActionEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setIconified(true);
    }

    @FXML
    private void handleMaximize(ActionEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setMaximized(!stage.isMaximized());
    }

    @FXML
    private void handleClose(ActionEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
    }

    @FXML
    private void handleNavAccueil(ActionEvent event) {
        if (UserSession.isEmployee()) {
            return;
        }
        loadPage("/fxml/pages/dashboard.fxml");
        setActiveButton(btnAccueil);
    }

    @FXML
    private void handleNavPatients(ActionEvent event) {
        loadPage("/fxml/pages/patient.fxml");
        setActiveButton(btnPatients);
    }

    @FXML
    private void handleNavSessions(ActionEvent event) {
        loadPage("/fxml/pages/session.fxml");
        setActiveButton(btnSessions);
    }

    @FXML
    private void handleNavWorkers(ActionEvent event) {
        if (UserSession.isEmployee()) {
            return;
        }
        loadPage("/fxml/pages/worker.fxml");
        setActiveButton(btnWorkers);
    }

    @FXML
    private void handleNavBills(ActionEvent event) {
        loadPage("/fxml/pages/bill.fxml");
        setActiveButton(btnBills);
    }

    @FXML
    private void handleNavSolds(ActionEvent event){
        loadPage("/fxml/pages/sold.fxml");
        setActiveButton(btnSolds);
    }

    @FXML
    private void handleNavUsers(ActionEvent event) {
        if (!UserSession.isAdmin()) {
            return;
        }
        loadPage("/fxml/pages/user.fxml");
        setActiveButton(btnUsers);
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Déconnexion");
        alert.setHeaderText("Confirmer la déconnexion");
        alert.setContentText("Êtes-vous sûr de vouloir vous déconnecter ?");
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        try {
            // Clear session context
            UserSession.clear();

            // Close the current dashboard window
            Node source = (Node) event.getSource();
            Stage currentStage = (Stage) source.getScene().getWindow();
            currentStage.close();

            // Open the Login window
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/login.fxml"));
            Stage loginStage = new Stage();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

            // Allow dragging the window (if using transparent/undecorated)
            root.setOnMousePressed((MouseEvent mouseEvent) -> {
                x = mouseEvent.getSceneX();
                y = mouseEvent.getSceneY();
            });
            root.setOnMouseDragged((MouseEvent mouseEvent) -> {
                loginStage.setX(mouseEvent.getScreenX() - x);
                loginStage.setY(mouseEvent.getScreenY() - y);
            });

            loginStage.initStyle(StageStyle.TRANSPARENT);
            loginStage.setScene(scene);
            loginStage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Loads an FXML file into the central content area.
     */
    private void loadPage(String fxmlPath) {
        try {
            Parent page = FXMLLoader.load(getClass().getResource(fxmlPath));
            contentArea.getChildren().clear();
            contentArea.getChildren().add(page);
        } catch (IOException e) {
            System.err.println("Could not load page: " + fxmlPath);
            e.printStackTrace();
        }
    }

    /**
     * Updates the CSS class to visually indicate which menu item is active.
     */
    private void setActiveButton(Button button) {
        if (activeButton != null) {
            activeButton.getStyleClass().remove("nav-btn-active");
        }
        activeButton = button;
        if (activeButton != null) {
            activeButton.getStyleClass().add("nav-btn-active");
        }
    }
}
