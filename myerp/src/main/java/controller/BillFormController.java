package controller;

import dao.BillDAO;
import dao.BillItemDAO;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import javafx.util.converter.DoubleStringConverter;
import model.Bill;
import model.BillItem;

import java.net.URL;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.ResourceBundle;

public class BillFormController implements Initializable {

    @FXML
    private DatePicker datePicker;
    @FXML
    private TableView<BillItem> itemTable;
    @FXML
    private TableColumn<BillItem, String> nameColumn;
    @FXML
    private TableColumn<BillItem, Double> quantityColumn;
    @FXML
    private TableColumn<BillItem, Double> priceColumn;
    @FXML
    private TableColumn<BillItem, Void> actionsColumn;
    @FXML
    private Label totalLabel;

    private final BillDAO billDAO = new BillDAO();
    private final BillItemDAO billItemDAO = new BillItemDAO();
    private final ObservableList<BillItem> itemList = FXCollections.observableArrayList();
    private final DecimalFormat df = new DecimalFormat("#,##0.00 DZD");
    private Bill currentBill;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        datePicker.setValue(LocalDate.now());
        setupTable();
    }

    public void setBill(Bill bill) {
        this.currentBill = bill;
        if (bill != null) {
            datePicker.setValue(LocalDate.parse(bill.getBillDate()));
            itemList.setAll(billItemDAO.getItemsByBillId(bill.getId()));
            updateTotal();
        }
    }

    private void setupTable() {
        itemTable.setItems(itemList);
        itemTable.setEditable(true);

        nameColumn.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        nameColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        nameColumn.setOnEditCommit(event -> {
            event.getTableView().getItems().get(event.getTablePosition().getRow()).setItemName(event.getNewValue());
        });

        quantityColumn.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        quantityColumn.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        quantityColumn.setOnEditCommit(event -> {
            event.getTableView().getItems().get(event.getTablePosition().getRow()).setQuantity(event.getNewValue());
            updateTotal();
        });

        priceColumn.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));
        priceColumn.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        priceColumn.setOnEditCommit(event -> {
            event.getTableView().getItems().get(event.getTablePosition().getRow()).setUnitPrice(event.getNewValue());
            updateTotal();
        });

        setupActionsColumn();
    }

    private void setupActionsColumn() {
        actionsColumn.setCellFactory(param -> new TableCell<>() {
            private final Button deleteBtn = createIconButton(
                    "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z",
                    "btn-action-delete", "#d32f2f");
            {
                deleteBtn.setOnAction(event -> {
                    itemList.remove(getIndex());
                    updateTotal();
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty)
                    setGraphic(null);
                else
                    setGraphic(deleteBtn);
            }
        });
    }

    private Button createIconButton(String svgPath, String styleClass, String colorHex) {
        SVGPath icon = new SVGPath();
        icon.setContent(svgPath);
        icon.setFill(Color.web(colorHex));
        icon.setScaleX(0.7);
        icon.setScaleY(0.7);

        Button btn = new Button();
        btn.setGraphic(icon);
        btn.getStyleClass().add(styleClass);
        return btn;
    }

    @FXML
    private void handleAddItem() {
        itemList.add(new BillItem(0, 0, "Nouvel article", 0, 1));
        updateTotal();
    }

    private void updateTotal() {
        double total = itemList.stream().mapToDouble(i -> i.getQuantity() * i.getUnitPrice()).sum();
        totalLabel.setText(df.format(total));
    }

    @FXML
    private void handleSave() {
        if (itemList.isEmpty()) {
            showAlert("Erreur", "Veuillez ajouter au moins un article à la facture.");
            return;
        }

        double total = itemList.stream().mapToDouble(i -> i.getQuantity() * i.getUnitPrice()).sum();
        String date = datePicker.getValue().toString();

        if (currentBill == null) {
            // New Bill
            Bill bill = new Bill(0, date, total);
            int billId = billDAO.addBill(bill);
            if (billId != -1) {
                for (BillItem item : itemList) {
                    item.setBillId(billId);
                    billItemDAO.addItem(item);
                }
                closeStage();
            }
        } else {
            // Edit Existing
            currentBill.setBillDate(date);
            currentBill.setTotalCost(total);
            if (billDAO.updateBillTotal(currentBill.getId(), total)) {
                // For simplicity, we delete and re-add items or update them
                // Here we'll delete and re-add to ensure the list is exactly as displayed
                billItemDAO.deleteItemsByBillId(currentBill.getId());
                for (BillItem item : itemList) {
                    item.setBillId(currentBill.getId());
                    billItemDAO.addItem(item);
                }
                closeStage();
            }
        }
    }

    @FXML
    private void handleCancel() {
        closeStage();
    }

    private void closeStage() {
        ((Stage) datePicker.getScene().getWindow()).close();
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
