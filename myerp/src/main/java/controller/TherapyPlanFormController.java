package controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import model.TherapyPlan;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class TherapyPlanFormController {

    @FXML
    private Label titleLabel;
    @FXML
    private DatePicker datePicker;
    @FXML
    private TextField costField;
    @FXML
    private Button saveBtn;

    private TherapyPlan plan;
    private boolean saveClicked = false;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public void setTherapyPlan(TherapyPlan plan) {
        this.plan = plan;

        if (plan != null) {
            titleLabel.setText("Modifier le Plan");
            datePicker.setValue(LocalDate.parse(plan.getDate(), DATE_FORMATTER));
            costField.setText(String.valueOf(plan.getCost()));
            saveBtn.setText("Mettre à jour");
        } else {
            titleLabel.setText("Nouveau Plan Thérapeutique");
            datePicker.setValue(LocalDate.now());
            saveBtn.setText("Enregistrer");
        }
    }

    public boolean isSaveClicked() {
        return saveClicked;
    }

    public TherapyPlan getTherapyPlan() {
        if (plan == null) {
            plan = new TherapyPlan();
        }
        plan.setDate(datePicker.getValue().format(DATE_FORMATTER));
        plan.setCost(Double.parseDouble(costField.getText()));
        return plan;
    }

    @FXML
    private void handleSave() {
        if (isInputValid()) {
            saveClicked = true;
            closeStage();
        }
    }

    @FXML
    private void handleCancel() {
        closeStage();
    }

    private void closeStage() {
        Stage stage = (Stage) saveBtn.getScene().getWindow();
        stage.close();
    }

    private boolean isInputValid() {
        String errorMessage = "";

        if (datePicker.getValue() == null) {
            errorMessage += "Date invalide !\n";
        }

        try {
            double cost = Double.parseDouble(costField.getText());
            if (cost <= 0) errorMessage += "Le coût doit être positif !\n";
        } catch (NumberFormatException e) {
            errorMessage += "Format du coût invalide !\n";
        }

        if (errorMessage.isEmpty()) {
            return true;
        } else {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Champs Invalides");
            alert.setHeaderText("Veuillez corriger les champs invalides");
            alert.setContentText(errorMessage);
            alert.showAndWait();
            return false;
        }
    }
}
