package ui;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import model.Patient;

import java.util.List;
import java.util.function.Consumer;

/**
 * Modern keyboard-navigable and space-insensitive search component for patients.
 * Shows instant candidate list with phone and color-coded balance badges.
 */
public class PatientSearchSelector extends VBox {

    private final TextField searchField = new TextField();
    private final ListView<Patient> candidateList = new ListView<>();
    private final HBox selectedPatientCard = new HBox(12);
    private final Label lblSelectedName = new Label();
    private final Label lblSelectedPhone = new Label();
    private final Label lblSelectedBalance = new Label();
    private final Button btnClearSelection = new Button("Changer ✕");

    private final ObservableList<Patient> allPatients = FXCollections.observableArrayList();
    private final FilteredList<Patient> filteredPatients = new FilteredList<>(allPatients, p -> true);

    private final ObjectProperty<Patient> selectedPatient = new SimpleObjectProperty<>(null);
    private Consumer<Patient> onPatientSelected;

    public PatientSearchSelector() {
        setSpacing(6);
        setFillWidth(true);
        setupUI();
        setupListeners();
    }

    private void setupUI() {
        searchField.setPromptText("🔍 Rechercher par nom ou numéro de téléphone...");
        searchField.setStyle("-fx-font-size: 13px; -fx-padding: 8 12; -fx-background-radius: 6; " +
                             "-fx-border-color: #cbd5e1; -fx-border-radius: 6; -fx-border-width: 1;");

        candidateList.setItems(filteredPatients);
        candidateList.setPrefHeight(150);
        candidateList.setMaxHeight(180);
        candidateList.setVisible(false);
        candidateList.setManaged(false);
        candidateList.setCellFactory(lv -> new PatientCandidateCell());
        candidateList.setStyle("-fx-background-radius: 6; -fx-border-color: #cbd5e1; -fx-border-radius: 6;");

        // Selected Patient Card layout
        selectedPatientCard.setAlignment(Pos.CENTER_LEFT);
        selectedPatientCard.setPadding(new Insets(10, 14, 10, 14));
        selectedPatientCard.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #0d9488; -fx-border-width: 1.5; " +
                                    "-fx-border-radius: 8; -fx-background-radius: 8;");
        selectedPatientCard.setVisible(false);
        selectedPatientCard.setManaged(false);

        lblSelectedName.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");
        lblSelectedPhone.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");

        VBox patientInfo = new VBox(2, lblSelectedName, lblSelectedPhone);
        HBox.setHgrow(patientInfo, Priority.ALWAYS);

        lblSelectedBalance.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 4 10; -fx-background-radius: 12;");

        btnClearSelection.setStyle("-fx-background-color: transparent; -fx-text-fill: #dc2626; -fx-font-size: 12px; " +
                                   "-fx-cursor: hand; -fx-font-weight: bold; -fx-padding: 4 8;");
        btnClearSelection.setOnAction(e -> clearSelection());

        selectedPatientCard.getChildren().addAll(patientInfo, lblSelectedBalance, btnClearSelection);

