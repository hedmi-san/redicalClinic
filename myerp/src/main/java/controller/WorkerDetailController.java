package controller;

import dao.PaymentCheckDAO;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import model.PaymentCheck;
import model.Worker;

import java.net.URL;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;

public class WorkerDetailController implements Initializable {

    @FXML
    private Label avatarLabel;
    @FXML
    private Label nameLabel;
    @FXML
    private Label functionLabel;
    @FXML
    private Label statusBadge;

    @FXML
    private Label detailName;
    @FXML
    private Label detailBirthDate;
    @FXML
    private Label detailBirthPlace;
    @FXML
    private Label detailPhone;
    @FXML
    private Label detailIdCard;
    @FXML
    private Label detailFamily;

    @FXML
    private Label monthlySalaryLabel;
    @FXML
    private Label totalEarningLabel;

    @FXML
    private ComboBox<String> monthSelector;
    @FXML
    private ComboBox<Integer> yearSelector;

    @FXML
    private TableView<PaymentCheck> paymentTable;
    @FXML
    private TableColumn<PaymentCheck, String> dateColumn;
    @FXML
    private TableColumn<PaymentCheck, String> noteColumn;
    @FXML
    private TableColumn<PaymentCheck, Double> amountColumn;
    @FXML
    private TableColumn<PaymentCheck, Void> actionsColumn;

