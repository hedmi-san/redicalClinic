package controller;

import dao.PatientDAO;
import model.Patient;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class PatientController implements Initializable, Refreshable {

    @FXML
    private VBox mainContainer;
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

    private PatientDAO patientDAO;
    private ObservableList<Patient> patientList;
    private final PauseTransition searchDebounce = new PauseTransition(Duration.millis(200));

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Load CSS programmatically like WorkerController
        String cssPath = getClass().getResource("/css/pages/patient.css").toExternalForm();
        if (cssPath != null) {
            mainContainer.getStylesheets().add(cssPath);
        }

        patientDAO = new PatientDAO();
        patientList = FXCollections.observableArrayList();

        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        phoneColumn.setCellValueFactory(new PropertyValueFactory<>("phone"));

        // Instant in-memory balance display without firing per-row network SQL queries
        balanceColumn.setCellValueFactory(cellData -> {
            Patient p = cellData.getValue();
            return new SimpleStringProperty(String.format("%.2f DZD", p != null ? p.getBalance() : 0.0));
        });

        // Disable column reordering for consistency
        nameColumn.setReorderable(false);
        phoneColumn.setReorderable(false);
        balanceColumn.setReorderable(false);

        patientTable.setItems(patientList);

        // Search listener with 200ms debounce to avoid hammering DB on fast typing
        searchDebounce.setOnFinished(e -> loadPatients(searchField.getText()));
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            searchDebounce.playFromStart();
        });

        // Double click listener for profile popup
        patientTable.setOnMouseClicked((MouseEvent event) -> {
            if (event.getClickCount() == 2 && patientTable.getSelectionModel().getSelectedItem() != null) {
                openPatientDetailPopup(patientTable.getSelectionModel().getSelectedItem());
            }
        });

        loadPatients("");
    }

    @Override
    public void refresh() {
        loadPatients(searchField != null ? searchField.getText() : "");
    }

    private void loadPatients(String query) {
        patientList.clear();
        List<Patient> patients;
        if (query == null || query.trim().isEmpty()) {
            patients = patientDAO.getAllPatients();
        } else {
            patients = patientDAO.searchPatients(query.trim());
        }
        patientList.addAll(patients);
    }

    @FXML
    private void handleNewPatient() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pages/patient_form.fxml"));
            Parent root = loader.load();

            PatientFormController controller = loader.getController();
            controller.setPatient(null);

            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.setTitle("Ajouter un Patient");
            dialogStage.setScene(new Scene(root));

            dialogStage.showAndWait();

            if (controller.isSaveClicked()) {
                patientDAO.addPatient(controller.getPatient());
                loadPatients(searchField.getText());
            }

        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Erreur", "Échec de l'ouverture du formulaire.", Alert.AlertType.ERROR);
        }
    }

    private void openPatientDetailPopup(Patient patient) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pages/patient_detail.fxml"));
            Parent root = loader.load();

            PatientDetailController controller = loader.getController();
            controller.setPatient(patient, () -> loadPatients(searchField.getText()));

            Stage stage = new Stage();
            stage.setTitle("Profil de " + patient.getName());
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();

            loadPatients(searchField.getText());
        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Erreur", "Échec de l'ouverture du profil.", Alert.AlertType.ERROR);
        }
    }

    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
