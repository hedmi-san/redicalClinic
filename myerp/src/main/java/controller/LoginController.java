package controller;

import config.AppConfig;
import config.DatabaseConfig;
import dao.UserDAO;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import model.User;
import network.LanDiscoveryClient;
import util.UserSession;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LoginController {

    @FXML
    private TextField userNameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private TextField passwordTextField;
    @FXML
    private Button togglePasswordBtn;
    @FXML
    private Button loginButton;
    @FXML
    private Label lblNetworkStatus;

    private ScheduledExecutorService discoveryExecutor;

    private double x = 0;
    private double y = 0;

    @FXML
    public void initialize() {
        // Sync text between the hidden TextField and the PasswordField
        if (passwordField != null && passwordTextField != null) {
            passwordTextField.textProperty().bindBidirectional(passwordField.textProperty());
        }

        setupNetworkState();
    }

    private void setupNetworkState() {
        if (lblNetworkStatus == null) return;
        lblNetworkStatus.setVisible(true);
        lblNetworkStatus.setManaged(true);

        if (AppConfig.isHost()) {
            lblNetworkStatus.setText("🟢 Poste Principal (Serveur local actif)");
            lblNetworkStatus.setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;");
        } else {
            // Client mode: check connection and discover if needed
            lblNetworkStatus.setText("⏳ Recherche du poste principal sur le réseau WiFi...");
            lblNetworkStatus.setStyle("-fx-text-fill: #d97706; -fx-font-weight: bold;");
            loginButton.setDisable(true);

            discoveryExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ClientDiscovery-Thread");
                t.setDaemon(true);
                return t;
            });

            discoveryExecutor.scheduleWithFixedDelay(() -> {
                LanDiscoveryClient.DiscoveryResult result = LanDiscoveryClient.discoverHost();
                if (result.success() && DatabaseConfig.testConnection()) {
                    Platform.runLater(() -> {
                        lblNetworkStatus.setText("🟢 Connecté au poste principal (" + AppConfig.getDbHost() + ")");
                        lblNetworkStatus.setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;");
                        loginButton.setDisable(false);
                    });
                    if (discoveryExecutor != null) {
                        discoveryExecutor.shutdown();
                    }
                } else {
                    Platform.runLater(() -> {
                        lblNetworkStatus.setText("⏳ En attente du poste du médecin (WiFi local)...");
                        lblNetworkStatus.setStyle("-fx-text-fill: #ea580c; -fx-font-weight: bold;");
                        loginButton.setDisable(true);
                    });
                }
            }, 0, 3, TimeUnit.SECONDS);
        }
    }

    @FXML
    private void togglePasswordVisibility() {
        if (passwordTextField.isVisible()) {
            // Hide text, show dots
            passwordTextField.setVisible(false);
            passwordTextField.setManaged(false);
            passwordField.setVisible(true);
            passwordField.setManaged(true);
        } else {
            // Show text, hide dots
            passwordTextField.setVisible(true);
            passwordTextField.setManaged(true);
            passwordField.setVisible(false);
            passwordField.setManaged(false);
        }
    }

    @FXML
    private void handleLogin() {
        String userName = userNameField.getText().trim();
        String password = passwordField.getText().trim();

        if (userName.isEmpty() || password.isEmpty()) {
            showAlert(Alert.AlertType.WARNING,
                    "Champs manquants",
                    "Veuillez remplir le nom d'utilisateur et le mot de passe.");
            return;
        }

        User user = UserDAO.login(userName, password);

        if (user != null) {
            if (discoveryExecutor != null) {
                discoveryExecutor.shutdown();
            }
            UserSession.setCurrentUser(user);
            Stage loginStage = (Stage) loginButton.getScene().getWindow();
            try {
                Parent root = FXMLLoader.load(getClass().getResource("/fxml/home.fxml"));
                Stage stage = new Stage();
                Scene scene = new Scene(root);
                scene.getStylesheets().add(
                        getClass().getResource("/css/home.css").toExternalForm());
                root.setOnMousePressed((MouseEvent event) -> {
                    x = event.getSceneX();
                    y = event.getSceneY();
                });

                root.setOnMouseDragged((MouseEvent event) -> {
                    stage.setX(event.getScreenX() - x);
                    stage.setY(event.getScreenY() - y);
                });

                stage.initStyle(StageStyle.TRANSPARENT);
                stage.setTitle("Cabinet Nour El Islam");
                stage.setResizable(false);
                stage.setScene(scene);
                stage.show();
                loginStage.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        } else {
            showAlert(Alert.AlertType.ERROR,
                    "Échec de la connexion",
                    "Nom d'utilisateur ou mot de passe incorrect.");
            passwordField.clear();
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
