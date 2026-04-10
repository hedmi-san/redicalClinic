package controller;

import dao.PatientDAO;
import dao.SessionDAO;
import dao.TherapyPlanDAO;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import model.Patient;
import model.Session;
import model.TherapyPlan;
import service.InvoiceService;

import java.io.File;
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
    @FXML
    private VBox therapyPlansContainer;

    private PatientDAO patientDAO = new PatientDAO();
    private SessionDAO sessionDAO = new SessionDAO();
    private TherapyPlanDAO therapyPlanDAO = new TherapyPlanDAO();
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

        // Normal sessions (therapyPlanId IS NULL)
        List<Session> sessions = sessionDAO.getSessionsByPatientId(this.patient.getId());
        this.patient.setSessions(sessions);

        // Explicitly calculate totals to ensure sync
        double totalCost = 0;
        double totalPaid = 0;
        for (Session s : sessions) {
            totalCost += s.getCost();
            totalPaid += s.getPaidAmount();
        }
        this.patient.setTotalCost(totalCost);
        this.patient.setTotalPaid(totalPaid);

        totalCostLabel.setText(String.format("%.2f DZD", totalCost));
        totalPaidLabel.setText(String.format("%.2f DZD", totalPaid));
        balanceLabel.setText(String.format("%.2f DZD", totalCost - totalPaid));

        sessionsContainer.getChildren().clear();
        for (Session session : sessions) {
            sessionsContainer.getChildren().add(createSessionCard(session));
        }

        // Therapy plans
        refreshTherapyPlans();
    }

    private void refreshTherapyPlans() {
        therapyPlansContainer.getChildren().clear();
        List<TherapyPlan> plans = therapyPlanDAO.getTherapyPlansByPatientId(patient.getId());
        for (TherapyPlan plan : plans) {
            therapyPlansContainer.getChildren().add(createTherapyPlanCard(plan));
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

    @FXML
    private void printPatientBill() {
        try {
            // 1. Load the session selection dialog
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pages/patient_session_invoice_form.fxml"));
            Parent root = loader.load();

            PatientSessionInvoiceFormController controller = loader.getController();

            // Pass current patient's sessions to the dialog
            List<Session> sessions = sessionDAO.getSessionsByPatientId(patient.getId());
            controller.setSessions(sessions);

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Sélectionner les séances");
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.setScene(new Scene(root));
            dialogStage.showAndWait();

            // 2. Check if user confirmed and selected sessions
            if (!controller.isConfirmed()) {
                return;
            }

            List<Session> selectedSessions = controller.getSelectedSessions();
            if (selectedSessions.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Aucune séance");
                alert.setHeaderText(null);
                alert.setContentText("Veuillez sélectionner au moins une séance pour générer la facture.");
                alert.showAndWait();
                return;
            }

            // 3. File chooser to select save location
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Enregistrer la facture PDF");
            fileChooser.setInitialFileName("Facture_" + patient.getName().replaceAll("\\s+", "_") + ".pdf");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
            );

            Stage ownerStage = (Stage) heroName.getScene().getWindow();
            File file = fileChooser.showSaveDialog(ownerStage);

            if (file == null) {
                return; // user cancelled
            }

            // 4. Generate the PDF
            InvoiceService invoiceService = new InvoiceService();
            invoiceService.generateInvoice(patient, selectedSessions, file);

            // 5. Success alert
            Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
            successAlert.setTitle("Facture générée");
            successAlert.setHeaderText(null);
            successAlert.setContentText("La facture a été enregistrée avec succès:\n" + file.getAbsolutePath());
            successAlert.showAndWait();

        } catch (IOException e) {
            e.printStackTrace();
            Alert errorAlert = new Alert(Alert.AlertType.ERROR);
            errorAlert.setTitle("Erreur");
            errorAlert.setHeaderText(null);
            errorAlert.setContentText("Une erreur est survenue lors de la génération de la facture:\n" + e.getMessage());
            errorAlert.showAndWait();
        }
    }

    // ======================== THERAPY PLAN ========================

    @FXML
    private void handleAddTherapyPlan() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pages/therapy_plan_form.fxml"));
            Parent root = loader.load();

            TherapyPlanFormController controller = loader.getController();
            controller.setTherapyPlan(null);

            Stage stage = new Stage();
            stage.setTitle("Nouveau Plan Thérapeutique");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();

            if (controller.isSaveClicked()) {
                TherapyPlan plan = controller.getTherapyPlan();
                plan.setPatientId(patient.getId());
                therapyPlanDAO.addTherapyPlan(plan);
                refreshTherapyPlans();
                if (onUpdateListener != null)
                    onUpdateListener.run();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showTherapyPlanDetail(TherapyPlan plan) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pages/therapy_plan_detail.fxml"));
            Parent root = loader.load();

            TherapyPlanDetailController controller = loader.getController();
            controller.setTherapyPlan(plan);

            Stage stage = new Stage();
            stage.setTitle("Détail du Plan");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();

            // Refresh if data changed
            if (controller.isDataChanged()) {
                refreshTherapyPlans();
                if (onUpdateListener != null)
                    onUpdateListener.run();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private VBox createTherapyPlanCard(TherapyPlan plan) {
        VBox card = new VBox(10);
        card.getStyleClass().add("session-card");

        // Double-click to open detail
        card.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                showTherapyPlanDetail(plan);
            }
        });

        HBox topRow = new HBox(12);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label dateLabel = new Label(plan.getDate());
        dateLabel.getStyleClass().add("session-date");
        dateLabel.setMinWidth(120);

        // Calculate sessions total for this plan
        List<Session> planSessions = sessionDAO.getSessionsByTherapyPlanId(plan.getId());
        double sessionsTotal = planSessions.stream().mapToDouble(Session::getCost).sum();
        double remaining = plan.getCost() - sessionsTotal;

        Label costLabel = new Label(String.format("Coût: %.2f DZD", plan.getCost()));
        costLabel.setStyle("-fx-text-fill: #0d9488; -fx-font-weight: bold; -fx-font-size: 14px;");

        // Badge for status
        String badgeText;
        String badgeClass;
        if (remaining <= 0) {
            badgeText = "COMPLET";
            badgeClass = "badge-full";
        } else if (sessionsTotal > 0) {
            badgeText = String.format("RESTE: %.2f", remaining);
            badgeClass = "badge-partial";
        } else {
            badgeText = "AUCUNE SÉANCE";
            badgeClass = "badge-none";
        }

        Label badge = new Label(badgeText);
        badge.getStyleClass().addAll("badge", badgeClass);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label hintLabel = new Label("Double-cliquer pour ouvrir");
        hintLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-font-style: italic;");

        topRow.getChildren().addAll(dateLabel, costLabel, badge, spacer, hintLabel);

        card.getChildren().add(topRow);
        return card;
    }

    // ======================== NORMAL SESSIONS ========================

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
                String.format("Coût: %.2f DZD | Payé: %.2f DZD", session.getCost(), session.getPaidAmount()));
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
