package controller;

import config.AppConfig;
import config.BackupManager;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.Optional;

public class WorkstationSetupController {

    @FXML
    private ToggleGroup roleToggleGroup;

    @FXML
    private RadioButton radioHost;

    @FXML
    private RadioButton radioClient;

    @FXML
    private VBox cardHost;

    @FXML
    private VBox cardClient;

    @FXML
    private Label lblError;

    @FXML
    private Button btnConfirm;

    @FXML
    private Button btnRestoreBackup;

    private boolean configured = false;

    @FXML
    public void initialize() {
        updateCardStyles();
        roleToggleGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> updateCardStyles());
    }

    @FXML
    private void handleSelectHost(MouseEvent event) {
        radioHost.setSelected(true);
    }

    @FXML
    private void handleSelectClient(MouseEvent event) {
        radioClient.setSelected(true);
    }

    @FXML
    private void handleConfirm(ActionEvent event) {
        String role = radioHost.isSelected() ? AppConfig.ROLE_HOST : AppConfig.ROLE_CLIENT;
        try {
            AppConfig.setNodeRole(role);
            configured = true;
            Stage stage = (Stage) btnConfirm.getScene().getWindow();
            stage.close();
        } catch (Exception e) {
            lblError.setText("Erreur d'enregistrement: " + e.getMessage());
            lblError.setVisible(true);
        }
    }

    @FXML
    private void handleRestoreBackup(ActionEvent event) {
        lblError.setVisible(false);
        Stage stage = (Stage) btnConfirm.getScene().getWindow();

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Sélectionner une sauvegarde (.cni) ou une ancienne base (.sql, .db)");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Tous les formats supportés (*.cni, *.sql, *.db)", "*.cni", "*.sql", "*.db", "*.sqlite", "*.sqlite3"),
                new FileChooser.ExtensionFilter("Archive Cabinet Nour El Islam (*.cni)", "*.cni"),
                new FileChooser.ExtensionFilter("Export SQL ancienne version (*.sql)", "*.sql"),
                new FileChooser.ExtensionFilter("Base SQLite ancienne version (*.db)", "*.db", "*.sqlite", "*.sqlite3")
        );

        File selectedFile = chooser.showOpenDialog(stage);
        if (selectedFile == null) {
            return;
        }

        try {
            BackupManager.BackupInspection info = BackupManager.inspectBackupFile(selectedFile);

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirmation de restauration / importation");
            confirm.setHeaderText("Fichier vérifié : " + selectedFile.getName() + " (" + info.format() + ")");
            confirm.setContentText(String.format(
                    "Clinique : %s\nDate : %s\n\nContenu détecté :\n• Patients : %d\n• Séances : %d\n• Factures : %d\n• Utilisateurs : %d\n\n" +
                    "Cet ordinateur sera configuré comme Poste Principal (Serveur) et les données seront importées. Continuer ?",
                    info.clinicName(),
                    info.timestamp(),
                    info.patientsCount(),
                    info.sessionsCount(),
                    info.billsCount(),
                    info.usersCount()
            ));

            Optional<ButtonType> res = confirm.showAndWait();
            if (res.isEmpty() || res.get() != ButtonType.OK) {
                return;
            }

            BackupManager.restoreOrImportDatabase(selectedFile);
            configured = true;

            Alert success = new Alert(Alert.AlertType.INFORMATION);
            success.setTitle("Restauration réussie");
            success.setHeaderText("Données restaurées avec succès !");
            success.setContentText("Le poste principal est configuré et toutes vos données ont été restaurées. Vous pouvez maintenant vous connecter.");
            success.showAndWait();

            stage.close();
        } catch (Exception e) {
            lblError.setText("Erreur : " + e.getMessage());
            lblError.setVisible(true);

            Alert errAlert = new Alert(Alert.AlertType.ERROR);
            errAlert.setTitle("Fichier invalide ou erreur de restauration");
            errAlert.setHeaderText("Impossible de restaurer le fichier sélectionné");
            errAlert.setContentText(e.getMessage());
            errAlert.showAndWait();
        }
    }

    public boolean isConfigured() {
        return configured;
    }

    private void updateCardStyles() {
        boolean isHost = radioHost.isSelected();
        cardHost.setStyle(isHost
                ? "-fx-background-color: #f0fdf4; -fx-border-color: #16a34a; -fx-border-width: 2; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 14 16; -fx-cursor: hand;"
                : "-fx-background-color: #f8fafc; -fx-border-color: #cbd5e1; -fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 14 16; -fx-cursor: hand;");

        cardClient.setStyle(!isHost
                ? "-fx-background-color: #f0f9ff; -fx-border-color: #0284c7; -fx-border-width: 2; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 14 16; -fx-cursor: hand;"
                : "-fx-background-color: #f8fafc; -fx-border-color: #cbd5e1; -fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 14 16; -fx-cursor: hand;");
    }
}
