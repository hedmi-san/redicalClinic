package controller;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.stage.Stage;
import model.Sold;

import java.net.URL;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
// import java.util.ArrayList;
import java.util.List;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class SaleInvoiceFormController implements Initializable {

    @FXML
    private TextField clientNameField;
    @FXML
    private DatePicker invoiceDatePicker;
    @FXML
    private DatePicker startDatePicker;
    @FXML
    private DatePicker endDatePicker;
    @FXML
    private TextField searchField;
    @FXML
    private CheckBox selectAllCheckBox;
    @FXML
    private Label selectionSummaryLabel;

    @FXML
    private TableView<SoldSelection> itemsTable;
    @FXML
    private TableColumn<SoldSelection, Boolean> selectColumn;
    @FXML
    private TableColumn<SoldSelection, String> dateColumn;
    @FXML
    private TableColumn<SoldSelection, String> itemNameColumn;
    @FXML
    private TableColumn<SoldSelection, String> priceColumn;
    @FXML
    private TableColumn<SoldSelection, String> quantityColumn;
    @FXML
    private TableColumn<SoldSelection, String> totalColumn;

    @FXML
    private Button btnGenerate;

    private final ObservableList<SoldSelection> masterList = FXCollections.observableArrayList();
    private FilteredList<SoldSelection> filteredList;

    private boolean confirmed = false;

    private static final DecimalFormatSymbols DFS = new DecimalFormatSymbols(Locale.FRENCH);
    static {
        DFS.setGroupingSeparator(' ');
        DFS.setDecimalSeparator(',');
    }
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0.00", DFS);
    private static final DecimalFormat QTY_FORMAT = new DecimalFormat("#,##0", DFS);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DISPLAY_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        invoiceDatePicker.setValue(LocalDate.now());
        clientNameField.setText("Client au comptant");

        setupTable();
        setupFilters();
        updateSelectionSummary();
    }

    private void setupTable() {
        itemsTable.setEditable(true);

        // Checkbox column
        selectColumn.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        selectColumn.setCellFactory(CheckBoxTableCell.forTableColumn(selectColumn));

        // Data columns
        dateColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().getSold().getSoldDate() != null ? cellData.getValue().getSold().getSoldDate()
                        : ""));
        itemNameColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                cellData.getValue().getSold().getItemName() != null ? cellData.getValue().getSold().getItemName()
                        : ""));

        // Price
        priceColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                MONEY_FORMAT.format(cellData.getValue().getSold().getSoldPrice())));
        priceColumn.setCellFactory(col -> createAlignedCell(Pos.CENTER_RIGHT));

        // Quantity
        quantityColumn.setCellValueFactory(cellData -> {
            double qty = cellData.getValue().getSold().getQuantity();
            String formatted = (qty == Math.floor(qty)) ? QTY_FORMAT.format(qty) : MONEY_FORMAT.format(qty);
            return new SimpleStringProperty(formatted);
        });
        quantityColumn.setCellFactory(col -> createAlignedCell(Pos.CENTER_RIGHT));

        // Total
        totalColumn.setCellValueFactory(cellData -> {
            Sold s = cellData.getValue().getSold();
            double total = s.getSoldPrice() * s.getQuantity();
            return new SimpleStringProperty(MONEY_FORMAT.format(total));
        });
        totalColumn.setCellFactory(col -> createAlignedCell(Pos.CENTER_RIGHT));

        // Non-reorderable
        selectColumn.setReorderable(false);
        dateColumn.setReorderable(false);
        itemNameColumn.setReorderable(false);
        priceColumn.setReorderable(false);
        quantityColumn.setReorderable(false);
        totalColumn.setReorderable(false);

        filteredList = new FilteredList<>(masterList, p -> true);
        itemsTable.setItems(filteredList);

        // Select all toggle
        selectAllCheckBox.setOnAction(e -> {
            boolean selectAll = selectAllCheckBox.isSelected();
            for (SoldSelection ss : filteredList) {
                ss.setSelected(selectAll);
            }
            updateSelectionSummary();
        });
    }

    private <T> TableCell<SoldSelection, T> createAlignedCell(Pos alignment) {
        return new TableCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.toString());
                    setAlignment(alignment);
                }
            }
        };
    }

    private void setupFilters() {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilter());
        startDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> applyFilter());
        endDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> applyFilter());
    }

    private void applyFilter() {
        String search = (searchField.getText() != null) ? searchField.getText().trim().toLowerCase() : "";
        LocalDate start = startDatePicker.getValue();
        LocalDate end = endDatePicker.getValue();

        filteredList.setPredicate(selection -> {
            Sold sold = selection.getSold();

            // 1. Keyword filter
            if (!search.isEmpty()) {
                String itemName = (sold.getItemName() != null) ? sold.getItemName().toLowerCase() : "";
                if (!itemName.contains(search)) {
                    return false;
                }
            }

            // 2. Date range filter
            if (start != null || end != null) {
                String dateStr = sold.getSoldDate();
                if (dateStr == null || dateStr.trim().isEmpty()) {
                    return false;
                }
                try {
                    LocalDate itemDate = LocalDate.parse(dateStr.trim(), DATE_FORMATTER);
                    if (start != null && itemDate.isBefore(start)) {
                        return false;
                    }
                    if (end != null && itemDate.isAfter(end)) {
                        return false;
                    }
                } catch (Exception e) {
                    return false;
                }
            }

            return true;
        });

        // Re-check select all state
        updateSelectAllCheckboxState();
    }

    private void updateSelectAllCheckboxState() {
        if (filteredList.isEmpty()) {
            selectAllCheckBox.setSelected(false);
            return;
        }
        boolean allSelected = filteredList.stream().allMatch(SoldSelection::isSelected);
        selectAllCheckBox.setSelected(allSelected);
    }

    public void setSolds(List<Sold> solds) {
        masterList.clear();
        if (solds != null) {
            for (Sold sold : solds) {
                SoldSelection ss = new SoldSelection(sold);
                ss.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
                    updateSelectionSummary();
                    updateSelectAllCheckboxState();
                });
                masterList.add(ss);
            }
        }
        applyFilter();
        updateSelectionSummary();
    }

    private void updateSelectionSummary() {
        long count = masterList.stream().filter(SoldSelection::isSelected).count();
        double total = masterList.stream()
                .filter(SoldSelection::isSelected)
                .mapToDouble(s -> s.getSold().getSoldPrice() * s.getSold().getQuantity())
                .sum();

        selectionSummaryLabel
                .setText(count + " article(s) sélectionné(s) — Total: " + MONEY_FORMAT.format(total) + " DZD");
    }

    @FXML
    private void handleResetDateFilter() {
        startDatePicker.setValue(null);
        endDatePicker.setValue(null);
        searchField.clear();
        applyFilter();
    }

    @FXML
    private void handleGenerate() {
        List<Sold> selected = getSelectedSolds();
        if (selected.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Sélection vide");
            alert.setHeaderText(null);
            alert.setContentText("Veuillez sélectionner au moins un article pour générer la facture proforma.");
            alert.showAndWait();
            return;
        }

        confirmed = true;
        closeDialog();
    }

    @FXML
    private void handleCancel() {
        confirmed = false;
        closeDialog();
    }

    private void closeDialog() {
        Stage stage = (Stage) itemsTable.getScene().getWindow();
        stage.close();
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public List<Sold> getSelectedSolds() {
        return masterList.stream()
                .filter(SoldSelection::isSelected)
                .map(SoldSelection::getSold)
                .collect(Collectors.toList());
    }

    public String getClientName() {
        String name = clientNameField.getText();
        return (name != null && !name.trim().isEmpty()) ? name.trim() : "Client au comptant";
    }

    public String getInvoiceDateFormatted() {
        LocalDate date = invoiceDatePicker.getValue();
        if (date != null) {
            return date.format(DISPLAY_DATE_FORMATTER);
        }
        return LocalDate.now().format(DISPLAY_DATE_FORMATTER);
    }

    // Inner selection wrapper
    public static class SoldSelection {
        private final Sold sold;
        private final SimpleBooleanProperty selected = new SimpleBooleanProperty(false);

        public SoldSelection(Sold sold) {
            this.sold = sold;
        }

        public Sold getSold() {
            return sold;
        }

        public boolean isSelected() {
            return selected.get();
        }

        public void setSelected(boolean selected) {
            this.selected.set(selected);
        }

        public SimpleBooleanProperty selectedProperty() {
            return selected;
        }
    }
}
