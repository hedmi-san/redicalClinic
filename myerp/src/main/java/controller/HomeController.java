package controller;

import config.AppConfig;
import config.DatabaseConfig;
import config.OfflineSyncManager;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import javafx.util.Duration;
import network.ConnectionMonitor;
import util.UserSession;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

public class HomeController implements Initializable {

    private static final double SIDEBAR_EXPANDED_WIDTH = 250.0;
    private static final double SIDEBAR_COLLAPSED_WIDTH = 68.0;
    private static final String ICON_COLLAPSE = "M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z";
    private static final String ICON_EXPAND = "M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z";

    @FXML
    private StackPane contentArea;
    @FXML
    private HBox titleBar;

    @FXML
    private VBox sidebar;
    @FXML
    private HBox sidebarToggleContainer;
    @FXML
    private Button btnToggleSidebar;
    @FXML
    private SVGPath toggleSidebarIcon;
    @FXML
    private VBox sidebarBrand;
    @FXML
    private Label lblBrandTop;
    @FXML
    private Label lblBrandBottom;
    @FXML
    private HBox userProfileCard;
    @FXML
    private VBox userInfoBox;

    @FXML
    private Button btnAccueil;
    @FXML
    private Button btnPatients;
    @FXML
    private Button btnRendezVous;
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
    private Button btnBackups;

    @FXML
    private Label lblUserFullName;
    @FXML
    private Label lblUserRole;
    @FXML
    private Label lblUserInitial;
    @FXML
    private Button btnLogout;

    @FXML
    private HBox connectionAlertBanner;
    @FXML
    private Label lblConnectionAlert;

    private final Map<String, Parent> pageCache = new HashMap<>();
    private final Map<String, Object> controllerCache = new HashMap<>();

    private Button activeButton;
    private boolean sidebarCollapsed = false;
    private double x = 0;
    private double y = 0;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
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

        if (btnToggleSidebar != null) {
            btnToggleSidebar.setTooltip(new Tooltip("Réduire le menu"));
        }

        setupRoleBasedAccess();

