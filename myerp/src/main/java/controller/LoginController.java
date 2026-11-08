package controller;

import config.AppConfig;
import config.DatabaseConfig;
import config.OfflineSyncManager;
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

    private static final int OFFLINE_BUTTON_PROBE_THRESHOLD = 2;

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
    @FXML
    private Button btnWorkOffline;

    private ScheduledExecutorService discoveryExecutor;
    private int failedDiscoveryProbes = 0;

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
        if (lblNetworkStatus == null)
            return;
        lblNetworkStatus.setVisible(true);
        lblNetworkStatus.setManaged(true);

        if (AppConfig.isHost()) {
            lblNetworkStatus.setText("Poste Principal (Serveur local actif)");
            lblNetworkStatus.setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;");
        } else {
            // Reset to online attempt at login screen initialization
            DatabaseConfig.setOfflineMode(false);
            failedDiscoveryProbes = 0;

            lblNetworkStatus.setText("Recherche du poste principal sur le réseau WiFi...");
            lblNetworkStatus.setStyle("-fx-text-fill: #d97706; -fx-font-weight: bold;");
            loginButton.setDisable(true);
            setOfflineButtonVisible(false, "");

            discoveryExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ClientDiscovery-Thread");
                t.setDaemon(true);
                return t;
            });

            discoveryExecutor.scheduleWithFixedDelay(() -> {
                if (DatabaseConfig.isOfflineMode()) {
                    return;
                }
                LanDiscoveryClient.DiscoveryResult result = LanDiscoveryClient.discoverHost();
                boolean reachable = (result.success() && DatabaseConfig.testHostConnection())
                        || DatabaseConfig.testHostConnection();

                if (reachable) {
                    failedDiscoveryProbes = 0;
                    Platform.runLater(() -> {
                        setOfflineButtonVisible(false, "");
                        lblNetworkStatus.setText("Connecté au poste principal (" + AppConfig.getDbHost() + ")");
                        lblNetworkStatus.setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold;");
                        loginButton.setDisable(false);
                    });
                    if (discoveryExecutor != null) {
                        discoveryExecutor.shutdown();
                    }
                } else {
                    failedDiscoveryProbes++;
                    boolean hasReplica = OfflineSyncManager.hasInitializedReplica();
                    String lastSync = AppConfig.getReplicaLastSync();
                    Platform.runLater(() -> {
                        if (DatabaseConfig.isOfflineMode()) {
                            return;
                        }
                        if (failedDiscoveryProbes >= OFFLINE_BUTTON_PROBE_THRESHOLD) {
                            if (hasReplica) {
                                String syncNote = (lastSync != null && !lastSync.isBlank())
                                        ? " (Synchro : " + lastSync + ")"
                                        : "";
                                lblNetworkStatus.setText("Serveur absent — Mode hors-ligne disponible");
                                lblNetworkStatus.setStyle("-fx-text-fill: #ea580c; -fx-font-weight: bold;");
                                setOfflineButtonVisible(true, "Travailler hors-ligne" + syncNote);
                            } else {
                                lblNetworkStatus.setText("Serveur absent (1ère connexion au serveur requise)");
                                lblNetworkStatus.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold;");
                                setOfflineButtonVisible(false, "");
                            }
                        } else {
                            lblNetworkStatus.setText("En attente du poste du médecin (WiFi local)...");
                            lblNetworkStatus.setStyle("-fx-text-fill: #ea580c; -fx-font-weight: bold;");
                        }
                        loginButton.setDisable(true);
                    });
                }
            }, 0, 3, TimeUnit.SECONDS);
        }
    }

    private void setOfflineButtonVisible(boolean visible, String labelText) {
        if (btnWorkOffline != null) {
            if (labelText != null && !labelText.isEmpty()) {
                btnWorkOffline.setText(labelText);
            }
            btnWorkOffline.setVisible(visible);
            btnWorkOffline.setManaged(visible);
        }
    }

    @FXML
    private void handleWorkOffline() {
        if (!OfflineSyncManager.hasInitializedReplica()) {
            showAlert(Alert.AlertType.WARNING,
                    "Réplique locale non initialisée",
                    "Ce poste doit se connecter au serveur principal au moins une fois avant de pouvoir fonctionner hors-ligne.");
            return;
        }
        if (discoveryExecutor != null) {
            discoveryExecutor.shutdownNow();
        }
        DatabaseConfig.setOfflineMode(true);
        setOfflineButtonVisible(false, "");
        String lastSync = AppConfig.getReplicaLastSync();
        String syncSuffix = (lastSync != null && !lastSync.isBlank()) ? " — Synchro : " + lastSync : "";
        lblNetworkStatus.setText("Mode Hors-Ligne actif (Base locale" + syncSuffix + ")");
        lblNetworkStatus.setStyle("-fx-text-fill: #c2410c; -fx-font-weight: bold;");
        loginButton.setDisable(false);
        userNameField.requestFocus();
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
                discoveryExecutor.shutdownNow();
            }

            // If logging in online as a Client, synchronize with Host (flush any pending
            // offline work + refresh local replica)
            if (AppConfig.isClient() && !DatabaseConfig.isOfflineMode()) {
                Thread syncThread = new Thread(OfflineSyncManager::synchronizeWithHost, "InitialReplicaSync-Thread");
                syncThread.setDaemon(true);
                syncThread.start();
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
