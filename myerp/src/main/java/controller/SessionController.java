package controller;

import dao.SessionDAO;
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
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import model.Session;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.ResourceBundle;

public class SessionController implements Initializable {

    @FXML
    private DatePicker datePicker;
    @FXML
    private TableView<Session> sessionTable;
    @FXML
    private TableColumn<Session, String> patientColumn;
    @FXML
    private TableColumn<Session, String> treatmentColumn;
    @FXML
    private TableColumn<Session, String> costColumn;
    @FXML
    private TableColumn<Session, String> paidColumn;
    @FXML
    private TableColumn<Session, String> statusColumn;
    @FXML
    private TableColumn<Session, String> balanceColumn;
    @FXML
    private Label dayTotalCostLabel;
    @FXML
    private Label dayTotalPaidLabel;

    private final SessionDAO sessionDAO = new SessionDAO();
    private final dao.TherapyPlanDAO therapyPlanDAO = new dao.TherapyPlanDAO();
    private ObservableList<Session> sessionList = FXCollections.observableArrayList();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        datePicker.setValue(LocalDate.now());
        setupTable();
        loadSessions();

        datePicker.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadSessions();
            }
        });
    }

    private void setupTable() {
        patientColumn.setCellValueFactory(cellData -> {
            String name = cellData.getValue().getPatientName();
            return new SimpleStringProperty(name != null ? name : "---");
        });
        treatmentColumn.setCellValueFactory(new PropertyValueFactory<>("treatment"));
        costColumn.setCellValueFactory(
                cellData -> new SimpleStringProperty(String.valueOf(cellData.getValue().getCost())));
        paidColumn.setCellValueFactory(
                cellData -> new SimpleStringProperty(String.valueOf(cellData.getValue().getPaidAmount())));

        statusColumn.setCellValueFactory(cellData -> {
            String status = cellData.getValue().getPaymentStatus();
            return new SimpleStringProperty(status);
        });

        balanceColumn.setCellValueFactory(cellData -> {
            double balance = cellData.getValue().getCost() - cellData.getValue().getPaidAmount();
            return new SimpleStringProperty(String.format("%.2f", balance));
        });

        // Add action buttons column if needed or use context menu
        sessionTable.setItems(sessionList);
    }

    private void loadSessions() {
        String date = datePicker.getValue().format(DATE_FORMATTER);
        sessionList.setAll(sessionDAO.getSessionsByDate(date));
        updateSummaryCards();
    }

    private void updateSummaryCards() {
        double totalCost = 0;
        double totalPaid = 0;
        for (Session s : sessionList) {
            // Only add normal sessions (where therapyPlanId is null or 0)
            if (s.getTherapyPlanId() == null || s.getTherapyPlanId() == 0) {
                totalCost += s.getCost();
                totalPaid += s.getPaidAmount();
            }
        }
        
        String date = datePicker.getValue().format(DATE_FORMATTER);
        double therapyPlanTotal = therapyPlanDAO.getTotalCostByDate(date);
        totalCost += therapyPlanTotal;
        totalPaid += therapyPlanTotal;
        
        dayTotalCostLabel.setText(String.format("%.2f DZD", totalCost));
        dayTotalPaidLabel.setText(String.format("%.2f DZD", totalPaid));
    }

    @FXML
    private void handleAddSession() {
        showSessionForm(null);
    }

    @FXML
    private void handleEditSession() {
        Session selectedSession = sessionTable.getSelectionModel().getSelectedItem();
        if (selectedSession != null) {
            showSessionForm(selectedSession);
        } else {
            showAlert("Aucun sélection", "Veuillez sélectionner une séance à modifier.");
        }
    }

    @FXML
    private void handleDeleteSession() {
        Session selectedSession = sessionTable.getSelectionModel().getSelectedItem();
        if (selectedSession != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Confirmation de suppression");
            alert.setHeaderText("Supprimer la séance ?");
            alert.setContentText("Êtes-vous sûr de vouloir supprimer cette séance ?");

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                if (sessionDAO.deleteSession(selectedSession.getId(), selectedSession.getPatientId())) {
                    loadSessions();
                }
            }
        } else {
            showAlert("Aucun sélection", "Veuillez sélectionner une séance à supprimer.");
        }
    }

    private void showSessionForm(Session session) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pages/session_form.fxml"));
            Parent root = loader.load();

            SessionFormController controller = loader.getController();
            controller.setSession(session, session != null ? session.getPatientId() : 0);

            Stage stage = new Stage();
            stage.setTitle(session == null ? "Nouvelle Séance" : "Modifier la Séance");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.UTILITY);
            stage.setScene(new Scene(root));

            stage.showAndWait();

            if (controller.isSaveClicked()) {
                Session updatedSession = controller.getSession();
                if (session == null) {
                    sessionDAO.addSession(updatedSession);
                } else {
                    sessionDAO.updateSession(updatedSession);
                }
                loadSessions();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
