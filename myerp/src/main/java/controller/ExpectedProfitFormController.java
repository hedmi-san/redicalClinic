package controller;

import dao.SessionDAO;
import dao.TherapyPlanDAO;
import model.Session;
import model.TherapyPlan;
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

    @FXML
    private TableView<TherapyPlan> therapyPlanTable;
    @FXML
    private TableColumn<TherapyPlan, String> tpPatientNameColumn;
    @FXML
    private TableColumn<TherapyPlan, String> tpCostColumn;

    private final SessionDAO sessionDAO = new SessionDAO();
    private final TherapyPlanDAO therapyPlanDAO = new TherapyPlanDAO();
    private final ObservableList<Session> sessionList = FXCollections.observableArrayList();
    private final ObservableList<TherapyPlan> planList = FXCollections.observableArrayList();

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

        // Setup therapy plan table columns
        tpPatientNameColumn.setCellValueFactory(new PropertyValueFactory<>("patientName"));
        tpCostColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(String.format("%.2f", cellData.getValue().getCost())));

        // Disable therapy column reordering
        tpPatientNameColumn.setReorderable(false);
        tpCostColumn.setReorderable(false);

        therapyPlanTable.setItems(planList);

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
        planList.clear();

        LocalDate date = datePicker.getValue();
        if (date == null) return;

        String dateStr = date.toString(); // yyyy-MM-dd format
        String gender = genderFilter.getValue();

        List<Session> sessions = sessionDAO.getSessionsWithPatientInfo(dateStr, gender);
        sessionList.addAll(sessions);

        List<TherapyPlan> plans = therapyPlanDAO.getTherapyPlansWithPatientInfo(dateStr, gender);
        planList.addAll(plans);

        // Calculate total cost
        double totalSessions = sessions.stream().mapToDouble(Session::getCost).sum();
        double totalPlans = plans.stream().mapToDouble(TherapyPlan::getCost).sum();
        totalLabel.setText(String.format("%,.2f DZD", totalSessions + totalPlans));
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) datePicker.getScene().getWindow();
        stage.close();
    }
}
