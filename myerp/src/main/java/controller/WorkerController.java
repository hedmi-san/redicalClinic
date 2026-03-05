package controller;

import dao.WorkerDAO;
import model.Worker;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
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

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class WorkerController implements Initializable {

    @FXML
    private VBox mainContainer;
    @FXML
    private TextField searchField;
    @FXML
    private TableView<Worker> workerTable;
    @FXML
    private TableColumn<Worker, String> colName;
    @FXML
    private TableColumn<Worker, Integer> colAge;
    @FXML
    private TableColumn<Worker, String> colFunction;
    @FXML
    private TableColumn<Worker, String> colPhone;
    @FXML
    private Button btnAdd;
    @FXML
    private Button btnEdit;
    @FXML
    private Button btnDelete;

    private WorkerDAO workerDAO;
    private ObservableList<Worker> workerList;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Load CSS programmatically
        String cssPath = getClass().getResource("/css/pages/worker.css").toExternalForm();
        if (cssPath != null) {
            mainContainer.getStylesheets().add(cssPath);
        }

        workerDAO = new WorkerDAO();
        workerList = FXCollections.observableArrayList();

        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colAge.setCellValueFactory(new PropertyValueFactory<>("age"));
        colFunction.setCellValueFactory(new PropertyValueFactory<>("function"));
        colPhone.setCellValueFactory(new PropertyValueFactory<>("phoneNumber"));

        // Disable column reordering
        colName.setReorderable(false);
        colAge.setReorderable(false);
        colFunction.setReorderable(false);
        colPhone.setReorderable(false);

        workerTable.setItems(workerList);

        // Search listener
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            loadWorkers(newValue);
        });

        // Double click listener for profile
        workerTable.setOnMouseClicked((MouseEvent event) -> {
            if (event.getClickCount() == 2 && workerTable.getSelectionModel().getSelectedItem() != null) {
                openWorkerDetail(workerTable.getSelectionModel().getSelectedItem());
            }
        });

        loadWorkers("");
    }

    private void loadWorkers(String query) {
        workerList.clear();
        List<Worker> workers;
        if (query == null || query.trim().isEmpty()) {
            workers = workerDAO.getAllWorkers();
        } else {
            workers = workerDAO.searchWorkersByName(query.trim());
        }
        workerList.addAll(workers);
    }

    @FXML
    void handleAddAction(ActionEvent event) {
        openWorkerForm(null);
    }

    @FXML
    void handleEditAction(ActionEvent event) {
        Worker selectedWorker = workerTable.getSelectionModel().getSelectedItem();
        if (selectedWorker == null) {
            showAlert("Aucune sélection", "Veuillez sélectionner un employé à modifier.", Alert.AlertType.WARNING);
            return;
        }
        openWorkerForm(selectedWorker);
    }

    @FXML
    void handleDeleteAction(ActionEvent event) {
        Worker selectedWorker = workerTable.getSelectionModel().getSelectedItem();
        if (selectedWorker == null) {
            showAlert("Aucune sélection", "Veuillez sélectionner un employé à supprimer.", Alert.AlertType.WARNING);
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmer la suppression");
        alert.setHeaderText("Supprimer l'employé");
        alert.setContentText("Voulez-vous supprimer cet employé : " + selectedWorker.getName() + " ?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            boolean success = workerDAO.deleteWorker(selectedWorker.getId());
            if (success) {
                loadWorkers(searchField.getText());
            } else {
                showAlert("Erreur", "Échec de la suppression de l'employé dans la base de données.",
                        Alert.AlertType.ERROR);
            }
        }
    }

    private void openWorkerForm(Worker worker) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pages/worker_form.fxml"));
            Parent root = loader.load();

            WorkerFormController controller = loader.getController();
            controller.setWorkerDAO(workerDAO);
            controller.setWorkerData(worker);

            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.setTitle(worker == null ? "Ajouter un employé" : "Modifier l'employé");
            dialogStage.setScene(new Scene(root));

            dialogStage.showAndWait();

            if (controller.isSaveSuccessful()) {
                loadWorkers(searchField.getText());
            }

        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Erreur", "Échec de l'ouverture du formulaire.", Alert.AlertType.ERROR);
        }
    }

    private void openWorkerDetail(Worker worker) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pages/worker_detail.fxml"));
            Parent root = loader.load();

            // We can pass the worker object to the detail controller when it's fully
            // implemented.
            // WorkerDetailController controller = loader.getController();
            // controller.setWorker(worker);

            Stage dialogStage = new Stage();
            dialogStage.setTitle(worker.getName() + " - Profil");
            dialogStage.setScene(new Scene(root));
            dialogStage.show();

        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Erreur", "Échec de l'ouverture du profil de l'employé.", Alert.AlertType.ERROR);
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
