package controller;

import dao.SessionDAO;
import dao.TherapyPlanDAO;
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
import model.Session;
import model.TherapyPlan;

import java.io.IOException;
import java.util.List;

public class TherapyPlanDetailController {

    @FXML
    private Label titleLabel;
    @FXML
    private Label planCostLabel;
    @FXML
    private Label sessionsTotalLabel;
    @FXML
    private Label remainingLabel;
    @FXML
    private VBox sessionsContainer;

    private TherapyPlan plan;
    private final SessionDAO sessionDAO = new SessionDAO();
    private final TherapyPlanDAO therapyPlanDAO = new TherapyPlanDAO();
    private boolean planDeleted = false;
    private boolean dataChanged = false;

    public void setTherapyPlan(TherapyPlan plan) {
        this.plan = plan;
        refreshUI();
    }

    public boolean isPlanDeleted() {
        return planDeleted;
    }

    public boolean isDataChanged() {
        return dataChanged;
    }

    private void refreshUI() {
        if (plan == null) return;

        titleLabel.setText("Plan du " + plan.getDate());

        List<Session> sessions = sessionDAO.getSessionsByTherapyPlanId(plan.getId());

        double planCost = plan.getCost();
        double sessionsTotal = sessions.stream().mapToDouble(Session::getCost).sum();
        double remaining = planCost - sessionsTotal;

        planCostLabel.setText(String.format("%.2f DZD", planCost));
        sessionsTotalLabel.setText(String.format("%.2f DZD", sessionsTotal));
        remainingLabel.setText(String.format("%.2f DZD", remaining));

        sessionsContainer.getChildren().clear();
        for (Session session : sessions) {
            sessionsContainer.getChildren().add(createSessionCard(session));
        }
    }

    @FXML
    private void handleAddSession() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pages/therapy_session_form.fxml"));
            Parent root = loader.load();

            TherapySessionFormController controller = loader.getController();
            controller.setSession(null, plan.getPatientId(), plan.getId());

            Stage stage = new Stage();
            stage.setTitle("Nouvelle Séance");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();

            if (controller.isSaveClicked()) {
                sessionDAO.addTherapySession(controller.getSession());
                dataChanged = true;
                refreshUI();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleEditPlan() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pages/therapy_plan_form.fxml"));
            Parent root = loader.load();

            TherapyPlanFormController controller = loader.getController();
            controller.setTherapyPlan(plan);

            Stage stage = new Stage();
            stage.setTitle("Modifier le Plan");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();

            if (controller.isSaveClicked()) {
                TherapyPlan updated = controller.getTherapyPlan();
                updated.setPatientId(plan.getPatientId());
                updated.setId(plan.getId());
                therapyPlanDAO.updateTherapyPlan(updated);
                this.plan = updated;
                dataChanged = true;
                refreshUI();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleDeletePlan() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Supprimer ce plan et toutes ses séances ?", ButtonType.YES, ButtonType.NO);
        alert.setTitle("Confirmation");
        alert.setHeaderText(null);
        alert.showAndWait().ifPresent(res -> {
            if (res == ButtonType.YES) {
                therapyPlanDAO.deleteTherapyPlan(plan.getId());
                planDeleted = true;
                dataChanged = true;
                handleClose();
            }
        });
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) titleLabel.getScene().getWindow();
        stage.close();
    }

    private VBox createSessionCard(Session session) {
        VBox card = new VBox(10);
        card.getStyleClass().add("session-card");

        HBox topRow = new HBox(12);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label dateLabel = new Label(session.getDate());
        dateLabel.getStyleClass().add("session-date");
        dateLabel.setMinWidth(120);

        Label costLabel = new Label(String.format("%.2f DZD", session.getCost()));
        costLabel.setStyle("-fx-text-fill: #0d9488; -fx-font-weight: bold; -fx-font-size: 14px;");

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
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Supprimer cette séance ?",
                    ButtonType.YES, ButtonType.NO);
            alert.showAndWait().ifPresent(res -> {
                if (res == ButtonType.YES) {
                    sessionDAO.deleteSession(session.getId(), session.getPatientId());
                    dataChanged = true;
                    refreshUI();
                }
            });
        });

        actions.getChildren().addAll(editBtn, deleteBtn);
        topRow.getChildren().addAll(dateLabel, costLabel, spacer, actions);

        card.getChildren().add(topRow);
        return card;
    }

    private void showSessionEditDialog(Session session) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pages/therapy_session_form.fxml"));
            Parent root = loader.load();

            TherapySessionFormController controller = loader.getController();
            controller.setSession(session, plan.getPatientId(), plan.getId());

            Stage stage = new Stage();
            stage.setTitle("Modifier la Séance");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();

            if (controller.isSaveClicked()) {
                sessionDAO.updateSession(controller.getSession());
                dataChanged = true;
                refreshUI();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
