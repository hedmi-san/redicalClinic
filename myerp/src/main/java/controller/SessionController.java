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

public class SessionController implements Initializable, Refreshable {

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

    private final dao.PaymentDAO paymentDAO = new dao.PaymentDAO();
    private final dao.PatientDAO patientDAO = new dao.PatientDAO();

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

    @Override
    public void refresh() {
        loadSessions();
    }

    private void setupTable() {
        patientColumn.setCellValueFactory(cellData -> {
            String name = cellData.getValue().getPatientName();
            return new SimpleStringProperty(name != null ? name : "---");
        });
        treatmentColumn.setCellValueFactory(new PropertyValueFactory<>("treatment"));
        costColumn.setCellValueFactory(
                cellData -> new SimpleStringProperty(String.format("%.2f", cellData.getValue().getCost())));
        paidColumn.setCellValueFactory(
                cellData -> new SimpleStringProperty(String.format("%.2f", cellData.getValue().getPaidAmount())));

        statusColumn.setCellValueFactory(cellData -> {
            Session s = cellData.getValue();
            if (s.getPaidAmount() >= s.getCost() && s.getCost() > 0) {
                return new SimpleStringProperty("RÉGLÉ");
            } else if (s.getPaidAmount() > 0) {
                return new SimpleStringProperty("PARTIEL");
            } else {
                // If 0 cash paid today, check if covered by patient's balance
                model.Patient p = patientDAO.getPatientById(s.getPatientId());
                if (p != null && p.getBalance() <= 0.01) {
                    return new SimpleStringProperty("COUVERT (AVANCE)");
                } else {
                    return new SimpleStringProperty("IMPAYÉ");
                }
            }
        });

        balanceColumn.setCellValueFactory(cellData -> {
            Session s = cellData.getValue();
            model.Patient p = patientDAO.getPatientById(s.getPatientId());
            double bal = p != null ? p.getBalance() : (s.getCost() - s.getPaidAmount());
            if (bal < -0.01) {
                return new SimpleStringProperty(String.format("+%.2f (Avance)", Math.abs(bal)));
            } else if (bal > 0.01) {
                return new SimpleStringProperty(String.format("%.2f (Dû)", bal));
            } else {
                return new SimpleStringProperty("0.00 (À jour)");
            }
        });

        sessionTable.setItems(sessionList);
    }

    private void loadSessions() {
        String date = datePicker.getValue().format(DATE_FORMATTER);
        sessionList.setAll(sessionDAO.getSessionsByDate(date));
        updateSummaryCards();
    }

    private void updateSummaryCards() {
        double totalCost = 0;
        for (Session s : sessionList) {
            // Only add normal sessions (where therapyPlanId is null or 0)
            if (s.getTherapyPlanId() == null || s.getTherapyPlanId() == 0) {
                totalCost += s.getCost();
            }
        }
        
        String date = datePicker.getValue().format(DATE_FORMATTER);
        double therapyPlanTotal = therapyPlanDAO.getTotalCostByDate(date);
        totalCost += therapyPlanTotal;
        
        // Exact physical cash in drawer on this business date from payment ledger
        dao.PaymentDAO.DailyCashRegister cashRegister = paymentDAO.getDailyCashRegister(date);
        double actualCashInDrawer = cashRegister.netCash();

        dayTotalCostLabel.setText(String.format("%.2f DZD", totalCost));
        dayTotalPaidLabel.setText(String.format("%.2f DZD", actualCashInDrawer));
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

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.TRANSPARENT);
            Scene scene = new Scene(root);
            scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
            stage.setScene(scene);
            stage.setResizable(false);

            controller.setSession(session, session != null ? session.getPatientId() : 0);
            controller.initStage(stage);

            stage.sizeToScene();
            stage.showAndWait();

            if (controller.isSaveClicked()) {
                Session updatedSession = controller.getSession();
                double cashReceived = controller.getCashReceived();
                String username = util.UserSession.isLoggedIn() ? util.UserSession.getCurrentUser().getUserName() : "Employé";
                if (session == null) {
                    sessionDAO.addSession(updatedSession, cashReceived, username);
                } else {
                    sessionDAO.updateSession(updatedSession, cashReceived, username);
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
