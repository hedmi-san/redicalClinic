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
import model.Session;

import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class PatientSessionInvoiceFormController implements Initializable {

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
    private TableColumn<SessionSelection, String> paidColumn;
    @FXML
    private CheckBox selectAllCheckBox;

    private final ObservableList<SessionSelection> sessionSelections = FXCollections.observableArrayList();
    private boolean confirmed = false;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Checkbox column
        selectColumn.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
        selectColumn.setCellFactory(CheckBoxTableCell.forTableColumn(selectColumn));

        // Data columns
        dateColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getSession().getDate()));
        treatmentColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getSession().getTreatment()));
        costColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(String.format("%.2f", cellData.getValue().getSession().getCost())));
        paidColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(String.format("%.2f", cellData.getValue().getSession().getPaidAmount())));

        // Disable column reordering
        selectColumn.setReorderable(false);
        dateColumn.setReorderable(false);
        treatmentColumn.setReorderable(false);
        costColumn.setReorderable(false);
        paidColumn.setReorderable(false);

        sessionTable.setItems(sessionSelections);
        sessionTable.setEditable(true);

        // Select all toggle
        selectAllCheckBox.setOnAction(e -> {
            boolean selectAll = selectAllCheckBox.isSelected();
            for (SessionSelection ss : sessionSelections) {
                ss.setSelected(selectAll);
            }
        });
    }

    public void setSessions(List<Session> sessions) {
        sessionSelections.clear();
        for (Session session : sessions) {
            sessionSelections.add(new SessionSelection(session));
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
