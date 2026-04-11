package controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import model.Session;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class TherapySessionFormController {

    @FXML
    private Label titleLabel;
    @FXML
    private DatePicker datePicker;
    @FXML
    private TextArea treatmentArea;
    @FXML
    private TextField costField;
    @FXML
    private Button saveBtn;

    private Session session;
    private int patientId;
    private int therapyPlanId;
    private boolean saveClicked = false;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public void setSession(Session session, int patientId, int therapyPlanId) {
        this.patientId = patientId;
        this.therapyPlanId = therapyPlanId;
        this.session = session;

        if (session != null) {
            titleLabel.setText("Modifier la Séance");
            datePicker.setValue(LocalDate.parse(session.getDate(), DATE_FORMATTER));
            treatmentArea.setText(session.getTreatment());
            costField.setText(String.valueOf(session.getCost()));
            saveBtn.setText("Mettre à jour");
        } else {
            titleLabel.setText("Nouvelle Séance");
            datePicker.setValue(LocalDate.now());
            saveBtn.setText("Enregistrer");
        }
    }

    public boolean isSaveClicked() {
        return saveClicked;
    }

    public Session getSession() {
        if (session == null) {
            session = new Session();
        }
        session.setPatientId(patientId);
        session.setTherapyPlanId(therapyPlanId);
        session.setDate(datePicker.getValue().format(DATE_FORMATTER));
        session.setTreatment(treatmentArea.getText());
        session.setCost(Double.parseDouble(costField.getText()));
        session.setPaidAmount(session.getCost()); // paidAmount = cost for therapy sessions
        return session;
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
            if (cost < 0) errorMessage += "Le montant doit être positif !\n";
        } catch (NumberFormatException e) {
            errorMessage += "Format du montant invalide !\n";
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
