package controller;

import dao.PatientDAO;
import dao.SessionDAO;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;
import model.Patient;
import model.Session;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class PatientController implements Initializable {

    @FXML
    private TextField searchField;
    @FXML
    private TableView<Patient> patientTable;
    @FXML
    private TableColumn<Patient, String> nameColumn;
    @FXML
    private TableColumn<Patient, String> phoneColumn;
    @FXML
    private TableColumn<Patient, String> balanceColumn;

    private PatientDAO patientDAO = new PatientDAO();
    private SessionDAO sessionDAO = new SessionDAO();
    private ObservableList<Patient> masterPatientList = FXCollections.observableArrayList();
    private FilteredList<Patient> filteredPatientList;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupTable();
        setupSearch();
        loadPatients();
    }

    private void setupTable() {
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        phoneColumn.setCellValueFactory(new PropertyValueFactory<>("phone"));

        balanceColumn.setCellValueFactory(cellData -> {
            Patient p = cellData.getValue();
            // We need to load sessions to calculate balance for the table if we want it
            // displayed here
            // Or use the value from DB if DAO maps it. For now, let's load it.
            List<Session> sessions = sessionDAO.getSessionsByPatientId(p.getId());
            p.setSessions(sessions);
            return new SimpleStringProperty(String.format("%.2f DH", p.getBalance()));
        });

        filteredPatientList = new FilteredList<>(masterPatientList, p -> true);
        patientTable.setItems(filteredPatientList);

        // Double click to open detail
        patientTable.setRowFactory(tv -> {
            TableRow<Patient> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    openPatientDetailPopup(row.getItem());
                }
            });
            return row;
        });
    }

    private void setupSearch() {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredPatientList.setPredicate(patient -> {
                if (newVal == null || newVal.isEmpty())
                    return true;
                String lowerCaseFilter = newVal.toLowerCase();
                if (patient.getName().toLowerCase().contains(lowerCaseFilter))
                    return true;
                if (patient.getPhone() != null && patient.getPhone().contains(lowerCaseFilter))
                    return true;
                return false;
            });
        });
    }

    private void loadPatients() {
        masterPatientList.setAll(patientDAO.getAllPatients());
    }

    @FXML
    private void handleNewPatient() {
        showPatientDialog(null);
    }

    private void showPatientDialog(Patient patient) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pages/patient_form.fxml"));
            Parent root = loader.load();

            PatientFormController controller = loader.getController();
            controller.setPatient(patient);

            Stage stage = new Stage();
            stage.setTitle(patient == null ? "Ajouter un Patient" : "Modifier le Patient");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();

            if (controller.isSaveClicked()) {
                Patient p = controller.getPatient();
                if (patient == null) {
                    patientDAO.addPatient(p);
                } else {
                    patientDAO.updatePatient(p);
                }
                loadPatients();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void openPatientDetailPopup(Patient patient) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pages/patient_detail.fxml"));
            Parent root = loader.load();

            PatientDetailController controller = loader.getController();
            controller.setPatient(patient, this::loadPatients);

            Stage stage = new Stage();
            stage.setTitle("Profil de " + patient.getName());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();

            loadPatients(); // Refresh list after popup closes
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
