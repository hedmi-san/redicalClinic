package controller;

import dao.PatientDAO;
import dao.PaymentDAO;
import dao.SessionDAO;
import dao.TherapyPlanDAO;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import model.Patient;
import model.Payment;
import model.Session;
import model.TherapyPlan;
import service.InvoiceService;
import util.UserSession;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
    @FXML
    private VBox paymentsContainer;

    private final PatientDAO patientDAO = new PatientDAO();
    private final SessionDAO sessionDAO = new SessionDAO();
    private final TherapyPlanDAO therapyPlanDAO = new TherapyPlanDAO();
    private final PaymentDAO paymentDAO = new PaymentDAO();
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

        // Synchronize and refresh data from DB to ensure ledger accuracy
        patientDAO.updatePatientTotals(patient.getId());
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

        double totalCost = this.patient.getTotalCost();
        double totalPaid = this.patient.getTotalPaid();
        double balance = totalCost - totalPaid;

        totalCostLabel.setText(String.format("%.2f DZD", totalCost));
        totalPaidLabel.setText(String.format("%.2f DZD", totalPaid));

        if (balance < -0.01) {
            balanceLabel.setText(String.format("Avance: %.2f DZD", Math.abs(balance)));
            balanceLabel.setStyle("-fx-text-fill: #0d9488; -fx-font-weight: bold;");
        } else if (balance > 0.01) {
            balanceLabel.setText(String.format("Reste: %.2f DZD", balance));
            balanceLabel.setStyle("-fx-text-fill: #e11d48; -fx-font-weight: bold;");
        } else {
            balanceLabel.setText("0.00 DZD (Réglé)");
            balanceLabel.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;");
        }

        sessionsContainer.getChildren().clear();
        for (Session session : sessions) {
            sessionsContainer.getChildren().add(createSessionCard(session, balance));
        }

        // Display Therapy plans
        List<TherapyPlan> plans = therapyPlanDAO.getTherapyPlansByPatientId(patient.getId());
        refreshTherapyPlans(plans);

        // Display Payments Ledger
        refreshPayments();
    }

    private void refreshTherapyPlans(List<TherapyPlan> plans) {
        therapyPlansContainer.getChildren().clear();
        for (TherapyPlan plan : plans) {
            therapyPlansContainer.getChildren().add(createTherapyPlanCard(plan));
        }
    }

    private void refreshTherapyPlans() {
        List<TherapyPlan> plans = therapyPlanDAO.getTherapyPlansByPatientId(patient.getId());
        refreshTherapyPlans(plans);
    }

    private void refreshPayments() {
        if (paymentsContainer == null) return;
        paymentsContainer.getChildren().clear();
        List<Payment> payments = paymentDAO.getPaymentsByPatientId(patient.getId());
        if (payments.isEmpty()) {
            Label emptyLabel = new Label("Aucun versement ou paiement enregistré pour ce patient.");
            emptyLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-padding: 12px;");
            paymentsContainer.getChildren().add(emptyLabel);
            return;
        }

        for (Payment payment : payments) {
            paymentsContainer.getChildren().add(createPaymentCard(payment));
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
                javafx.application.Platform.runLater(this::refreshUI);
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

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(javafx.stage.StageStyle.TRANSPARENT);
            Scene scene = new Scene(root);
            scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
            stage.setScene(scene);
            stage.setResizable(false);

            controller.setSession(null, patient.getId());
            controller.initStage(stage);

            stage.sizeToScene();
            stage.showAndWait();

            if (controller.isSaveClicked()) {
                String username = UserSession.isLoggedIn() ? UserSession.getCurrentUser().getUserName() : "Employé";
                sessionDAO.addSession(controller.getSession(), controller.getCashReceived(), username);
                javafx.application.Platform.runLater(this::refreshUI);
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
            showErrorAlert("Une erreur est survenue lors de la génération de la facture:\n" + e.getMessage());
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
                patientDAO.updatePatientTotals(patient.getId());
                refreshTherapyPlans();
                refreshUI();
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
                patientDAO.updatePatientTotals(patient.getId());
                refreshTherapyPlans();
                refreshUI();
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

    private VBox createSessionCard(Session session, double patientBalance) {
        VBox card = new VBox(12);
        card.getStyleClass().add("session-card");

        HBox topRow = new HBox(12);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label dateLabel = new Label(session.getDate());
        dateLabel.getStyleClass().add("session-date");
        dateLabel.setMinWidth(120);

        String statusLabel;
        String colorClass;
        if (session.getPaidAmount() >= session.getCost() && session.getCost() > 0) {
            statusLabel = "RÉGLÉ";
            colorClass = "badge-full";
        } else if (session.getPaidAmount() > 0) {
            statusLabel = "PARTIEL";
            colorClass = "badge-partial";
        } else if (patientBalance <= 0.01) {
            statusLabel = "COUVERT (AVANCE)";
            colorClass = "badge-full";
        } else {
            statusLabel = "IMPAYÉ";
            colorClass = "badge-none";
        }

        Label badge = new Label(statusLabel);
        badge.getStyleClass().addAll("badge", colorClass);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox actions = new HBox(8);
        Button editBtn = new Button("Modifier");
        editBtn.getStyleClass().add("btn-ghost");
        editBtn.setOnAction(e -> showSessionEditDialog(session));

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
                String.format("Coût: %.2f DZD | Espèces reçues: %.2f DZD", session.getCost(), session.getPaidAmount()));
        costPaidLabel.setStyle("-fx-text-fill: #64748b; -fx-font-size: 13px;");

        card.getChildren().addAll(topRow, noteLabel, costPaidLabel);
        return card;
    }

    private void showSessionEditDialog(Session session) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pages/session_form.fxml"));
            Parent root = loader.load();
            SessionFormController controller = loader.getController();

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(javafx.stage.StageStyle.TRANSPARENT);
            Scene scene = new Scene(root);
            scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
            stage.setScene(scene);
            stage.setResizable(false);

            controller.setSession(session, patient.getId());
            controller.initStage(stage);

            stage.sizeToScene();
            stage.showAndWait();

            if (controller.isSaveClicked()) {
                String username = UserSession.isLoggedIn() ? UserSession.getCurrentUser().getUserName() : "Employé";
                sessionDAO.updateSession(controller.getSession(), controller.getCashReceived(), username);
                javafx.application.Platform.runLater(this::refreshUI);
                if (onUpdateListener != null)
                    onUpdateListener.run();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ======================== PAYMENTS & ADVANCE LEDGER ========================

    private VBox createPaymentCard(Payment payment) {
        VBox card = new VBox(8);
        card.getStyleClass().add("session-card");

        HBox topRow = new HBox(12);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label dateLabel = new Label(payment.getPaymentDate());
        dateLabel.getStyleClass().add("session-date");
        dateLabel.setMinWidth(110);

        String badgeText;
        String badgeStyle;
        String type = payment.getPaymentType() != null ? payment.getPaymentType() : Payment.TYPE_SESSION;
        switch (type) {
            case Payment.TYPE_DEPOSIT -> {
                badgeText = "Versement libre";
                badgeStyle = "-fx-background-color: #d1fae5; -fx-text-fill: #047857; -fx-background-radius: 9999px; -fx-padding: 3 10; -fx-font-weight: bold; -fx-font-size: 11px;";
            }
            case Payment.TYPE_SETTLEMENT -> {
                badgeText = "Règlement dette";
                badgeStyle = "-fx-background-color: #ccfbf1; -fx-text-fill: #0f766e; -fx-background-radius: 9999px; -fx-padding: 3 10; -fx-font-weight: bold; -fx-font-size: 11px;";
            }
            case Payment.TYPE_REFUND -> {
                badgeText = "Remboursement";
                badgeStyle = "-fx-background-color: #ffedd5; -fx-text-fill: #c2410c; -fx-background-radius: 9999px; -fx-padding: 3 10; -fx-font-weight: bold; -fx-font-size: 11px;";
            }
            case Payment.TYPE_REVERSAL -> {
                badgeText = "Annulation";
                badgeStyle = "-fx-background-color: #fee2e2; -fx-text-fill: #b91c1c; -fx-background-radius: 9999px; -fx-padding: 3 10; -fx-font-weight: bold; -fx-font-size: 11px;";
            }
            case Payment.TYPE_THERAPY_PLAN -> {
                badgeText = "Plan Thérapeutique";
                badgeStyle = "-fx-background-color: #f3e8ff; -fx-text-fill: #7e22ce; -fx-background-radius: 9999px; -fx-padding: 3 10; -fx-font-weight: bold; -fx-font-size: 11px;";
            }
            default -> {
                badgeText = "Séance";
                badgeStyle = "-fx-background-color: #e0f2fe; -fx-text-fill: #0369a1; -fx-background-radius: 9999px; -fx-padding: 3 10; -fx-font-weight: bold; -fx-font-size: 11px;";
            }
        }

        Label typeBadge = new Label(badgeText);
        typeBadge.setStyle(badgeStyle);

        Label amountLabel = new Label();
        if (payment.getAmount() >= 0) {
            amountLabel.setText(String.format("+%.2f DZD", payment.getAmount()));
            amountLabel.setStyle("-fx-text-fill: #059669; -fx-font-weight: bold; -fx-font-size: 14px;");
        } else {
            amountLabel.setText(String.format("%.2f DZD", payment.getAmount()));
            amountLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold; -fx-font-size: 14px;");
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_RIGHT);

        // Allow correction and reversal only for positive non-reversal rows
        if (payment.getAmount() > 0 && !Payment.TYPE_REVERSAL.equalsIgnoreCase(payment.getPaymentType())) {
            Button correctBtn = new Button("Corriger");
            correctBtn.getStyleClass().add("btn-ghost");
            correctBtn.setOnAction(e -> handleCorrectPayment(payment));

            Button reverseBtn = new Button("Annuler");
            reverseBtn.getStyleClass().add("btn-ghost");
            reverseBtn.setStyle("-fx-text-fill: #e11d48;");
            reverseBtn.setOnAction(e -> handleReversePayment(payment));

            actions.getChildren().addAll(correctBtn, reverseBtn);
        }

        topRow.getChildren().addAll(dateLabel, typeBadge, amountLabel, spacer, actions);

        Label reasonLabel = new Label(payment.getReason() != null ? payment.getReason() : "");
        reasonLabel.getStyleClass().add("session-note");
        reasonLabel.setWrapText(true);

        StringBuilder auditInfo = new StringBuilder();
        auditInfo.append("Enregistré par : ").append(payment.getCreatedBy() != null ? payment.getCreatedBy() : "Système");
        if (payment.getSessionId() != null) {
            auditInfo.append(" | Séance #").append(payment.getSessionId());
        }
        if (payment.getReversalOf() != null) {
            auditInfo.append(" | Annule réf #").append(payment.getReversalOf());
        }

        Label auditLabel = new Label(auditInfo.toString());
        auditLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");

        card.getChildren().addAll(topRow, reasonLabel, auditLabel);
        return card;
    }

    @FXML
    private void handleAddPaymentAction() {
        if (patient == null) return;

        Dialog<Payment> dialog = new Dialog<>();
        dialog.setTitle("Nouveau Versement");
        dialog.setHeaderText("Enregistrer un versement pour " + patient.getName());

        ButtonType saveButtonType = new ButtonType("Enregistrer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20, 20, 10, 20));

        ComboBox<String> typeCombo = new ComboBox<>();
        typeCombo.getItems().addAll("Versement libre (Avance future)", "Règlement dette");
        typeCombo.getSelectionModel().select(0);
        typeCombo.setMaxWidth(Double.MAX_VALUE);

        TextField amountField = new TextField();
        amountField.setPromptText("Montant (DZD), ex: 2000.00");

        TextField reasonField = new TextField();
        reasonField.setPromptText("Motif (facultatif)");

        grid.add(new Label("Type de versement:"), 0, 0);
        grid.add(typeCombo, 1, 0);
        grid.add(new Label("Montant (DZD):"), 0, 1);
        grid.add(amountField, 1, 1);
        grid.add(new Label("Motif / Remarque:"), 0, 2);
        grid.add(reasonField, 1, 2);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                double amount = SessionFormController.parseAmount(amountField.getText());
                if (amount <= 0) {
                    return null;
                }
                String type = typeCombo.getSelectionModel().getSelectedIndex() == 0
                        ? Payment.TYPE_DEPOSIT
                        : Payment.TYPE_SETTLEMENT;
                String reason = reasonField.getText().trim();
                if (reason.isEmpty()) {
                    reason = typeCombo.getSelectionModel().getSelectedIndex() == 0 ? "Versement d'avance" : "Règlement dette";
                }
                String username = UserSession.isLoggedIn() ? UserSession.getCurrentUser().getUserName() : "Employé";

                Payment payment = new Payment();
                payment.setPatientId(patient.getId());
                payment.setPaymentDate(LocalDate.now().toString());
                payment.setAmount(amount);
                payment.setPaymentType(type);
                payment.setReason(reason);
                payment.setCreatedBy(username);
                return payment;
            }
            return null;
        });

        Optional<Payment> result = dialog.showAndWait();
        result.ifPresent(payment -> {
            boolean ok = paymentDAO.recordPayment(payment);
            if (ok) {
                patientDAO.updatePatientTotals(patient.getId());
                refreshUI();
                if (onUpdateListener != null)
                    onUpdateListener.run();
            } else {
                showErrorAlert("Impossible d'enregistrer le versement.");
            }
        });
    }

    @FXML
    private void handleRefundAction() {
        if (patient == null) return;

        double balance = patient.getTotalCost() - patient.getTotalPaid();
        double availableCredit = balance < -0.01 ? Math.abs(balance) : 0;

        if (availableCredit <= 0.01) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Aucune Avance Disponible");
            alert.setHeaderText(null);
            alert.setContentText("Le patient " + patient.getName() + " n'a aucune avance disponible à rembourser.\n(Solde actuel: " + String.format("%.2f DZD", balance) + ")");
            alert.showAndWait();
            return;
        }

        Dialog<Double> dialog = new Dialog<>();
        dialog.setTitle("Rembourser Avance");
        dialog.setHeaderText("Remboursement d'avance au patient : " + patient.getName());

        ButtonType refundButtonType = new ButtonType("Rembourser", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(refundButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20, 20, 10, 20));

        Label creditLabel = new Label(String.format("Crédit disponible: %.2f DZD", availableCredit));
        creditLabel.setStyle("-fx-text-fill: #0d9488; -fx-font-weight: bold;");

        TextField amountField = new TextField(String.format(Locale.US, "%.2f", availableCredit));
        TextField reasonField = new TextField("Remboursement surplus patient");

        grid.add(new Label("Avance disponible:"), 0, 0);
        grid.add(creditLabel, 1, 0);
        grid.add(new Label("Montant à rembourser (DZD):"), 0, 1);
        grid.add(amountField, 1, 1);
        grid.add(new Label("Motif du remboursement:"), 0, 2);
        grid.add(reasonField, 1, 2);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == refundButtonType) {
                return SessionFormController.parseAmount(amountField.getText());
            }
            return null;
        });

        Optional<Double> result = dialog.showAndWait();
        if (result.isPresent()) {
            double refundAmount = result.get();
            if (refundAmount <= 0 || refundAmount > availableCredit + 0.01) {
                showErrorAlert(String.format("Montant invalide. Le remboursement doit être compris entre 0.01 et %.2f DZD.", availableCredit));
                return;
            }

            String reason = reasonField.getText().trim();
            if (reason.isEmpty()) reason = "Remboursement avance patient";
            String username = UserSession.isLoggedIn() ? UserSession.getCurrentUser().getUserName() : "Employé";

            boolean ok = paymentDAO.refundCredit(patient.getId(), refundAmount, reason, username);
            if (ok) {
                patientDAO.updatePatientTotals(patient.getId());
                refreshUI();
                if (onUpdateListener != null)
                    onUpdateListener.run();
            } else {
                showErrorAlert("Impossible d'effectuer le remboursement.");
            }
        }
    }

    private void handleReversePayment(Payment p) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Annuler le Paiement");
        dialog.setHeaderText(String.format("Annulation du paiement #%d de %.2f DZD\n(Action traçable dans le grand livre)", p.getId(), p.getAmount()));
        dialog.setContentText("Motif obligatoire de l'annulation :");

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            String reason = result.get().trim();
            if (reason.isEmpty()) {
                showErrorAlert("Le motif de l'annulation est obligatoire.");
                return;
            }

            String username = UserSession.isLoggedIn() ? UserSession.getCurrentUser().getUserName() : "Employé";
            boolean ok = paymentDAO.reversePayment(p.getId(), reason, username);
            if (ok) {
                patientDAO.updatePatientTotals(patient.getId());
                refreshUI();
                if (onUpdateListener != null)
                    onUpdateListener.run();
            } else {
                showErrorAlert("Impossible d'annuler ce paiement (il a peut-être déjà été annulé).");
            }
        }
    }

    private void handleCorrectPayment(Payment p) {
        Dialog<Double> dialog = new Dialog<>();
        dialog.setTitle("Corriger le Paiement #" + p.getId());
        dialog.setHeaderText(String.format("Correction du paiement #%d (Montant actuel: %.2f DZD)", p.getId(), p.getAmount()));

        ButtonType saveButtonType = new ButtonType("Corriger", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20, 20, 10, 20));

        TextField amountField = new TextField(String.format(Locale.US, "%.2f", p.getAmount()));
        TextField reasonField = new TextField();
        reasonField.setPromptText("Motif de la correction (ex: Erreur de saisie)");

        grid.add(new Label("Nouveau montant (DZD):"), 0, 0);
        grid.add(amountField, 1, 0);
        grid.add(new Label("Motif de la correction:"), 0, 1);
        grid.add(reasonField, 1, 1);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                return SessionFormController.parseAmount(amountField.getText());
            }
            return null;
        });

        Optional<Double> result = dialog.showAndWait();
        if (result.isPresent()) {
            double newAmount = result.get();
            if (newAmount <= 0) {
                showErrorAlert("Le nouveau montant doit être strictement supérieur à 0.");
                return;
            }

            String reason = reasonField.getText().trim();
            if (reason.isEmpty()) {
                showErrorAlert("Le motif de la correction est obligatoire.");
                return;
            }

            String username = UserSession.isLoggedIn() ? UserSession.getCurrentUser().getUserName() : "Employé";
            boolean ok = paymentDAO.correctPayment(p.getId(), newAmount, reason, username);
            if (ok) {
                patientDAO.updatePatientTotals(patient.getId());
                refreshUI();
                if (onUpdateListener != null)
                    onUpdateListener.run();
            } else {
                showErrorAlert("Impossible de corriger ce paiement.");
            }
        }
    }

    private void showErrorAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Erreur");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