        getChildren().addAll(searchField, candidateList, selectedPatientCard);
    }

    private void setupListeners() {
        // Filter-as-you-type (space-insensitive)
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.trim().isEmpty()) {
                filteredPatients.setPredicate(p -> true);
                candidateList.setVisible(false);
                candidateList.setManaged(false);
            } else {
                String cleanQuery = newVal.replaceAll("\\s+", "").toLowerCase();
                filteredPatients.setPredicate(patient -> {
                    if (patient == null) return false;
                    String name = patient.getName() != null ? patient.getName().toLowerCase() : "";
                    String phone = patient.getPhone() != null ? patient.getPhone().replaceAll("\\s+", "") : "";
                    return name.contains(cleanQuery) || phone.contains(cleanQuery);
                });
                boolean hasCandidates = !filteredPatients.isEmpty();
                candidateList.setVisible(hasCandidates);
                candidateList.setManaged(hasCandidates);
            }
        });

        // SearchField Keyboard Navigation
        searchField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DOWN) {
                if (candidateList.isVisible() && !candidateList.getItems().isEmpty()) {
                    candidateList.requestFocus();
                    candidateList.getSelectionModel().select(0);
                }
            } else if (event.getCode() == KeyCode.ENTER) {
                if (!filteredPatients.isEmpty()) {
                    selectPatient(filteredPatients.get(0));
                }
            } else if (event.getCode() == KeyCode.ESCAPE) {
                candidateList.setVisible(false);
                candidateList.setManaged(false);
            }
        });

        // CandidateList Keyboard & Click Selection
        candidateList.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                Patient sel = candidateList.getSelectionModel().getSelectedItem();
                if (sel != null) {
                    selectPatient(sel);
                }
            } else if (event.getCode() == KeyCode.ESCAPE) {
                candidateList.setVisible(false);
                candidateList.setManaged(false);
                searchField.requestFocus();
            } else if (event.getCode() == KeyCode.UP && candidateList.getSelectionModel().getSelectedIndex() == 0) {
                searchField.requestFocus();
            }
        });

        candidateList.setOnMouseClicked(event -> {
            Patient sel = candidateList.getSelectionModel().getSelectedItem();
            if (sel != null) {
                selectPatient(sel);
            }
        });
    }

    public void setPatients(List<Patient> patients) {
        allPatients.setAll(patients != null ? patients : List.of());
    }

    public void selectPatient(Patient patient) {
        selectedPatient.set(patient);
        if (patient != null) {
            lblSelectedName.setText(patient.getName());
            lblSelectedPhone.setText(patient.getPhone() != null && !patient.getPhone().isEmpty() ? "📞 " + patient.getPhone() : "Aucun téléphone");

            double balance = patient.getBalance();
            if (balance < -0.01) {
                lblSelectedBalance.setText(String.format("🟢 Avance: +%,.2f DA", Math.abs(balance)));
                lblSelectedBalance.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #166534; -fx-font-weight: bold; -fx-padding: 4 10; -fx-background-radius: 12;");
            } else if (balance > 0.01) {
                lblSelectedBalance.setText(String.format("🔴 Reste dû: %,.2f DA", balance));
                lblSelectedBalance.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-font-weight: bold; -fx-padding: 4 10; -fx-background-radius: 12;");
            } else {
                lblSelectedBalance.setText("⚪ À jour (0 DA)");
                lblSelectedBalance.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #475569; -fx-font-weight: bold; -fx-padding: 4 10; -fx-background-radius: 12;");
            }

            searchField.setVisible(false);
            searchField.setManaged(false);
            candidateList.setVisible(false);
            candidateList.setManaged(false);

            selectedPatientCard.setVisible(true);
            selectedPatientCard.setManaged(true);

            if (onPatientSelected != null) {
                onPatientSelected.accept(patient);
            }
        } else {
            clearSelection();
        }
    }

    public void clearSelection() {
        selectedPatient.set(null);
        searchField.clear();
        searchField.setVisible(true);
        searchField.setManaged(true);
        selectedPatientCard.setVisible(false);
        selectedPatientCard.setManaged(false);
        candidateList.setVisible(false);
        candidateList.setManaged(false);
        Platform.runLater(searchField::requestFocus);

        if (onPatientSelected != null) {
            onPatientSelected.accept(null);
        }
    }

    public Patient getSelectedPatient() {
        return selectedPatient.get();
    }

    public ObjectProperty<Patient> selectedPatientProperty() {
        return selectedPatient;
    }

    public void setOnPatientSelected(Consumer<Patient> onPatientSelected) {
        this.onPatientSelected = onPatientSelected;
    }

    /**
     * Custom ListCell rendering Name, Phone, and colored balance badge.
     */
    private static class PatientCandidateCell extends ListCell<Patient> {
        @Override
        protected void updateItem(Patient patient, boolean empty) {
            super.updateItem(patient, empty);
            if (empty || patient == null) {
                setText(null);
                setGraphic(null);
            } else {
                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(4, 6, 4, 6));

                Label name = new Label(patient.getName());
                name.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #0f172a;");

                Label phone = new Label(patient.getPhone() != null && !patient.getPhone().isEmpty() ? "• " + patient.getPhone() : "");
                phone.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");

                HBox infoBox = new HBox(6, name, phone);
                infoBox.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(infoBox, Priority.ALWAYS);

                Label badge = new Label();
                double balance = patient.getBalance();
                if (balance < -0.01) {
                    badge.setText(String.format("+%,.2f DA", Math.abs(balance)));
                    badge.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #166534; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 2 8; -fx-background-radius: 10;");
                } else if (balance > 0.01) {
                    badge.setText(String.format("Dû %,.2f DA", balance));
                    badge.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 2 8; -fx-background-radius: 10;");
                } else {
                    badge.setText("0 DA");
                    badge.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #64748b; -fx-font-size: 11px; -fx-padding: 2 8; -fx-background-radius: 10;");
                }

                row.getChildren().addAll(infoBox, badge);
                setGraphic(row);
                setText(null);
            }
        }
    }
}
