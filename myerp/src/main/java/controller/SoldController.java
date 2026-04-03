package controller;

import dao.SoldDAO;
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
import model.Sold;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.util.Optional;
import java.util.ResourceBundle;

public class SoldController implements Initializable {

    @FXML private ComboBox<String> monthComboBox;
    @FXML private ComboBox<Integer> yearComboBox;
    @FXML private TextField searchField;
    @FXML private Label soldCountLabel;
    @FXML private Label totalRevenueLabel;

    @FXML private TableView<Sold> soldTable;
    @FXML private TableColumn<Sold, Integer> idColumn;
    @FXML private TableColumn<Sold, String> itemNameColumn;
    @FXML private TableColumn<Sold, String> dateColumn;
    @FXML private TableColumn<Sold, String> priceColumn;
    @FXML private TableColumn<Sold, String> quantityColumn;
    @FXML private TableColumn<Sold, String> totalColumn;

    private final SoldDAO soldDAO = new SoldDAO();
    private ObservableList<Sold> soldList = FXCollections.observableArrayList();

    private final String[] MONTHS_FR = {
            "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
            "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"
    };

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupFilters();
        setupTable();
        loadSolds();
    }

    private void setupFilters() {
        monthComboBox.getItems().addAll(MONTHS_FR);
        int currentMonth = LocalDate.now().getMonthValue();
        monthComboBox.getSelectionModel().select(currentMonth - 1);

        int currentYear = LocalDate.now().getYear();
        for (int i = currentYear - 5; i <= currentYear + 5; i++) {
            yearComboBox.getItems().add(i);
        }
        yearComboBox.getSelectionModel().select(Integer.valueOf(currentYear));

        monthComboBox.setOnAction(e -> loadSolds());
        yearComboBox.setOnAction(e -> loadSolds());
    }

    private void setupTable() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        itemNameColumn.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("soldDate"));
        
        priceColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(String.format("%.2f", cellData.getValue().getSoldPrice())));
            
        quantityColumn.setCellValueFactory(cellData -> 
            new SimpleStringProperty(String.valueOf(cellData.getValue().getQuantity())));

        totalColumn.setCellValueFactory(cellData -> {
            Sold s = cellData.getValue();
            double total = s.getSoldPrice() * s.getQuantity();
            return new SimpleStringProperty(String.format("%.2f", total));
        });

        soldTable.setItems(soldList);
    }

    private void loadSolds() {
        int month = monthComboBox.getSelectionModel().getSelectedIndex() + 1;
        Integer year = yearComboBox.getValue();
        String search = searchField.getText();

        if (year != null) {
            soldList.setAll(soldDAO.getSoldsByMonthYearAndSearch(month, year, search));
            updateSummaryCards(month, year);
        }
    }

    private void updateSummaryCards(int month, int year) {
        SoldDAO.MonthlySummary summary = soldDAO.getMonthlySummary(month, year);
        soldCountLabel.setText(String.valueOf(summary.count()));
        totalRevenueLabel.setText(String.format("%.2f DZD", summary.totalRevenue()));
    }

    @FXML
    private void handleSearch() {
        loadSolds();
    }

    @FXML
    private void handleNewSold() {
        showSoldForm(null);
    }

    @FXML
    private void handleEditSold() {
        Sold selectedSold = soldTable.getSelectionModel().getSelectedItem();
        if (selectedSold != null) {
            showSoldForm(selectedSold);
        } else {
            showAlert("Aucune sélection", "Veuillez sélectionner une vente à modifier.");
        }
    }

    @FXML
    private void handleDeleteSold() {
        Sold selectedSold = soldTable.getSelectionModel().getSelectedItem();
        if (selectedSold != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Confirmation de suppression");
            alert.setHeaderText("Supprimer la vente ?");
            alert.setContentText("Êtes-vous sûr de vouloir supprimer cette vente : " + selectedSold.getItemName() + " ?");

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                if (soldDAO.deleteSold(selectedSold.getId())) {
                    loadSolds();
                }
            }
        } else {
            showAlert("Aucune sélection", "Veuillez sélectionner une vente à supprimer.");
        }
    }

    private void showSoldForm(Sold sold) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pages/sold_form.fxml"));
            Parent root = loader.load();

            SoldFormController controller = loader.getController();
            controller.setSold(sold);

            Stage stage = new Stage();
            stage.setTitle(sold == null ? "Nouvelle Vente" : "Modifier la Vente");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.UTILITY);
            stage.setScene(new Scene(root));

            stage.showAndWait();

            if (controller.isSaveClicked()) {
                Sold updatedSold = controller.getSold();
                if (sold == null) {
                    soldDAO.addSold(updatedSold);
                } else {
                    soldDAO.updateSold(updatedSold);
                }
                loadSolds();
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
