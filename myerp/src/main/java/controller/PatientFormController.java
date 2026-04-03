package controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import model.Patient;

public class PatientFormController {

    @FXML
    private Label titleLabel;
    @FXML
    private TextField nameField;
    @FXML
    private TextField phoneField;
    @FXML
    private Button saveBtn;

    private Patient patient;
    private boolean saveClicked = false;

    public void setPatient(Patient patient) {
        this.patient = patient;
        if (patient != null) {
            titleLabel.setText("Modifier le Patient");
            nameField.setText(patient.getName());
            phoneField.setText(patient.getPhone());
            saveBtn.setText("Mettre à jour");
        } else {
            titleLabel.setText("Nouveau Patient");
            saveBtn.setText("Enregistrer");
        }
    }

    public boolean isSaveClicked() {
        return saveClicked;
    }

    public Patient getPatient() {
        if (patient == null) {
            patient = new Patient();
        }
        patient.setName(nameField.getText());
        patient.setPhone(phoneField.getText());
        return patient;
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
        Stage stage = (Stage) nameField.getScene().getWindow();
        stage.close();
    }

    private boolean isInputValid() {
        String errorMessage = "";

        if (nameField.getText() == null || nameField.getText().trim().isEmpty()) {
            errorMessage += "Nom invalide !\n";
        }
        if (phoneField.getText() != null && !phoneField.getText().isEmpty() && !phoneField.getText().matches("\\d+")) {
            errorMessage += "Le téléphone ne doit contenir que des chiffres !\n";
        }
        
        if (errorMessage.isEmpty()) {
            dao.PatientDAO dao = new dao.PatientDAO();
            int currentId = (patient != null && patient.getId() != 0) ? patient.getId() : -1;
            if (dao.patientExists(nameField.getText(), phoneField.getText(), currentId)) {
                errorMessage += "Un patient avec ce nom et ce numéro de téléphone existe déjà !\n";
            }
        }

        if (errorMessage.isEmpty()) {
            return true;
        } else {
            // Alert user
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle("Champs Invalides");
            alert.setHeaderText("Veuillez corriger les champs invalides");
            alert.setContentText(errorMessage);
            alert.showAndWait();
            return false;
        }
    }
}