        if (AppConfig.isClient()) {
            updateOfflineBannerState(!DatabaseConfig.isOfflineMode());
            ConnectionMonitor.start(
                    this::updateOfflineBannerState,
                    this::handleOfflineSyncCompleted);
        }
    }

    private void setupRoleBasedAccess() {
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
                lblUserInitial.setText(
                        name != null && !name.trim().isEmpty() ? name.trim().substring(0, 1).toUpperCase() : "A");
            }
        } else {
            if (lblUserFullName != null) {
                lblUserFullName.setText("Administrateur");
            }
            if (lblUserRole != null) {
                lblUserRole.setText("Compte admin");
            }
            if (lblUserInitial != null) {
                lblUserInitial.setText("A");
            }
        }

        if (btnBackups != null) {
            boolean showBackups = UserSession.isAdmin() && AppConfig.isHost();
            btnBackups.setVisible(showBackups);
            btnBackups.setManaged(showBackups);
        }

        if (UserSession.isEmployee()) {
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
            handleNavPatients(null);
        } else {
            handleNavAccueil(null);
        }
    }

    private void updateOfflineBannerState(boolean connected) {
        if (connectionAlertBanner != null) {
            if (!connected && lblConnectionAlert != null) {
                lblConnectionAlert.setText(
                        "Mode Hors-Ligne — Modifications sauvegardées localement");
            }
            connectionAlertBanner.setVisible(!connected);
            connectionAlertBanner.setManaged(!connected);
        }
    }

    private void handleOfflineSyncCompleted(OfflineSyncManager.SyncResult syncResult) {
        for (Object ctrl : controllerCache.values()) {
            if (ctrl instanceof Refreshable refreshable) {
                try {
                    refreshable.refresh();
                } catch (Exception ignored) {
                }
            }
        }

        if (syncResult != null && syncResult.conflicts() != null && !syncResult.conflicts().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("La connexion au poste principal est rétablie et ");
            sb.append(syncResult.flushedCount());
            sb.append(" modification(s) hors-ligne ont été synchronisées.\n\n");
            sb.append("Les éléments suivants ont été modifiés simultanément sur le poste principal (la version du serveur a été conservée) :\n\n");
            for (OfflineSyncManager.SyncConflict conflict : syncResult.conflicts()) {
                sb.append("• ");
                sb.append(conflict.description());
                sb.append("\n");
            }

            Alert conflictAlert = new Alert(Alert.AlertType.WARNING);
            conflictAlert.setTitle("Synchronisation terminée avec conflits résolus");
            conflictAlert.setHeaderText("Conflits de modification détectés (Priorité Serveur)");
            conflictAlert.setContentText(sb.toString());
            conflictAlert.showAndWait();
        }
    }

    // Loads an FXML file into the central content area with view caching and
    // instant tab switching. Keeps cached pages mounted in the Scene graph and toggles
    // visibility to avoid expensive CSS StyleManager and Control Skin teardown/rebuild.
    private void loadPage(String fxmlPath) {
        try {
            Parent page = pageCache.get(fxmlPath);
            Object controller = controllerCache.get(fxmlPath);

            if (page == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
                page = loader.load();
                controller = loader.getController();
                pageCache.put(fxmlPath, page);
                controllerCache.put(fxmlPath, controller);
                contentArea.getChildren().add(page);
            } else if (controller instanceof Refreshable refreshable) {
                refreshable.refresh();
            }

            for (Node child : contentArea.getChildren()) {
                boolean isTarget = (child == page);
                if (child.isVisible() != isTarget) {
                    child.setVisible(isTarget);
                }
                if (child.isManaged() != isTarget) {
                    child.setManaged(isTarget);
                }
            }
        } catch (IOException e) {
            System.err.println("Could not load page: " + fxmlPath);
            e.printStackTrace();
        }
    }

    private void setActiveButton(Button button) {
        if (activeButton != null) {
            activeButton.getStyleClass().remove("nav-btn-active");
        }
        activeButton = button;
        if (activeButton != null) {
            activeButton.getStyleClass().add("nav-btn-active");
        }
    }

    @FXML
    private void handleToggleSidebar(ActionEvent event) {
        if (sidebar == null) {
            return;
        }
        sidebarCollapsed = !sidebarCollapsed;
        double targetWidth = sidebarCollapsed ? SIDEBAR_COLLAPSED_WIDTH : SIDEBAR_EXPANDED_WIDTH;

        if (sidebarCollapsed) {
            if (!sidebar.getStyleClass().contains("sidebar-collapsed")) {
                sidebar.getStyleClass().add("sidebar-collapsed");
            }
            applySidebarContentState(true);
        } else {
            sidebar.getStyleClass().remove("sidebar-collapsed");
        }

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.millis(180),
                        new KeyValue(sidebar.minWidthProperty(), targetWidth, Interpolator.EASE_BOTH),
                        new KeyValue(sidebar.prefWidthProperty(), targetWidth, Interpolator.EASE_BOTH),
                        new KeyValue(sidebar.maxWidthProperty(), targetWidth, Interpolator.EASE_BOTH)));
        timeline.setOnFinished(e -> {
            if (!sidebarCollapsed) {
                applySidebarContentState(false);
            }
        });
        timeline.play();
    }

    private void applySidebarContentState(boolean collapsed) {
        if (sidebarToggleContainer != null) {
            sidebarToggleContainer.setAlignment(collapsed ? Pos.CENTER : Pos.CENTER_RIGHT);
        }
        if (toggleSidebarIcon != null) {
            toggleSidebarIcon.setContent(collapsed ? ICON_EXPAND : ICON_COLLAPSE);
        }
        if (btnToggleSidebar != null) {
            btnToggleSidebar.setTooltip(new Tooltip(collapsed ? "Déplier le menu" : "Réduire le menu"));
        }

        if (lblBrandTop != null) {
            lblBrandTop.setVisible(!collapsed);
            lblBrandTop.setManaged(!collapsed);
        }
        if (lblBrandBottom != null) {
            lblBrandBottom.setVisible(!collapsed);
            lblBrandBottom.setManaged(!collapsed);
        }

        List<Button> navButtons = List.of(
                btnAccueil, btnPatients, btnRendezVous, btnSessions,
                btnWorkers, btnBills, btnSolds, btnUsers, btnBackups, btnLogout);
        for (Button btn : navButtons) {
            if (btn == null) {
                continue;
            }
            btn.setContentDisplay(collapsed ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.LEFT);
            btn.setAlignment(collapsed ? Pos.CENTER : Pos.BASELINE_LEFT);
            btn.setTooltip(collapsed ? new Tooltip(btn.getText()) : null);
        }

        if (userInfoBox != null) {
            userInfoBox.setVisible(!collapsed);
            userInfoBox.setManaged(!collapsed);
        }
        if (userProfileCard != null) {
            userProfileCard.setAlignment(collapsed ? Pos.CENTER : Pos.CENTER_LEFT);
            if (collapsed && lblUserFullName != null && lblUserRole != null) {
                Tooltip.install(userProfileCard,
                        new Tooltip(lblUserFullName.getText() + " (" + lblUserRole.getText() + ")"));
            } else {
                Tooltip.uninstall(userProfileCard, null);
            }
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
        ConnectionMonitor.stop();
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
    private void handleNavRendezVous(ActionEvent event) {
        loadPage("/fxml/pages/rendez_vous.fxml");
        setActiveButton(btnRendezVous);
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
    private void handleNavSolds(ActionEvent event) {
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
    private void handleNavBackups(ActionEvent event) {
        if (!UserSession.isAdmin() || !AppConfig.isHost()) {
            return;
        }
        loadPage("/fxml/pages/backup.fxml");
        setActiveButton(btnBackups);
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
            ConnectionMonitor.stop();
            pageCache.clear();
            controllerCache.clear();
            UserSession.clear();

            Node source = (Node) event.getSource();
            Stage currentStage = (Stage) source.getScene().getWindow();
            currentStage.close();

            Parent root = FXMLLoader.load(getClass().getResource("/fxml/login.fxml"));
            Stage loginStage = new Stage();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

            loginStage.setTitle("Cabinet Nour El Islam");
            loginStage.setResizable(false);
            loginStage.setScene(scene);
            loginStage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
