package controller;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.stage.Stage;
import model.Patient;
import model.Session;

import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class PatientSessionInvoiceFormController implements Initializable {

    @FXML
    private Label patientNameLabel;
    @FXML
    private Label patientBalanceBadge;
    @FXML
    private TableView<SessionSelection> sessionTable;
    @FXML
    private TableColumn<SessionSelection, Boolean> selectColumn;
    @FXML
    private TableColumn<SessionSelection, String> dateColumn;
    @FXML
    private TableColumn<SessionSelection, String> treatmentColumn;
    @FXML
    private TableColumn<SessionSelection, String> costColumn;
    @FXML
    private TableColumn<SessionSelection, String> statusColumn;
    @FXML
    private CheckBox selectAllCheckBox;
    @FXML
    private Label selectionSummaryLabel;
    @FXML
    private Button btnOk;

    private Patient patient;
    private final ObservableList<SessionSelection> sessionSelections = FXCollections.observableArrayList();
    private boolean confirmed = false;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Checkbox column
        selectColumn.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        selectColumn.setCellFactory(CheckBoxTableCell.forTableColumn(selectColumn));

        // Data columns
        dateColumn
                .setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getSession().getDate()));
        treatmentColumn.setCellValueFactory(
                cellData -> new SimpleStringProperty(cellData.getValue().getSession().getTreatment()));
        costColumn.setCellValueFactory(cellData -> new SimpleStringProperty(
                String.format(Locale.FRENCH, "%,.2f", cellData.getValue().getSession().getCost())));

        // Derived coverage status badge
        statusColumn.setCellValueFactory(cellData -> {
            Session s = cellData.getValue().getSession();
            double balance = patient != null ? patient.getBalance() : 0.0;
            String status;
            if (s.getPaidAmount() >= s.getCost() && s.getCost() > 0) {
                status = "RÉGLÉ";
            } else if (s.getPaidAmount() > 0) {
                status = String.format(Locale.FRENCH, "PARTIEL (%,.0f DA)", s.getPaidAmount());
            } else if (balance <= 0.01) {
                status = "COUVERT (AVANCE)";
            } else {
                status = "IMPAYÉ";
            }
            return new SimpleStringProperty(status);
        });

        statusColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(item);
                    badge.getStyleClass().add("badge");
                    if (item.startsWith("RÉGLÉ") || item.startsWith("COUVERT")) {
                        badge.getStyleClass().add("badge-full");
                    } else if (item.startsWith("PARTIEL")) {
                        badge.getStyleClass().add("badge-partial");
                    } else {
                        badge.getStyleClass().add("badge-none");
                    }
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        // Disable column reordering
        selectColumn.setReorderable(false);
        dateColumn.setReorderable(false);
        treatmentColumn.setReorderable(false);
        costColumn.setReorderable(false);
        statusColumn.setReorderable(false);

        sessionTable.setItems(sessionSelections);
        sessionTable.setEditable(true);

        // Select all toggle
        selectAllCheckBox.setOnAction(e -> {
            boolean selectAll = selectAllCheckBox.isSelected();
            for (SessionSelection ss : sessionSelections) {
                ss.setSelected(selectAll);
            }
            updateSelectionSummary();
        });

        updateSelectionSummary();
    }

    public void setPatientAndSessions(Patient patient, List<Session> sessions) {
        this.patient = patient;
        updatePatientHeader();
        setSessions(sessions);
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
        updatePatientHeader();
        sessionTable.refresh();
    }

    public void setSessions(List<Session> sessions) {
        sessionSelections.clear();
        for (Session session : sessions) {
            SessionSelection ss = new SessionSelection(session);
            ss.selectedProperty().addListener((obs, oldVal, newVal) -> updateSelectionSummary());
            sessionSelections.add(ss);
        }
        updateSelectionSummary();
    }

    private void updatePatientHeader() {
        if (patient == null)
            return;
        if (patientNameLabel != null) {
            patientNameLabel.setText(patient.getName() != null ? patient.getName() : "N/A");
        }
        if (patientBalanceBadge != null) {
            double balance = patient.getBalance();
            patientBalanceBadge.getStyleClass().setAll("badge");
            if (balance < -0.01) {
                patientBalanceBadge.setText(String.format(Locale.FRENCH, "Avance: %,.2f DZD", Math.abs(balance)));
                patientBalanceBadge.getStyleClass().add("badge-full");
            } else if (balance > 0.01) {
                patientBalanceBadge.setText(String.format(Locale.FRENCH, "Reste dû: %,.2f DZD", balance));
                patientBalanceBadge.getStyleClass().add("badge-none");
            } else {
                patientBalanceBadge.setText("Compte soldé (0,00 DZD)");
                patientBalanceBadge.getStyleClass().add("badge-full");
            }
        }
    }

    private void updateSelectionSummary() {
        long count = sessionSelections.stream().filter(SessionSelection::isSelected).count();
        double sum = sessionSelections.stream()
                .filter(SessionSelection::isSelected)
                .mapToDouble(ss -> ss.getSession().getCost())
                .sum();

        if (selectionSummaryLabel != null) {
            selectionSummaryLabel.setText(String.format(Locale.FRENCH,
                    "%d séance(s) sélectionnée(s) — Total : %,.2f DZD", count, sum));
        }
    }

    public List<Session> getSelectedSessions() {
        return sessionSelections.stream()
                .filter(SessionSelection::isSelected)
                .map(SessionSelection::getSession)
                .collect(Collectors.toList());
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    @FXML
    private void handleOk() {
        confirmed = true;
        closeDialog();
    }

    @FXML
    private void handleCancel() {
        confirmed = false;
        closeDialog();
    }

    private void closeDialog() {
        Stage stage = (Stage) sessionTable.getScene().getWindow();
        stage.close();
    }

    // Inner wrapper class to add a selected property to each session
    public static class SessionSelection {
        private final Session session;
        private final SimpleBooleanProperty selected = new SimpleBooleanProperty(false);

        public SessionSelection(Session session) {
            this.session = session;
        }

        public Session getSession() {
            return session;
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
