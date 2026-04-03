package controller;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import model.Sold;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class SoldFormController {

    @FXML private Label titleLabel;
    @FXML private TextField itemNameField;
    @FXML private DatePicker datePicker;
    @FXML private TextField priceField;
    @FXML private TextField quantityField;

    private Stage dialogStage;
    private Sold sold;
    private boolean saveClicked = false;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @FXML
    private void initialize() {
        datePicker.setValue(LocalDate.now());
    }

    public void setSold(Sold sold) {
        this.sold = sold;
        if (sold != null) {
            titleLabel.setText("Modifier la Vente");
            itemNameField.setText(sold.getItemName());
            if (sold.getSoldDate() != null && !sold.getSoldDate().isEmpty()) {
                datePicker.setValue(LocalDate.parse(sold.getSoldDate(), DATE_FORMATTER));
            }
            priceField.setText(String.valueOf(sold.getSoldPrice()));
            quantityField.setText(String.valueOf(sold.getQuantity()));
        } else {
            this.sold = new Sold();
        }
    }

    public boolean isSaveClicked() {
        return saveClicked;
    }

    public Sold getSold() {
        return sold;
    }

    @FXML
    private void handleSave() {
        if (isInputValid()) {
            sold.setItemName(itemNameField.getText());
            sold.setSoldDate(datePicker.getValue().format(DATE_FORMATTER));
            sold.setSoldPrice(Double.parseDouble(priceField.getText()));
            sold.setQuantity(Double.parseDouble(quantityField.getText()));

            saveClicked = true;
            closeDialog();
        }
    }

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    private void closeDialog() {
        dialogStage = (Stage) itemNameField.getScene().getWindow();
        dialogStage.close();
    }

    private boolean isInputValid() {
        String errorMessage = "";

        if (itemNameField.getText() == null || itemNameField.getText().isEmpty()) {
            errorMessage += "Nom d'article invalide!\n";
        }
        if (datePicker.getValue() == null) {
            errorMessage += "Date invalide!\n";
        }
        
        try {
            Double.parseDouble(priceField.getText());
        } catch (NumberFormatException e) {
            errorMessage += "Prix invalide (doit être un nombre)!\n";
        }

        try {
            Double.parseDouble(quantityField.getText());
        } catch (NumberFormatException e) {
            errorMessage += "Quantité invalide (doit être un nombre)!\n";
        }

        if (errorMessage.length() == 0) {
            return true;
        } else {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.initOwner(itemNameField.getScene().getWindow());
            alert.setTitle("Erreur de validation");
            alert.setHeaderText("Veuillez corriger les champs invalides");
            alert.setContentText(errorMessage);
            alert.showAndWait();
            return false;
        }
    }
}
