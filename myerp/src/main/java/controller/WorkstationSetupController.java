package controller;

import config.AppConfig;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

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

    public boolean isConfigured() {
        return configured;
    }

    private void updateCardStyles() {
        boolean isHost = radioHost.isSelected();
        cardHost.setStyle(isHost
                ? "-fx-background-color: #f0fdf4; -fx-border-color: #16a34a; -fx-border-width: 2; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 16; -fx-cursor: hand;"
                : "-fx-background-color: #f8fafc; -fx-border-color: #cbd5e1; -fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 16; -fx-cursor: hand;");

        cardClient.setStyle(!isHost
                ? "-fx-background-color: #f0f9ff; -fx-border-color: #0284c7; -fx-border-width: 2; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 16; -fx-cursor: hand;"
                : "-fx-background-color: #f8fafc; -fx-border-color: #cbd5e1; -fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 16; -fx-cursor: hand;");
    }
}
