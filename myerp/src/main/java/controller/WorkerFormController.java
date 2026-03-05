package controller;

import dao.WorkerDAO;
import model.Worker;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class WorkerFormController {

    @FXML
    private Label lblTitle;
    @FXML
    private TextField txtName;
    @FXML
    private TextField txtBirthDate;
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

    public void setWorkerDAO(WorkerDAO workerDAO) {
        this.workerDAO = workerDAO;
    }

    public void setWorkerData(Worker worker) {
        this.workerToEdit = worker;
        if (worker != null) {
            lblTitle.setText("Edit Worker");
            txtName.setText(worker.getName());
            txtBirthDate.setText(worker.getBirthDate());
            txtBirthPlace.setText(worker.getBirthPlace());
            txtPhone.setText(worker.getPhoneNumber());
            txtIdCard.setText(worker.getIdentityCardNumber());
            txtFunction.setText(worker.getFunction());
            txtFamilySituation.setText(worker.getFamillySituation());
        } else {
            lblTitle.setText("Add New Worker");
        }
    }

    public boolean isSaveSuccessful() {
        return isSaveSuccessful;
    }

    @FXML
    private void handleSave(ActionEvent event) {
        if (txtName.getText() == null || txtName.getText().trim().isEmpty()) {
            showAlert("Validation Error", "Name is required.", Alert.AlertType.ERROR);
            return;
        }

        Worker worker = workerToEdit != null ? workerToEdit : new Worker();
        worker.setName(txtName.getText().trim());
        worker.setBirthDate(txtBirthDate.getText());
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
            showAlert("Database Error", "Failed to save the worker. Please check the inputs.", Alert.AlertType.ERROR);
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
}
