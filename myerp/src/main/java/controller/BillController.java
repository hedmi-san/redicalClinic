package controller;

import dao.BillDAO;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.Modality;
import javafx.stage.Stage;
import model.Bill;

import java.io.IOException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.Optional;
import java.util.ResourceBundle;

public class BillController implements Initializable {

    @FXML
    private TableView<Bill> billTable;
    @FXML
    private TableColumn<Bill, Integer> idColumn;
    @FXML
    private TableColumn<Bill, String> dateColumn;
    @FXML
    private TableColumn<Bill, Double> totalColumn;
    @FXML
    private TableColumn<Bill, Void> actionsColumn;
    
    @FXML
    private ComboBox<String> monthComboBox;
    @FXML
    private ComboBox<Integer> yearComboBox;

    private final String[] MONTHS_FR = {
            "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
            "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"
    };

    private final BillDAO billDAO = new BillDAO();
    private final ObservableList<Bill> billList = FXCollections.observableArrayList();
    private final DecimalFormat df = new DecimalFormat("#,##0.00 DZD");

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupFilters();
        setupTable();
        loadBills();
    }

    private void setupFilters() {
        monthComboBox.getItems().addAll(MONTHS_FR);
        int currentMonth = java.time.LocalDate.now().getMonthValue();
        monthComboBox.getSelectionModel().select(currentMonth - 1);

        int currentYear = java.time.LocalDate.now().getYear();
        for (int i = currentYear - 5; i <= currentYear + 5; i++) {
            yearComboBox.getItems().add(i);
        }
        yearComboBox.getSelectionModel().select(Integer.valueOf(currentYear));

        monthComboBox.setOnAction(e -> loadBills());
        yearComboBox.setOnAction(e -> loadBills());
    }

    private void setupTable() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("billDate"));

        totalColumn.setCellValueFactory(new PropertyValueFactory<>("totalCost"));
        totalColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(df.format(item));
                }
            }
        });

        setupActionsColumn();
    }

    private void setupActionsColumn() {
        actionsColumn.setCellFactory(param -> new TableCell<>() {
            private final Button editBtn = createIconButton(
                    "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z",
                    "btn-action-edit", "#0060b0");
            private final Button deleteBtn = createIconButton(
                    "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z",
                    "btn-action-delete", "#d32f2f");
            private final HBox container = new HBox(12, editBtn, deleteBtn);
            {
                container.setStyle("-fx-alignment: CENTER;");
                editBtn.setOnAction(event -> {
                    Bill b = getTableView().getItems().get(getIndex());
                    handleEditBill(b);
                });
                deleteBtn.setOnAction(event -> {
                    Bill b = getTableView().getItems().get(getIndex());
                    handleDeleteBill(b);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty)
                    setGraphic(null);
                else
                    setGraphic(container);
            }
        });
    }

    private Button createIconButton(String svgPath, String styleClass, String colorHex) {
        SVGPath icon = new SVGPath();
        icon.setContent(svgPath);
        icon.setFill(Color.web(colorHex));
        icon.setScaleX(0.8);
        icon.setScaleY(0.8);

        Button btn = new Button();
        btn.setGraphic(icon);
        btn.getStyleClass().add(styleClass);
        btn.setTooltip(new Tooltip(styleClass.equals("btn-action-edit") ? "Modifier" : "Supprimer"));
        return btn;
    }

    private void loadBills() {
        if (monthComboBox != null && yearComboBox != null) {
            int month = monthComboBox.getSelectionModel().getSelectedIndex() + 1;
            Integer year = yearComboBox.getValue();
            if (year != null) {
                billList.setAll(billDAO.getBillsByMonthAndYear(month, year));
            } else {
                billList.setAll(billDAO.getAllBills());
            }
        } else {
            billList.setAll(billDAO.getAllBills());
        }
        billTable.setItems(billList);
    }

    @FXML
    private void handleNewBill() {
        showBillDialog(null);
    }

    private void handleEditBill(Bill bill) {
        showBillDialog(bill);
    }

    private void handleDeleteBill(Bill bill) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmation de suppression");
        alert.setHeaderText("Supprimer la facture N°" + bill.getId() + " ?");
        alert.setContentText("Cette action supprimera également tous les articles de cette facture.");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            if (billDAO.deleteBill(bill.getId())) {
                loadBills();
            }
        }
    }

    private void showBillDialog(Bill bill) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pages/bill_form.fxml"));
            Parent root = loader.load();

            BillFormController controller = loader.getController();
            if (bill != null)
                controller.setBill(bill);

            Stage stage = new Stage();
            stage.setTitle(bill == null ? "Nouvelle Facture" : "Modifier Facture");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();

            loadBills(); // Refresh after dialog closes
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
