package controller;

import dao.WorkerDAO;
import model.Worker;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ResourceBundle;

public class WorkerFormController implements Initializable {

    @FXML
    private VBox rootVBox;
    @FXML
    private Label lblTitle;
    @FXML
    private TextField txtName;
    @FXML
    private DatePicker dpBirthDate;
    @FXML
    private TextField txtBirthPlace;
    @FXML
    private TextField txtPhone;
    @FXML
    private TextField txtIdCard;
    @FXML
    private TextField txtFunction;
    @FXML
    private TextField txtFamilySituation;

    private WorkerDAO workerDAO;
    private Worker workerToEdit;
    private boolean isSaveSuccessful = false;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Load CSS programmatically
        String cssPath = getClass().getResource("/css/pages/worker_form.css").toExternalForm();
        if (cssPath != null) {
            rootVBox.getStylesheets().add(cssPath);
        }
    }

    public void setWorkerDAO(WorkerDAO workerDAO) {
        this.workerDAO = workerDAO;
    }

    public void setWorkerData(Worker worker) {
        this.workerToEdit = worker;
        if (worker != null) {
            lblTitle.setText("Modifier l'employé");
            txtName.setText(worker.getName());
            dpBirthDate.setValue(parseDate(worker.getBirthDate()));
            txtBirthPlace.setText(worker.getBirthPlace());
            txtPhone.setText(worker.getPhoneNumber());
            txtIdCard.setText(worker.getIdentityCardNumber());
            txtFunction.setText(worker.getFunction());
            txtFamilySituation.setText(worker.getFamillySituation());
        } else {
            lblTitle.setText("Ajouter un employé");
        }
    }

    public boolean isSaveSuccessful() {
        return isSaveSuccessful;
    }

    @FXML
    private void handleSave(ActionEvent event) {
        if (txtName.getText() == null || txtName.getText().trim().isEmpty()) {
            showAlert("Erreur de validation", "Le nom est requis.", Alert.AlertType.ERROR);
            return;
        }

        Worker worker = workerToEdit != null ? workerToEdit : new Worker();
        worker.setName(txtName.getText().trim());
        if (dpBirthDate.getValue() != null) {
            worker.setBirthDate(dpBirthDate.getValue().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        }
        worker.setBirthPlace(txtBirthPlace.getText());
        worker.setPhoneNumber(txtPhone.getText());
        worker.setIdentityCardNumber(txtIdCard.getText());
        worker.setFunction(txtFunction.getText());
        worker.setFamillySituation(txtFamilySituation.getText());

        if (workerToEdit == null) {
            isSaveSuccessful = workerDAO.addWorker(worker);
        } else {
            isSaveSuccessful = workerDAO.updateWorker(worker);
        }

        if (isSaveSuccessful) {
            closeDialog();
        } else {
            showAlert("Erreur de base de données",
                    "Échec de l'enregistrement de l'employé. Veuillez vérifier les informations.",
                    Alert.AlertType.ERROR);
        }
    }

    @FXML
    private void handleCancel(ActionEvent event) {
        closeDialog();
    }

    private void closeDialog() {
        Stage stage = (Stage) lblTitle.getScene().getWindow();
        stage.close();
    }

    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private LocalDate parseDate(String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            return null;
        }
        try {
            if (dateString.contains("/")) {
                return LocalDate.parse(dateString, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            } else {
                return LocalDate.parse(dateString, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            }
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
