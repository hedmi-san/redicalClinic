package controller;

import dao.PatientDAO;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import model.Patient;
import model.Session;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class SessionFormController {

    @FXML
    private Label titleLabel;
    @FXML
    private ComboBox<Patient> patientComboBox;
    @FXML
    private DatePicker datePicker;
    @FXML
    private TextArea treatmentArea;
    @FXML
    private TextField costField;
    @FXML
    private TextField paidAmountField;
    @FXML
    private Button saveBtn;

    private Session session;
    private boolean saveClicked = false;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final PatientDAO patientDAO = new PatientDAO();

    @FXML
    public void initialize() {
        setupPatientComboBox();
    }

    private void setupPatientComboBox() {
        List<Patient> patients = patientDAO.getAllPatients();
        patientComboBox.setItems(FXCollections.observableArrayList(patients));

        patientComboBox.setConverter(new StringConverter<Patient>() {
            @Override
            public String toString(Patient patient) {
                return patient == null ? "" : patient.getName();
            }

            @Override
            public Patient fromString(String string) {
                return null; // Not needed
            }
        });
    }

    public void setSession(Session session, int patientId) {
        this.session = session;

        if (session != null) {
            titleLabel.setText("Modifier la Séance");
            datePicker.setValue(LocalDate.parse(session.getDate(), DATE_FORMATTER));
            treatmentArea.setText(session.getTreatment());
            costColumnText(session.getCost());
            paidAmountText(session.getPaidAmount());

            // Set selected patient
            Patient selectedPatient = patientDAO.getPatientById(session.getPatientId());
            if (selectedPatient != null) {
                patientComboBox.getSelectionModel().select(selectedPatient);
            }

            saveBtn.setText("Mettre à jour");
        } else {
            titleLabel.setText("Nouvelle Séance");
            datePicker.setValue(LocalDate.now());
            if (patientId > 0) {
                Patient selectedPatient = patientDAO.getPatientById(patientId);
                if (selectedPatient != null) {
                    patientComboBox.getSelectionModel().select(selectedPatient);
                }
            }
            saveBtn.setText("Enregistrer");
        }
    }

    private void costColumnText(double value) {
        costField.setText(String.valueOf(value));
    }

    private void paidAmountText(double value) {
        paidAmountField.setText(String.valueOf(value));
    }

    public boolean isSaveClicked() {
        return saveClicked;
    }

    public Session getSession() {
        if (session == null) {
            session = new Session();
        }
        Patient selectedPatient = patientComboBox.getSelectionModel().getSelectedItem();
        if (selectedPatient != null) {
            session.setPatientId(selectedPatient.getId());
            session.setPatientName(selectedPatient.getName());
        } else {
            session.setPatientId(0);
            session.setPatientName(null);
        }
        session.setDate(datePicker.getValue().format(DATE_FORMATTER));
        session.setTreatment(treatmentArea.getText());
        session.setCost(Double.parseDouble(costField.getText()));
        session.setPaidAmount(Double.parseDouble(paidAmountField.getText()));
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
        if (treatmentArea.getText() == null || treatmentArea.getText().trim().isEmpty()) {
            errorMessage += "Description du traitement invalide !\n";
        }

        try {
            double cost = Double.parseDouble(costField.getText());
            if (cost < 0)
                errorMessage += "Le coût doit être positif !\n";
        } catch (NumberFormatException e) {
            errorMessage += "Format du coût invalide !\n";
        }

        try {
            double paid = Double.parseDouble(paidAmountField.getText());
            if (paid < 0)
                errorMessage += "Le montant payé doit être positif !\n";
        } catch (NumberFormatException e) {
            errorMessage += "Format du montant payé invalide !\n";
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
