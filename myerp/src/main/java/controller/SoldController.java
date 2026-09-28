package controller;

import dao.SoldDAO;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import model.Sold;

import java.io.IOException;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;

public class SoldController implements Initializable {

    @FXML private ComboBox<String> monthComboBox;
    @FXML private ComboBox<Integer> yearComboBox;
    @FXML private TextField searchField;
    @FXML private Label soldCountLabel;
    @FXML private Label totalRevenueLabel;

    @FXML private VBox tableCard;
    @FXML private Button btnEdit;
    @FXML private Button btnDelete;

    @FXML private TableView<Sold> soldTable;
    @FXML private TableColumn<Sold, Integer> idColumn;
    @FXML private TableColumn<Sold, String> itemNameColumn;
    @FXML private TableColumn<Sold, String> dateColumn;
    @FXML private TableColumn<Sold, String> priceColumn;
    @FXML private TableColumn<Sold, String> quantityColumn;
    @FXML private TableColumn<Sold, String> totalColumn;

    private final SoldDAO soldDAO = new SoldDAO();
    private final ObservableList<Sold> soldList = FXCollections.observableArrayList();

    private final String[] MONTHS_FR = {
            "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
            "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"
    };

    private static final DecimalFormatSymbols DFS = new DecimalFormatSymbols(Locale.FRENCH);
    static {
        DFS.setGroupingSeparator(' ');
        DFS.setDecimalSeparator(',');
    }
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0.00", DFS);
    private static final DecimalFormat QTY_FORMAT = new DecimalFormat("#,##0", DFS);

    private final PauseTransition searchDebounce = new PauseTransition(Duration.millis(250));
    private final Label emptyStateTitle = new Label();
    private final Label emptyStateSubtitle = new Label("Cliquez sur « Nouvelle vente » pour en enregistrer une.");

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupCardClip();
        setupFilters();
        setupTable();
        setupActionButtons();
        setupEmptyState();
        loadSolds();
    }

    private void setupCardClip() {
        if (tableCard != null) {
            Rectangle clip = new Rectangle();
            clip.setArcWidth(24);
            clip.setArcHeight(24);
            clip.widthProperty().bind(tableCard.widthProperty());
            clip.heightProperty().bind(tableCard.heightProperty());
            tableCard.setClip(clip);
        }
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

        monthComboBox.setOnAction(e -> {
            updateEmptyState();
            loadSolds();
        });
        yearComboBox.setOnAction(e -> {
            updateEmptyState();
            loadSolds();
        });

        searchDebounce.setOnFinished(e -> loadSolds());
        searchField.textProperty().addListener((obs, oldVal, newVal) -> searchDebounce.playFromStart());
    }

    private void setupActionButtons() {
        if (btnEdit != null) {
            btnEdit.disableProperty().bind(soldTable.getSelectionModel().selectedItemProperty().isNull());
        }
        if (btnDelete != null) {
            btnDelete.disableProperty().bind(soldTable.getSelectionModel().selectedItemProperty().isNull());
        }
    }

    private void setupEmptyState() {
        VBox placeholder = new VBox(10);
        placeholder.setAlignment(Pos.CENTER);
        placeholder.getStyleClass().add("empty-state-box");

        SVGPath emptyIcon = new SVGPath();
        emptyIcon.setContent("M7 18c-1.1 0-1.99.9-1.99 2S5.9 22 7 22s2-.9 2-2-.9-2-2-2zM1 2v2h2l3.6 7.59-1.35 2.45c-.16.28-.25.61-.25.96 0 1.1.9 2 2 2h12v-2H7.42c-.14 0-.25-.11-.25-.25l.03-.12.9-1.63h7.45c.75 0 1.41-.41 1.75-1.03l3.58-6.49c.08-.14.12-.31.12-.48 0-.55-.45-1-1-1H5.21l-.94-2H1zm16 16c-1.1 0-1.99.9-1.99 2s.89 2 1.99 2 2-.9 2-2-.9-2-2-2z");
        emptyIcon.getStyleClass().add("empty-state-icon");

        emptyStateTitle.getStyleClass().add("empty-state-title");
        emptyStateSubtitle.getStyleClass().add("empty-state-subtitle");

        placeholder.getChildren().addAll(emptyIcon, emptyStateTitle, emptyStateSubtitle);
        soldTable.setPlaceholder(placeholder);
        updateEmptyState();
    }

    private void updateEmptyState() {
        String month = monthComboBox.getValue();
        Integer year = yearComboBox.getValue();
        String m = (month != null) ? month.toLowerCase() : "";
        String y = (year != null) ? String.valueOf(year) : "";
        emptyStateTitle.setText("Aucune vente en " + m + " " + y);
    }

    private void setupTable() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        idColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.valueOf(item));
                    setAlignment(Pos.CENTER);
                }
            }
        });

        itemNameColumn.setCellValueFactory(new PropertyValueFactory<>("itemName"));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("soldDate"));

        // Format and right-align Prix (DZD)
        makeHeaderRightAligned(priceColumn, "Prix (DZD)");
        priceColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(MONEY_FORMAT.format(cellData.getValue().getSoldPrice())));
        priceColumn.setCellFactory(col -> createRightAlignedCell());

        // Format and right-align Quantité
        makeHeaderRightAligned(quantityColumn, "Quantité");
        quantityColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(QTY_FORMAT.format(cellData.getValue().getQuantity())));
        quantityColumn.setCellFactory(col -> createRightAlignedCell());

        // Format and right-align Total (DZD)
        makeHeaderRightAligned(totalColumn, "Total (DZD)");
        totalColumn.setCellValueFactory(cellData -> {
            Sold s = cellData.getValue();
            double total = s.getSoldPrice() * s.getQuantity();
            return new SimpleStringProperty(MONEY_FORMAT.format(total));
        });
        totalColumn.setCellFactory(col -> createRightAlignedCell());

        soldTable.setItems(soldList);
    }

    private void makeHeaderRightAligned(TableColumn<?, ?> column, String title) {
        Label label = new Label(title);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setAlignment(Pos.CENTER_RIGHT);
        label.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: 600; -fx-font-size: 11px; -fx-text-fill: #64748b;");
        column.setText("");
        column.setGraphic(label);
    }

    private <T> TableCell<Sold, T> createRightAlignedCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.toString());
                    setAlignment(Pos.CENTER_RIGHT);
                }
            }
        };
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
        soldCountLabel.setText(QTY_FORMAT.format(summary.count()));
        totalRevenueLabel.setText(MONEY_FORMAT.format(summary.totalRevenue()));
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
            stage.setTitle(sold == null ? "Nouvelle vente" : "Modifier la vente");
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
