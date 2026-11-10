package controller;

import dao.PatientDAO;
import model.BalanceFilter;
import model.Patient;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleObjectProperty;
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
import javafx.util.StringConverter;

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
    private ComboBox<BalanceFilter> balanceFilterCombo;
    @FXML
    private Label patientCountLabel;
    @FXML
    private TableView<Patient> patientTable;
    @FXML
    private TableColumn<Patient, String> nameColumn;
    @FXML
    private TableColumn<Patient, String> phoneColumn;
    @FXML
    private TableColumn<Patient, Double> balanceColumn;

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

        // Numerical value factory for precise sorting by balance
        balanceColumn.setCellValueFactory(cellData -> {
            Patient p = cellData.getValue();
            return new SimpleObjectProperty<>(p != null ? p.getBalance() : 0.0);
        });

        // Visual badge rendering in balance column
        balanceColumn.setCellFactory(col -> new TableCell<Patient, Double>() {
            @Override
            protected void updateItem(Double balance, boolean empty) {
                super.updateItem(balance, empty);
                if (empty || balance == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label();
                    badge.getStyleClass().add("badge");
                    if (balance > 0.01) {
                        badge.setText(String.format("Dû : %,.2f DZD", balance));
                        badge.getStyleClass().add("balance-badge-debt");
                    } else if (balance < -0.01) {
                        badge.setText(String.format("Avance : %,.2f DZD", Math.abs(balance)));
                        badge.getStyleClass().add("balance-badge-advance");
                    } else {
                        badge.setText("0.00 DZD (Réglé)");
                        badge.getStyleClass().add("balance-badge-settled");
                    }
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        // Disable column reordering for consistency
        nameColumn.setReorderable(false);
        phoneColumn.setReorderable(false);
        balanceColumn.setReorderable(false);

        patientTable.setItems(patientList);

        // Setup balance filter combo box
        if (balanceFilterCombo != null) {
            balanceFilterCombo.setItems(FXCollections.observableArrayList(BalanceFilter.values()));
            balanceFilterCombo.setValue(BalanceFilter.ALL);
            balanceFilterCombo.setConverter(new StringConverter<BalanceFilter>() {
                @Override
                public String toString(BalanceFilter filter) {
                    return filter != null ? filter.getDisplayName() : "";
                }

                @Override
                public BalanceFilter fromString(String string) {
                    return BalanceFilter.fromDisplayName(string);
                }
            });
            balanceFilterCombo.valueProperty().addListener((observable, oldValue, newValue) -> {
                loadPatients();
            });
        }

        // Search listener with 200ms debounce to avoid hammering DB on fast typing
        searchDebounce.setOnFinished(e -> loadPatients());
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            searchDebounce.playFromStart();
        });

        // Double click listener for profile popup
        patientTable.setOnMouseClicked((MouseEvent event) -> {
            if (event.getClickCount() == 2 && patientTable.getSelectionModel().getSelectedItem() != null) {
                openPatientDetailPopup(patientTable.getSelectionModel().getSelectedItem());
            }
        });

        loadPatients();
    }

    @Override
    public void refresh() {
        loadPatients();
    }

    public void loadPatients() {
        loadPatients(searchField != null ? searchField.getText() : "");
    }

    public void loadPatients(String query) {
        BalanceFilter filter = (balanceFilterCombo != null && balanceFilterCombo.getValue() != null)
                ? balanceFilterCombo.getValue()
                : BalanceFilter.ALL;

        patientList.clear();
        List<Patient> patients = patientDAO.getPatients(query, filter);
        patientList.addAll(patients);

        if (patientCountLabel != null) {
            int count = patientList.size();
            patientCountLabel.setText(count + (count > 1 ? " patients" : " patient"));
        }
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

    public ComboBox<BalanceFilter> getBalanceFilterCombo() {
        return balanceFilterCombo;
    }

    public void setBalanceFilter(BalanceFilter filter) {
        if (balanceFilterCombo != null) {
            balanceFilterCombo.setValue(filter);
        }
    }

    public ObservableList<Patient> getPatientList() {
        return patientList;
    }

    public Label getPatientCountLabel() {
        return patientCountLabel;
    }

    public TextField getSearchField() {
        return searchField;
    }
}
