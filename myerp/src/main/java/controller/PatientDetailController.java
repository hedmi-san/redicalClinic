package controller;

import dao.PatientDAO;
import dao.SessionDAO;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import model.Patient;
import model.Session;

import java.io.IOException;
import java.util.List;

public class PatientDetailController {

    @FXML
    private Label heroInitials;
    @FXML
    private Label heroName;
    @FXML
    private Label heroPhone;

    @FXML
    private Label totalCostLabel;
    @FXML
    private Label totalPaidLabel;
    @FXML
    private Label balanceLabel;

    @FXML
    private VBox sessionsContainer;

    private PatientDAO patientDAO = new PatientDAO();
    private SessionDAO sessionDAO = new SessionDAO();
    private Patient patient;
    private Runnable onUpdateListener;

    public void setPatient(Patient patient, Runnable onUpdateListener) {
        this.patient = patient;
        this.onUpdateListener = onUpdateListener;
        refreshUI();
    }

    private void refreshUI() {
        if (patient == null)
            return;

        // Refresh data from DB to ensure accuracy
        Patient updatedPatient = patientDAO.getPatientById(patient.getId());
        if (updatedPatient != null) {
            this.patient = updatedPatient;
        }

        heroName.setText(patient.getName());
        heroPhone.setText(patient.getPhone() != null ? patient.getPhone() : "Aucun numéro");
        heroInitials.setText(getInitials(patient.getName()));

        List<Session> sessions = sessionDAO.getSessionsByPatientId(patient.getId());
        patient.setSessions(sessions);

        totalCostLabel.setText(String.format("%.2f DH", patient.getTotalCost()));
        totalPaidLabel.setText(String.format("%.2f DH", patient.getTotalPaid()));
        balanceLabel.setText(String.format("%.2f DH", patient.getBalance()));

        sessionsContainer.getChildren().clear();
        for (Session session : sessions) {
            sessionsContainer.getChildren().add(createSessionCard(session));
        }
    }

    private String getInitials(String name) {
        if (name == null || name.isEmpty())
            return "??";
        String[] parts = name.split("\\s+");
        if (parts.length >= 2) {
            return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
        }
        return name.substring(0, Math.min(2, name.length())).toUpperCase();
    }

    @FXML
    private void handleEditPatient() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pages/patient_form.fxml"));
            Parent root = loader.load();

            PatientFormController controller = loader.getController();
            controller.setPatient(patient);

            Stage stage = new Stage();
            stage.setTitle("Modifier le Patient");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();

            if (controller.isSaveClicked()) {
                patientDAO.updatePatient(controller.getPatient());
                refreshUI();
                if (onUpdateListener != null)
                    onUpdateListener.run();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleAddSession() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pages/session_form.fxml"));
            Parent root = loader.load();

            SessionFormController controller = loader.getController();
            controller.setSession(null, patient.getId());

            Stage stage = new Stage();
            stage.setTitle("Nouvelle Séance");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();

            if (controller.isSaveClicked()) {
                sessionDAO.addSession(controller.getSession());
                refreshUI();
                if (onUpdateListener != null)
                    onUpdateListener.run();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleDeletePatient() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Supprimer " + patient.getName() + " ?", ButtonType.YES,
                ButtonType.NO);
        alert.setTitle("Confirmation");
        alert.setHeaderText(null);
        alert.showAndWait().ifPresent(res -> {
            if (res == ButtonType.YES) {
                patientDAO.deletePatient(patient.getId());
                if (onUpdateListener != null)
                    onUpdateListener.run();
                handleClose();
            }
        });
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) heroName.getScene().getWindow();
        stage.close();
    }

    private VBox createSessionCard(Session session) {
        VBox card = new VBox(12);
        card.getStyleClass().add("session-card");

        HBox topRow = new HBox(12);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label dateLabel = new Label(session.getDate());
        dateLabel.getStyleClass().add("session-date");
        dateLabel.setMinWidth(120);

        String status = session.getPaymentStatus();
        String statusLabel = "IMPAYÉ";
        String colorClass = "badge-none";
        if ("FULL".equals(status)) {
            statusLabel = "PAYÉ";
            colorClass = "badge-full";
        } else if ("PARTIAL".equals(status)) {
            statusLabel = "PARTIEL";
            colorClass = "badge-partial";
        }

        Label badge = new Label(statusLabel);
        badge.getStyleClass().addAll("badge", colorClass);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox actions = new HBox(8);
        Button editBtn = new Button("Modifier");
        editBtn.getStyleClass().add("btn-ghost");
        editBtn.setOnAction(e -> {
            showSessionEditDialog(session);
        });

        Button deleteBtn = new Button("Supprimer");
        deleteBtn.getStyleClass().add("btn-ghost");
        deleteBtn.setStyle("-fx-text-fill: #e11d48;");
        deleteBtn.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Supprimer cette séance ?", ButtonType.YES,
                    ButtonType.NO);
            alert.showAndWait().ifPresent(res -> {
                if (res == ButtonType.YES) {
                    sessionDAO.deleteSession(session.getId(), session.getPatientId());
                    refreshUI();
                    if (onUpdateListener != null)
                        onUpdateListener.run();
                }
            });
        });

        actions.getChildren().addAll(editBtn, deleteBtn);
        topRow.getChildren().addAll(dateLabel, badge, spacer, actions);

        Label noteLabel = new Label(session.getTreatment());
        noteLabel.getStyleClass().add("session-note");
        noteLabel.setWrapText(true);

        Label costPaidLabel = new Label(
                String.format("Coût: %.2f DH | Payé: %.2f DH", session.getCost(), session.getPaidAmount()));
        costPaidLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px;");

        card.getChildren().addAll(topRow, noteLabel, costPaidLabel);
        return card;
    }

    private void showSessionEditDialog(Session session) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pages/session_form.fxml"));
            Parent root = loader.load();

            SessionFormController controller = loader.getController();
            controller.setSession(session, patient.getId());

            Stage stage = new Stage();
            stage.setTitle("Modifier la Séance");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();

            if (controller.isSaveClicked()) {
                sessionDAO.updateSession(controller.getSession());
                refreshUI();
                if (onUpdateListener != null)
                    onUpdateListener.run();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
