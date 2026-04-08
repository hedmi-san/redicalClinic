package controller;

import dao.BillDAO;
import dao.PaymentCheckDAO;
import dao.SessionDAO;
import dao.SoldDAO;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.util.ResourceBundle;
import java.util.stream.IntStream;

public class DashboardController implements Initializable {

    @FXML
    private ComboBox<String> monthSelector;
    @FXML
    private ComboBox<Integer> yearSelector;

    @FXML
    private Label sessionCountLabel;
    @FXML
    private Label expectedProfitLabel;
    @FXML
    private Label actualProfitLabel;
    @FXML
    private Label balanceLabel;
    @FXML
    private Label workerPaymentsLabel;
    @FXML
    private Label billsLabel;
    @FXML
    private Label salesLabel;
    @FXML
    private VBox expectedProfitCard;
    @FXML
    private VBox actualProfitCard;

    private final SessionDAO sessionDAO = new SessionDAO();
    private final BillDAO billDAO = new BillDAO();
    private final PaymentCheckDAO paymentCheckDAO = new PaymentCheckDAO();
    private final SoldDAO soldDAO = new SoldDAO();

    private final String[] months = {
            "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
            "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"
    };

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupSelectors();
        setupCardClickHandlers();
        loadDashboardData();
    }

    private void setupSelectors() {
        monthSelector.setItems(FXCollections.observableArrayList(months));

        int currentYear = LocalDate.now().getYear();
        yearSelector.setItems(FXCollections.observableArrayList(
                IntStream.rangeClosed(currentYear - 5, currentYear + 2).boxed().toList()));

        // Set current month/year
        monthSelector.getSelectionModel().select(LocalDate.now().getMonthValue() - 1);
        yearSelector.getSelectionModel().select(Integer.valueOf(currentYear));

        // Add listeners
        monthSelector.setOnAction(e -> loadDashboardData());
        yearSelector.setOnAction(e -> loadDashboardData());
    }

    private void loadDashboardData() {
        int month = monthSelector.getSelectionModel().getSelectedIndex() + 1;
        Integer year = yearSelector.getValue();

        if (year == null)
            return;

        // Fetch data from DAOs
        SessionDAO.MonthlySummary sessionSummary = sessionDAO.getMonthlySummary(month, year);
        double workerPayments = paymentCheckDAO.getMonthlyTotalForAllWorkers(month, year);
        double billTotal = billDAO.getMonthlyTotal(month, year);
        SoldDAO.MonthlySummary soldSummary = soldDAO.getMonthlySummary(month, year);

        // Update UI
        sessionCountLabel.setText(String.valueOf(sessionSummary.count()));
        expectedProfitLabel.setText(String.format("%,.2f", sessionSummary.totalCost()));
        actualProfitLabel.setText(String.format("%,.2f", sessionSummary.totalPaid()));

        double balance = sessionSummary.totalCost() - sessionSummary.totalPaid();
        balanceLabel.setText(String.format("%,.2f", balance));

        workerPaymentsLabel.setText(String.format("%,.2f", workerPayments));
        billsLabel.setText(String.format("%,.2f", billTotal));
        salesLabel.setText(String.format("%,.2f", soldSummary.totalRevenue()));

        // Color balancing - optionally highlight if balance is high or profit is low
        if (balance > 0) {
            balanceLabel.setStyle("-fx-text-fill: #f59e0b;"); // Orange warning
        } else {
            balanceLabel.setStyle("-fx-text-fill: #14b8a6;"); // Teal healthy
        }
    }

    private void setupCardClickHandlers() {
        expectedProfitCard.setOnMouseClicked((MouseEvent event) -> {
            if (event.getClickCount() == 2) {
                openProfitDialog("/fxml/pages/expected_profit_form.fxml", "Profit Attendu - Détails");
            }
        });

        actualProfitCard.setOnMouseClicked((MouseEvent event) -> {
            if (event.getClickCount() == 2) {
                openProfitDialog("/fxml/pages/actual_profit_form.fxml", "Profit Réalisé - Détails");
            }
        });
    }

    private void openProfitDialog(String fxmlPath, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.setTitle(title);
            dialogStage.setScene(new Scene(root));
            dialogStage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