    private Worker currentWorker;
    private final PaymentCheckDAO paymentDAO = new PaymentCheckDAO();
    private final DecimalFormat df = new DecimalFormat("#,##0.00 DZD");

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupTable();
        setupFilters();
    }

    private void setupFilters() {
        // Populate months
        for (Month month : Month.values()) {
            monthSelector.getItems()
                    .add(month.getDisplayName(TextStyle.FULL, Locale.FRENCH).substring(0, 1).toUpperCase() +
                            month.getDisplayName(TextStyle.FULL, Locale.FRENCH).substring(1));
        }

        // Populate years (current year - 5 to current year + 2)
        int currentYear = LocalDate.now().getYear();
        for (int i = currentYear - 5; i <= currentYear + 2; i++) {
            yearSelector.getItems().add(i);
        }

        // Set defaults
        monthSelector.getSelectionModel().select(LocalDate.now().getMonthValue() - 1);
        yearSelector.getSelectionModel().select(Integer.valueOf(currentYear));

        // Add listeners
        monthSelector.setOnAction(e -> loadPayments());
        yearSelector.setOnAction(e -> loadPayments());
    }

    public void setWorker(Worker worker) {
        this.currentWorker = worker;
        if (worker != null) {
            updateUI();
            loadPayments();
        }
    }

    private void setupTable() {
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("paymentDate"));
        noteColumn.setCellValueFactory(new PropertyValueFactory<>("note"));
        amountColumn.setCellValueFactory(new PropertyValueFactory<>("paidAmount"));

        // Custom cell factory for amount to format it and color code it
        amountColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Double amount, boolean empty) {
                super.updateItem(amount, empty);
                if (empty || amount == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(df.format(amount));
                    if (amount < 0) {
                        setTextFill(javafx.scene.paint.Color.RED);
                    } else {
                        setTextFill(javafx.scene.paint.Color.GREEN);
                    }
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
                    PaymentCheck p = getTableView().getItems().get(getIndex());
                    handleEditPayment(p);
                });
                deleteBtn.setOnAction(event -> {
                    PaymentCheck p = getTableView().getItems().get(getIndex());
                    handleDeletePayment(p);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(container);
                }
            }
        });
    }

    private Button createIconButton(String svgPath, String styleClass, String colorHex) {
        SVGPath icon = new SVGPath();
        icon.setContent(svgPath);
        icon.setFill(Color.web(colorHex));
        icon.setScaleX(0.85);
        icon.setScaleY(0.85);

        Button btn = new Button();
        btn.setGraphic(icon);
        btn.getStyleClass().add(styleClass);
        btn.setTooltip(new Tooltip(styleClass.equals("btn-action-edit") ? "Modifier" : "Supprimer"));
        return btn;
    }

    private void handleEditPayment(PaymentCheck payment) {
        Dialog<PaymentCheck> dialog = new Dialog<>();
        dialog.setTitle("Modifier le Paiement");
        dialog.setHeaderText("Modifiez les détails du paiement");

        ButtonType saveButtonType = new ButtonType("Enregistrer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        TextField amountField = new TextField(String.valueOf(payment.getPaidAmount()));
        TextField noteField = new TextField(payment.getNote());
        DatePicker datePicker = new DatePicker(LocalDate.parse(payment.getPaymentDate()));

        grid.add(new Label("Montant (DZD):"), 0, 0);
        grid.add(amountField, 1, 0);
        grid.add(new Label("Note:"), 0, 1);
        grid.add(noteField, 1, 1);
        grid.add(new Label("Date:"), 0, 2);
        grid.add(datePicker, 1, 2);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                try {
                    double amount = Double.parseDouble(amountField.getText());
                    String note = noteField.getText();
                    String date = datePicker.getValue().toString();
                    payment.setPaidAmount(amount);
                    payment.setNote(note);
                    payment.setPaymentDate(date);
                    return payment;
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return null;
        });

        Optional<PaymentCheck> result = dialog.showAndWait();
        result.ifPresent(p -> {
            if (paymentDAO.updatePayment(p)) {
                loadPayments();
            }
        });
    }

    private void updateUI() {
        if (currentWorker == null)
            return;

        String initials = "";
        String[] parts = currentWorker.getName().split(" ");
        for (String part : parts)
            if (!part.isEmpty())
                initials += part.charAt(0);
        avatarLabel.setText(initials.toUpperCase());

        nameLabel.setText(currentWorker.getName());
        functionLabel.setText(currentWorker.getFunction());

        detailName.setText(currentWorker.getName());
        detailBirthDate.setText(currentWorker.getBirthDate());
        detailBirthPlace.setText(currentWorker.getBirthPlace());
        detailPhone.setText(currentWorker.getPhoneNumber());
        detailIdCard.setText(currentWorker.getIdentityCardNumber());
        detailFamily.setText(currentWorker.getFamillySituation());

        // Monthly salary is not directly in Worker model, could be a constant or
        // fetched elsewhere
        // For now, let's keep it as Placeholder or fetch a default if known.
        monthlySalaryLabel.setText("- DZD");
    }

    private void loadPayments() {
        if (currentWorker == null || monthSelector.getValue() == null || yearSelector.getValue() == null)
            return;

        int month = monthSelector.getSelectionModel().getSelectedIndex() + 1;
        int year = yearSelector.getValue();

        ObservableList<PaymentCheck> payments = FXCollections.observableArrayList(
                paymentDAO.getPaymentsByWorkerAndMonth(currentWorker.getId(), month, year));
        paymentTable.setItems(payments);

        updateMetrics(month, year);
    }

    private void updateMetrics(int month, int year) {
        if (currentWorker == null)
            return;

        // Update Monthly Paid (using the selector)
        double monthlyTotal = paymentDAO.getMonthlyTotalByWorker(currentWorker.getId(), month, year);
        monthlySalaryLabel.setText(df.format(monthlyTotal));

        // Update Total Earning (global)
        double total = paymentDAO.getTotalEarningByWorkerId(currentWorker.getId());
        totalEarningLabel.setText(df.format(total));
    }

    @FXML
    private void handleAddPayment() {
        // Show a dialog to add payment.
        // For simplicity in this task, I'll use a simple TextInputDialog or similar if
        // I don't want to create a full FXML.
        // But the user might want a proper form. Let's start with a basic
        // implementation.

        Dialog<PaymentCheck> dialog = new Dialog<>();
        dialog.setTitle("Nouveau Paiement");
        dialog.setHeaderText("Entrez les détails du paiement");

        ButtonType saveButtonType = new ButtonType("Enregistrer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        TextField amountField = new TextField();
        amountField.setPromptText("Montant");
        TextField noteField = new TextField();
        noteField.setPromptText("Note");
        DatePicker datePicker = new DatePicker(java.time.LocalDate.now());

        grid.add(new Label("Montant (DZD):"), 0, 0);
        grid.add(amountField, 1, 0);
        grid.add(new Label("Note:"), 0, 1);
        grid.add(noteField, 1, 1);
        grid.add(new Label("Date:"), 0, 2);
        grid.add(datePicker, 1, 2);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == saveButtonType) {
                try {
                    double amount = Double.parseDouble(amountField.getText());
                    String note = noteField.getText();
                    String date = datePicker.getValue().toString();
                    return new PaymentCheck(0, currentWorker.getId(), date, amount, note);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return null;
        });

        Optional<PaymentCheck> result = dialog.showAndWait();
        result.ifPresent(p -> {
            if (paymentDAO.addPayment(p)) {
                loadPayments();
            }
        });
    }

    private void handleDeletePayment(PaymentCheck p) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmation de suppression");
        alert.setHeaderText("Supprimer ce paiement ?");
        alert.setContentText("Cette action est irréversible.");

        if (alert.showAndWait().get() == ButtonType.OK) {
            if (paymentDAO.deletePayment(p.getId())) {
                loadPayments();
            }
        }
    }
}
