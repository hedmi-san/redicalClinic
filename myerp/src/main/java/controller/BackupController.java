package controller;

import config.AppConfig;
import config.BackupManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import network.ConnectionMonitor;
import util.UserSession;

import java.io.File;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class BackupController implements Initializable, Refreshable {

    @FXML
    private VBox mainContainer;
    @FXML
    private Label lblHostBadge;
    @FXML
    private Button btnExportCni;
    @FXML
    private Button btnRestoreImport;
    @FXML
    private HBox statusBox;
    @FXML
    private Label lblStatusMessage;
    @FXML
    private Label lblBackupDirectory;
    @FXML
    private Button btnRefreshBackups;
    @FXML
    private Button btnCopyUsb;
    @FXML
    private TableView<BackupManager.LocalBackupItem> backupTable;
    @FXML
    private TableColumn<BackupManager.LocalBackupItem, String> colFileName;
    @FXML
    private TableColumn<BackupManager.LocalBackupItem, String> colDate;
    @FXML
    private TableColumn<BackupManager.LocalBackupItem, String> colSize;

    private final ObservableList<BackupManager.LocalBackupItem> backupItems = FXCollections.observableArrayList();
    private static final DateTimeFormatter EXPORT_NAME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm");

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        if (lblBackupDirectory != null) {
            lblBackupDirectory.setText("Dossier local : " + BackupManager.getBackupDir());
        }

        colFileName.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().fileName()));
        colDate.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().formattedDate()));
        colSize.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().formattedSize()));

        backupTable.setItems(backupItems);

        enforceAccessRestrictions();
        loadLocalBackups();
    }

    @Override
    public void refresh() {
        enforceAccessRestrictions();
        loadLocalBackups();
    }

    private void enforceAccessRestrictions() {
        boolean authorized = UserSession.isAdmin() && AppConfig.isHost();
        if (btnExportCni != null) {
            btnExportCni.setDisable(!authorized);
        }
        if (btnRestoreImport != null) {
            btnRestoreImport.setDisable(!authorized);
        }
        if (btnCopyUsb != null) {
            btnCopyUsb.setDisable(!authorized);
        }
        if (!AppConfig.isHost() && lblHostBadge != null) {
            lblHostBadge.setText("Poste Client (Gestion sur le Serveur uniquement)");
            lblHostBadge.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #b91c1c; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 6 12; -fx-background-radius: 999;");
        }
    }

    private void loadLocalBackups() {
        backupItems.clear();
        if (AppConfig.isHost()) {
            List<BackupManager.LocalBackupItem> list = BackupManager.listLocalBackups();
            backupItems.addAll(list);
        }
    }

    @FXML
    private void handleExportCni(ActionEvent event) {
        if (!UserSession.isAdmin() || !AppConfig.isHost()) {
            showAlert(Alert.AlertType.WARNING, "Accès refusé", "Seul un administrateur sur le poste principal peut exporter la base de données.");
            return;
        }

        Stage stage = (Stage) mainContainer.getScene().getWindow();
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exporter la base de données (.cni)");
        String suggestedName = "Sauvegarde_Cabinet_" + LocalDateTime.now().format(EXPORT_NAME_FMT) + ".cni";
        chooser.setInitialFileName(suggestedName);
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Archive Cabinet Nour El Islam (*.cni)", "*.cni")
        );

        File targetFile = chooser.showSaveDialog(stage);
        if (targetFile == null) {
            return;
        }
        if (!targetFile.getName().toLowerCase().endsWith(".cni")) {
            targetFile = new File(targetFile.getParentFile(), targetFile.getName() + ".cni");
        }

        try {
            BackupManager.BackupInspection exported = BackupManager.exportToCni(targetFile);
            showStatusBanner("Sauvegarde exportée avec succès vers : " + targetFile.getAbsolutePath(), false);

            Alert info = new Alert(Alert.AlertType.INFORMATION);
            info.setTitle("Exportation réussie");
            info.setHeaderText("Sauvegarde .cni créée avec succès !");
            info.setContentText(String.format(
                    "Fichier : %s\n\nDonnées sauvegardées :\n• Patients : %d\n• Séances : %d\n• Factures : %d\n• Utilisateurs : %d\n\nEmpreinte SHA-256 : %s...",
                    targetFile.getAbsolutePath(),
                    exported.patientsCount(),
                    exported.sessionsCount(),
                    exported.billsCount(),
                    exported.usersCount(),
                    exported.sha256().substring(0, Math.min(16, exported.sha256().length()))
            ));
            info.showAndWait();
        } catch (Exception e) {
            showStatusBanner("Échec de l'exportation : " + e.getMessage(), true);
            showAlert(Alert.AlertType.ERROR, "Erreur d'exportation", "Impossible d'exporter la base de données :\n" + e.getMessage());
        }
    }

    @FXML
    private void handleRestoreOrImport(ActionEvent event) {
        if (!UserSession.isAdmin() || !AppConfig.isHost()) {
            showAlert(Alert.AlertType.WARNING, "Accès refusé", "Seul un administrateur sur le poste principal peut restaurer ou importer des données.");
            return;
        }

        Stage stage = (Stage) mainContainer.getScene().getWindow();
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Sélectionner un fichier à restaurer ou importer (.cni, .sql, .db)");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Tous les formats supportés (*.cni, *.sql, *.db)", "*.cni", "*.sql", "*.db", "*.sqlite", "*.sqlite3"),
                new FileChooser.ExtensionFilter("Archive Cabinet Nour El Islam (*.cni)", "*.cni"),
                new FileChooser.ExtensionFilter("Fichier SQL ancienne version (*.sql)", "*.sql"),
                new FileChooser.ExtensionFilter("Base SQLite ancienne version (*.db)", "*.db", "*.sqlite", "*.sqlite3")
        );

        File selectedFile = chooser.showOpenDialog(stage);
        if (selectedFile == null) {
            return;
        }

        try {
            // 1. Pre-flight inspection and validation
            BackupManager.BackupInspection inspection = BackupManager.inspectBackupFile(selectedFile);

            // 2. Show inspection preview dialog before modifying active database
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Vérification avant restauration / importation");
            confirm.setHeaderText("Fichier valide : " + selectedFile.getName() + " [" + inspection.format() + "]");
            confirm.setContentText(String.format(
                    "Origine : %s\nDate : %s\nSHA-256 : %s...\n\n" +
                    "Enregistrements détectés :\n" +
                    "• Patients : %d\n" +
                    "• Séances : %d\n" +
                    "• Factures : %d\n" +
                    "• Utilisateurs : %d\n\n" +
                    "⚠️ Une sauvegarde de sécurité de la base actuelle sera créée automatiquement dans ~/clinic/backups/ avant le remplacement.\n" +
                    "Voulez-vous confirmer la restauration de ces données ?",
                    inspection.clinicName(),
                    inspection.timestamp(),
                    inspection.sha256().substring(0, Math.min(16, inspection.sha256().length())),
                    inspection.patientsCount(),
                    inspection.sessionsCount(),
                    inspection.billsCount(),
                    inspection.usersCount()
            ));

            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) {
                return;
            }

            // 3. Perform atomic restore (.cni) or clean-replace migration (.sql / .db)
            BackupManager.restoreOrImportDatabase(selectedFile);
            loadLocalBackups();

            Alert success = new Alert(Alert.AlertType.INFORMATION);
            success.setTitle("Opération terminée");
            success.setHeaderText("Données restaurées avec succès !");
            success.setContentText("La base de données a été mise à jour avec succès. Vous allez être redirigé vers l'écran de connexion.");
            success.showAndWait();

            redirectToLogin(stage);
        } catch (Exception e) {
            showStatusBanner("Erreur : " + e.getMessage(), true);
            showAlert(Alert.AlertType.ERROR, "Fichier invalide ou opération annulée", e.getMessage());
        }
    }

    @FXML
    private void handleRefreshBackups(ActionEvent event) {
        loadLocalBackups();
        showStatusBanner("Liste des sauvegardes locales actualisée.", false);
    }

    @FXML
    private void handleCopySelectedBackup(ActionEvent event) {
        BackupManager.LocalBackupItem selected = backupTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Aucune sélection", "Veuillez sélectionner une sauvegarde dans le tableau ci-dessous pour la copier.");
            return;
        }

        Stage stage = (Stage) mainContainer.getScene().getWindow();
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Copier la sauvegarde vers une clé USB ou un dossier externe");
        chooser.setInitialFileName(selected.fileName());

        File targetFile = chooser.showSaveDialog(stage);
        if (targetFile == null) {
            return;
        }

        try {
            BackupManager.copyBackupToExternal(selected.filePath(), targetFile);
            showStatusBanner("Sauvegarde copiée vers : " + targetFile.getAbsolutePath(), false);
            showAlert(Alert.AlertType.INFORMATION, "Copie terminée", "La sauvegarde a été copiée avec succès vers :\n" + targetFile.getAbsolutePath());
        } catch (Exception e) {
            showStatusBanner("Erreur lors de la copie : " + e.getMessage(), true);
            showAlert(Alert.AlertType.ERROR, "Erreur de copie", "Impossible de copier le fichier :\n" + e.getMessage());
        }
    }

    private void redirectToLogin(Stage currentStage) {
        try {
            ConnectionMonitor.stop();
            UserSession.clear();
            currentStage.close();

            Parent root = FXMLLoader.load(getClass().getResource("/fxml/login.fxml"));
            Stage loginStage = new Stage();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

            loginStage.setTitle("Cabinet Nour El Islam");
            loginStage.setResizable(false);
            loginStage.setScene(scene);
            loginStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showStatusBanner(String message, boolean isError) {
        if (statusBox == null || lblStatusMessage == null) return;
        statusBox.setVisible(true);
        statusBox.setManaged(true);
        lblStatusMessage.setText(message);
        if (isError) {
            statusBox.setStyle("-fx-background-color: #fef2f2; -fx-border-color: #fca5a5; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 10 16;");
            lblStatusMessage.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #991b1b;");
        } else {
            statusBox.setStyle("-fx-background-color: #f0fdf4; -fx-border-color: #86efac; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 10 16;");
            lblStatusMessage.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #166534;");
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

