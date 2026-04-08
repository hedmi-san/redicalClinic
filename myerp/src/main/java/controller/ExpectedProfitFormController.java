package controller;

import dao.SessionDAO;
import model.Session;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.ResourceBundle;

public class ExpectedProfitFormController implements Initializable {

    @FXML
    private DatePicker datePicker;
    @FXML
    private ComboBox<String> genderFilter;
    @FXML
    private Label totalLabel;
    @FXML
    private TableView<Session> resultTable;
    @FXML
    private TableColumn<Session, String> patientNameColumn;
    @FXML
    private TableColumn<Session, String> treatmentColumn;
    @FXML
    private TableColumn<Session, String> costColumn;

    private final SessionDAO sessionDAO = new SessionDAO();
    private final ObservableList<Session> sessionList = FXCollections.observableArrayList();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Setup gender filter
        genderFilter.setItems(FXCollections.observableArrayList("Tout", "Male", "Female"));
        genderFilter.getSelectionModel().selectFirst();

        // Setup table columns
        patientNameColumn.setCellValueFactory(new PropertyValueFactory<>("patientName"));
        treatmentColumn.setCellValueFactory(new PropertyValueFactory<>("treatment"));
        costColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(String.format("%.2f", cellData.getValue().getCost())));

        // Disable column reordering
        patientNameColumn.setReorderable(false);
        treatmentColumn.setReorderable(false);
        costColumn.setReorderable(false);

        resultTable.setItems(sessionList);

        // Set default date to today
        datePicker.setValue(LocalDate.now());

        // Add filter listeners
        datePicker.setOnAction(e -> loadData());
        genderFilter.setOnAction(e -> loadData());

        // Initial load
        loadData();
    }

    private void loadData() {
        sessionList.clear();

        LocalDate date = datePicker.getValue();
        if (date == null) return;

        String dateStr = date.toString(); // yyyy-MM-dd format
        String gender = genderFilter.getValue();

        List<Session> sessions = sessionDAO.getSessionsWithPatientInfo(dateStr, gender);
        sessionList.addAll(sessions);

        // Calculate total cost
        double total = sessions.stream().mapToDouble(Session::getCost).sum();
        totalLabel.setText(String.format("%,.2f DZD", total));
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) datePicker.getScene().getWindow();
        stage.close();
    }
}
