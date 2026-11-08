package controller;

import dao.AppointmentDAO;
import dao.ClinicSettingsDAO;
import dao.PatientDAO;
import dao.WorkerDAO;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;
import model.Appointment;
import model.Patient;
import model.Worker;
import util.UserSession;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class AppointmentFormController {

    @FXML
    private StackPane modalRootPane;
    @FXML
    private VBox cardRoot;
    @FXML
    private HBox headerBar;
    @FXML
    private Label titleLabel;
    @FXML
    private Label subtitleLabel;
    @FXML
    private Button closeBtn;

    @FXML
    private ToggleButton toggleRegistered;
    @FXML
    private ToggleButton toggleWalkIn;
    @FXML
    private Button btnQuickNewPatient;

    @FXML
    private VBox registeredPatientBox;
    @FXML
    private StackPane patientFieldContainer;
    @FXML
    private TextField patientSearchField;
    @FXML
    private Button btnClearPatient;

    @FXML
    private GridPane walkInGuestBox;
    @FXML
    private TextField guestNameField;
    @FXML
    private TextField guestPhoneField;

    @FXML
    private Label patientErrorLabel;

    @FXML
    private DatePicker datePicker;
    @FXML
    private ComboBox<String> startTimeCombo;
    @FXML
    private ComboBox<String> durationCombo;
    @FXML
    private ComboBox<Worker> workerCombo;
    @FXML
    private ComboBox<String> statusCombo;
    @FXML
    private TextArea motifArea;

    @FXML
    private HBox conflictWarningBanner;
    @FXML
    private Label conflictWarningLabel;

    @FXML
    private Button cancelBtn;
    @FXML
    private Button saveBtn;

    private Stage dialogStage;
    private double xOffset = 0;
    private double yOffset = 0;

    private Appointment appointment;
    private Patient selectedPatient;
    private boolean saveClicked = false;
    private boolean isEditMode = false;

    private final PatientDAO patientDAO = new PatientDAO();
    private final WorkerDAO workerDAO = new WorkerDAO();
    private final AppointmentDAO appointmentDAO = new AppointmentDAO();
    private final ClinicSettingsDAO settingsDAO = new ClinicSettingsDAO();

    private final ObservableList<Patient> allPatients = FXCollections.observableArrayList();
    private final ObservableList<Patient> filteredPatients = FXCollections.observableArrayList();

    private final Popup suggestionPopup = new Popup();
    private final ListView<Patient> suggestionListView = new ListView<>();
    private final PauseTransition searchDebounce = new PauseTransition(Duration.millis(200));

    private String currentSearchQuery = "";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    @FXML
    public void initialize() {
        ToggleGroup modeGroup = new ToggleGroup();
        toggleRegistered.setToggleGroup(modeGroup);
        toggleWalkIn.setToggleGroup(modeGroup);

        modeGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null) {
                if (oldToggle != null) {
                    oldToggle.setSelected(true);
                }
                return;
            }
            boolean walkIn = (newToggle == toggleWalkIn);
            updatePatientInputMode(walkIn);
        });

        setupSuggestionPopup();
        setupInputControls();
        setupListeners();
        updateSaveButtonState();
        updateLiveConflictBanner();
    }

    public void initStage(Stage stage) {
        this.dialogStage = stage;

        if (headerBar != null) {
            headerBar.setOnMousePressed(e -> {
                xOffset = e.getSceneX();
                yOffset = e.getSceneY();
            });
            headerBar.setOnMouseDragged(e -> {
                stage.setX(e.getScreenX() - xOffset);
                stage.setY(e.getScreenY() - yOffset);
            });
        }

        if (stage.getScene() != null) {
            stage.getScene().setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    if (suggestionPopup.isShowing()) {
                        suggestionPopup.hide();
                    } else {
                        closeStage();
                    }
                }
            });
        }

        if (cancelBtn != null) {
            cancelBtn.setCancelButton(true);
        }
        if (saveBtn != null) {
            saveBtn.setDefaultButton(true);
        }
    }

    private void updatePatientInputMode(boolean walkIn) {
        suggestionPopup.hide();
        registeredPatientBox.setVisible(!walkIn);
        registeredPatientBox.setManaged(!walkIn);
        walkInGuestBox.setVisible(walkIn);
        walkInGuestBox.setManaged(walkIn);

        if (walkIn && selectedPatient == null && guestNameField.getText().trim().isEmpty()
                && !patientSearchField.getText().trim().isEmpty()) {
            guestNameField.setText(patientSearchField.getText().trim());
        }
        patientErrorLabel.setVisible(false);
        patientErrorLabel.setManaged(false);
        updateSaveButtonState();
    }

    private void setupSuggestionPopup() {
        suggestionPopup.setAutoHide(true);
        suggestionListView.getStyleClass().add("patient-popup-list");
        suggestionListView.setFixedCellSize(48);
        suggestionListView.setItems(filteredPatients);
        suggestionListView.setCellFactory(lv -> new PatientListCell());

        suggestionListView.setOnMouseClicked(event -> {
            Patient p = suggestionListView.getSelectionModel().getSelectedItem();
            if (p != null) {
                commitPatientSelection(p);
            }
        });

        suggestionListView.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                Patient p = suggestionListView.getSelectionModel().getSelectedItem();
                if (p != null) {
                    commitPatientSelection(p);
                }
            } else if (event.getCode() == KeyCode.ESCAPE) {
                suggestionPopup.hide();
                patientSearchField.requestFocus();
            }
        });

        suggestionPopup.getContent().add(suggestionListView);
    }

    private void setupInputControls() {
        allPatients.setAll(patientDAO.getAllPatients());
        datePicker.setValue(LocalDate.now());

        // Populate time slots based on clinic opening/closing hours in 15-min increments
        LocalTime openTime = settingsDAO.getStartTime();
        LocalTime closeTime = settingsDAO.getEndTime();
        List<String> timeSlots = new ArrayList<>();
        LocalTime cursor = openTime.isBefore(LocalTime.of(7, 0)) ? openTime : LocalTime.of(8, 0);
        LocalTime limit = closeTime.isAfter(LocalTime.of(19, 0)) ? closeTime : LocalTime.of(19, 0);
        while (!cursor.isAfter(limit)) {
            timeSlots.add(cursor.format(TIME_FORMATTER));
            cursor = cursor.plusMinutes(15);
        }
        startTimeCombo.setItems(FXCollections.observableArrayList(timeSlots));
        startTimeCombo.setValue(openTime.format(TIME_FORMATTER));

        // Duration options
        int defaultDur = settingsDAO.getDefaultDurationMin();
        durationCombo.setItems(FXCollections.observableArrayList("15", "20", "30", "45", "60", "90", "120"));
        durationCombo.setValue(String.valueOf(defaultDur));

        // Workers (optional practitioner)
        List<Worker> workers = new ArrayList<>();
        workers.add(null); // Represents "Aucun / Cabinet général"
        workers.addAll(workerDAO.getAllWorkers());
        workerCombo.setItems(FXCollections.observableArrayList(workers));
        workerCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Worker w) {
                if (w == null) return "— Aucun praticien spécifié —";
                return w.getName() + (w.getFunction() != null && !w.getFunction().isEmpty() ? " (" + w.getFunction() + ")" : "");
            }

            @Override
            public Worker fromString(String s) {
                return null;
            }
        });
        workerCombo.getSelectionModel().selectFirst();

        // Status options
        statusCombo.setItems(FXCollections.observableArrayList(
                Appointment.STATUS_PREVU,
                Appointment.STATUS_CONFIRME,
                Appointment.STATUS_TERMINE,
                Appointment.STATUS_ANNULE,
                Appointment.STATUS_ABSENT
        ));
        statusCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(String status) {
                return Appointment.formatStatusLabel(status);
            }

            @Override
            public String fromString(String s) {
                return s;
            }
        });
        statusCombo.setValue(Appointment.STATUS_PREVU);
    }

    private void setupListeners() {
        patientSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (selectedPatient != null && newVal != null && newVal.trim().equalsIgnoreCase(selectedPatient.getName().trim())) {
                searchDebounce.stop();
                suggestionPopup.hide();
                return;
            }
            if (selectedPatient != null && (newVal == null || !newVal.trim().equalsIgnoreCase(selectedPatient.getName().trim()))) {
                selectedPatient = null;
                btnClearPatient.setVisible(false);
                updateSaveButtonState();
            }

            searchDebounce.setOnFinished(e -> performPatientFilter(newVal));
            searchDebounce.playFromStart();
        });

        patientSearchField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DOWN) {
                if (!suggestionPopup.isShowing() && !filteredPatients.isEmpty()) {
                    showSuggestionPopup();
                }
                if (!filteredPatients.isEmpty()) {
                    suggestionListView.requestFocus();
                    suggestionListView.getSelectionModel().select(0);
                }
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE && suggestionPopup.isShowing()) {
                suggestionPopup.hide();
                event.consume();
            }
        });

        patientSearchField.focusedProperty().addListener((obs, oldVal, isFocused) -> {
            if (!isFocused && !suggestionListView.isFocused()) {
                suggestionPopup.hide();
            }
        });

        guestNameField.textProperty().addListener((obs, o, n) -> updateSaveButtonState());
        guestPhoneField.textProperty().addListener((obs, o, n) -> updateSaveButtonState());

        datePicker.valueProperty().addListener((obs, o, n) -> {
            updateSaveButtonState();
            updateLiveConflictBanner();
        });
        startTimeCombo.valueProperty().addListener((obs, o, n) -> {
            updateSaveButtonState();
            updateLiveConflictBanner();
        });
        startTimeCombo.getEditor().textProperty().addListener((obs, o, n) -> {
            updateSaveButtonState();
            updateLiveConflictBanner();
        });
        durationCombo.valueProperty().addListener((obs, o, n) -> {
            updateSaveButtonState();
            updateLiveConflictBanner();
        });
        durationCombo.getEditor().textProperty().addListener((obs, o, n) -> {
            updateSaveButtonState();
            updateLiveConflictBanner();
        });
        workerCombo.valueProperty().addListener((obs, o, n) -> updateLiveConflictBanner());
        statusCombo.valueProperty().addListener((obs, o, n) -> updateLiveConflictBanner());
    }

    private void performPatientFilter(String query) {
        this.currentSearchQuery = query != null ? query.trim() : "";
        if (currentSearchQuery.isEmpty()) {
            suggestionPopup.hide();
            return;
        }

        String cleanQuery = currentSearchQuery.replaceAll("\\s+", "").toLowerCase();
        List<Patient> matches = allPatients.stream().filter(p -> {
            if (p == null) return false;
            String name = p.getName() != null ? p.getName().toLowerCase() : "";
            String phone = p.getPhone() != null ? p.getPhone().replaceAll("\\s+", "") : "";
            return name.contains(cleanQuery) || phone.contains(cleanQuery);
        }).toList();

        filteredPatients.setAll(matches);
        suggestionListView.setPrefWidth(patientSearchField.getWidth());

        if (matches.isEmpty()) {
            suggestionListView.setPrefHeight(44);
        } else {
            int visibleCount = Math.min(matches.size(), 5);
            suggestionListView.setPrefHeight(visibleCount * 48 + 4);
        }
        showSuggestionPopup();
    }

    private void showSuggestionPopup() {
        if (patientSearchField.getScene() == null || patientSearchField.getScene().getWindow() == null) {
            return;
        }
        Point2D point = patientSearchField.localToScreen(0, patientSearchField.getHeight() + 4);
        if (point != null) {
            if (!suggestionPopup.isShowing()) {
                suggestionPopup.show(patientSearchField.getScene().getWindow(), point.getX(), point.getY());
            } else {
                suggestionPopup.setX(point.getX());
                suggestionPopup.setY(point.getY());
            }
        }
    }

    private void commitPatientSelection(Patient patient) {
        this.selectedPatient = patient;
        searchDebounce.stop();
        suggestionPopup.hide();
        patientSearchField.setText(patient.getName());
        btnClearPatient.setVisible(true);
        patientErrorLabel.setVisible(false);
        patientErrorLabel.setManaged(false);
        updateSaveButtonState();
    }

    @FXML
    private void handleClearPatient() {
        this.selectedPatient = null;
        patientSearchField.clear();
        btnClearPatient.setVisible(false);
        suggestionPopup.hide();
        updateSaveButtonState();
        patientSearchField.requestFocus();
    }

    @FXML
    private void handleQuickCreatePatient() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pages/patient_form.fxml"));
            Parent root = loader.load();

            PatientFormController controller = loader.getController();
            String initialName = toggleWalkIn.isSelected() ? guestNameField.getText() : patientSearchField.getText();
            String initialPhone = toggleWalkIn.isSelected() ? guestPhoneField.getText() : "";
            controller.setWalkInPrefill(initialName, initialPhone);

            Stage stage = new Stage();
            stage.setTitle("Nouveau Patient");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();

            if (controller.isSaveClicked()) {
                Patient newPatient = controller.getPatient();
                if (patientDAO.addPatient(newPatient)) {
                    allPatients.setAll(patientDAO.getAllPatients());
                    toggleRegistered.setSelected(true);
                    updatePatientInputMode(false);
                    commitPatientSelection(newPatient);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setAppointment(Appointment appointment, Integer preselectedPatientId, LocalDate defaultDate) {
        this.appointment = appointment;
        this.isEditMode = (appointment != null && appointment.getId() > 0);

        if (defaultDate != null) {
            datePicker.setValue(defaultDate);
        }

        if (isEditMode) {
            titleLabel.setText("Modifier le Rendez-vous");
            subtitleLabel.setText("Déplacer, confirmer ou modifier les détails du créneau");
            saveBtn.setText("Mettre à jour");

            if (appointment.getDate() != null) {
                try {
                    datePicker.setValue(LocalDate.parse(appointment.getDate(), DATE_FORMATTER));
                } catch (Exception ignored) {
                }
            }
            if (appointment.getStartTime() != null) {
                startTimeCombo.setValue(appointment.getStartTime());
                startTimeCombo.getEditor().setText(appointment.getStartTime());
            }
            durationCombo.setValue(String.valueOf(appointment.getDurationMin()));
            durationCombo.getEditor().setText(String.valueOf(appointment.getDurationMin()));
            motifArea.setText(appointment.getMotif() != null ? appointment.getMotif() : "");
            statusCombo.setValue(appointment.getStatus() != null ? appointment.getStatus() : Appointment.STATUS_PREVU);

            if (appointment.getWorkerId() != null && appointment.getWorkerId() > 0) {
                for (Worker w : workerCombo.getItems()) {
                    if (w != null && w.getId() == appointment.getWorkerId()) {
                        workerCombo.setValue(w);
                        break;
                    }
                }
            } else {
                workerCombo.getSelectionModel().selectFirst();
            }

            if (!appointment.isWalkIn()) {
                toggleRegistered.setSelected(true);
                updatePatientInputMode(false);
                Patient p = patientDAO.getPatientById(appointment.getPatientId());
                if (p != null) {
                    commitPatientSelection(p);
                }
            } else {
                toggleWalkIn.setSelected(true);
                updatePatientInputMode(true);
                guestNameField.setText(appointment.getGuestName() != null ? appointment.getGuestName() : "");
                guestPhoneField.setText(appointment.getGuestPhone() != null ? appointment.getGuestPhone() : "");
            }
        } else {
            titleLabel.setText("Nouveau Rendez-vous");
            subtitleLabel.setText("Planifier un créneau pour un patient inscrit ou par téléphone");
            saveBtn.setText("Enregistrer");

            if (preselectedPatientId != null && preselectedPatientId > 0) {
                toggleRegistered.setSelected(true);
                updatePatientInputMode(false);
                Patient p = patientDAO.getPatientById(preselectedPatientId);
                if (p != null) {
                    commitPatientSelection(p);
                }
            }
        }

        searchDebounce.stop();
        suggestionPopup.hide();
        updateSaveButtonState();
        updateLiveConflictBanner();
    }

    private String resolveStartTimeInput() {
        String raw = startTimeCombo.getEditor() != null && !startTimeCombo.getEditor().getText().trim().isEmpty()
                ? startTimeCombo.getEditor().getText().trim()
                : startTimeCombo.getValue();
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String s = raw.trim();
        // Normalize H:mm to HH:mm
        if (s.matches("^\\d:\\d{2}$")) {
            s = "0" + s;
        } else if (s.matches("^\\d{1,2}$")) {
            int hr = Integer.parseInt(s);
            if (hr >= 0 && hr <= 23) {
                s = String.format("%02d:00", hr);
            }
        }
        try {
            LocalTime parsed = LocalTime.parse(s, TIME_FORMATTER);
            return parsed.format(TIME_FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }

    private int resolveDurationInput() {
        String raw = durationCombo.getEditor() != null && !durationCombo.getEditor().getText().trim().isEmpty()
                ? durationCombo.getEditor().getText().trim()
                : durationCombo.getValue();
        if (raw == null || raw.trim().isEmpty()) {
            return settingsDAO.getDefaultDurationMin();
        }
        try {
            int val = Integer.parseInt(raw.replaceAll("\\D+", ""));
            return val > 0 ? val : settingsDAO.getDefaultDurationMin();
        } catch (NumberFormatException e) {
            return settingsDAO.getDefaultDurationMin();
        }
    }

    private List<Appointment> checkCurrentConflicts() {
        if (datePicker.getValue() == null) {
            return List.of();
        }
        String status = statusCombo.getValue();
        if (Appointment.STATUS_ANNULE.equalsIgnoreCase(status) || Appointment.STATUS_ABSENT.equalsIgnoreCase(status)) {
            return List.of();
        }
        String timeStr = resolveStartTimeInput();
        if (timeStr == null) {
            return List.of();
        }
        int dur = resolveDurationInput();
        Worker selectedWorker = workerCombo.getValue();
        Integer wid = selectedWorker != null ? selectedWorker.getId() : null;
        Integer pid = (!toggleWalkIn.isSelected() && selectedPatient != null) ? selectedPatient.getId() : null;
        int excludeId = (isEditMode && appointment != null) ? appointment.getId() : -1;

        return appointmentDAO.findOverlappingAppointments(
                datePicker.getValue().format(DATE_FORMATTER),
                timeStr,
                dur,
                wid,
                pid,
                excludeId
        );
    }

    private void updateLiveConflictBanner() {
        if (conflictWarningBanner == null || conflictWarningLabel == null) {
            return;
        }
        List<Appointment> conflicts = checkCurrentConflicts();
        if (conflicts.isEmpty()) {
            conflictWarningBanner.setVisible(false);
            conflictWarningBanner.setManaged(false);
        } else {
            Worker selectedWorker = workerCombo.getValue();
            Integer wid = selectedWorker != null ? selectedWorker.getId() : null;
            Integer pid = (!toggleWalkIn.isSelected() && selectedPatient != null) ? selectedPatient.getId() : null;

            boolean hasDoctorConflict = wid != null && wid > 0 && conflicts.stream().anyMatch(c -> wid.equals(c.getWorkerId()));
            boolean hasPatientConflict = pid != null && pid > 0 && conflicts.stream().anyMatch(c -> pid.equals(c.getPatientId()));

            String message;
            if (hasDoctorConflict) {
                String names = conflicts.stream()
                        .filter(c -> wid.equals(c.getWorkerId()))
                        .map(c -> c.getDisplayName() + " (" + c.getFormattedTimeRange() + ")")
                        .collect(Collectors.joining(", "));
                message = "Praticien " + selectedWorker.getName() + " déjà occupé sur ce créneau avec : " + names;
            } else if (hasPatientConflict) {
                message = "Ce patient a déjà un autre rendez-vous sur ce créneau.";
            } else {
                int maxCap = settingsDAO.getMaxConcurrentAppointments();
                message = "Capacité d'accueil atteinte : " + conflicts.size() + " rendez-vous simultanés (capacité max : " + maxCap + ").";
            }

            conflictWarningLabel.setText(message);
            conflictWarningBanner.setVisible(true);
            conflictWarningBanner.setManaged(true);
        }
    }

    private void updateSaveButtonState() {
        if (saveBtn == null) return;
        boolean validPerson = toggleWalkIn.isSelected()
                ? (guestNameField.getText() != null && !guestNameField.getText().trim().isEmpty())
                : (selectedPatient != null);
        boolean validDate = datePicker.getValue() != null;
        boolean validTime = resolveStartTimeInput() != null;
        saveBtn.setDisable(!validPerson || !validDate || !validTime);
    }

    @FXML
    private void handleSave() {
        boolean isWalkIn = toggleWalkIn.isSelected();
        if (!isWalkIn && selectedPatient == null) {
            patientErrorLabel.setText("Sélectionnez un patient inscrit ou basculez en mode 'Invité / Appel tél.'");
            patientErrorLabel.setVisible(true);
            patientErrorLabel.setManaged(true);
            patientSearchField.requestFocus();
            return;
        }
        if (isWalkIn && (guestNameField.getText() == null || guestNameField.getText().trim().isEmpty())) {
            patientErrorLabel.setText("Veuillez saisir le nom de l'invité");
            patientErrorLabel.setVisible(true);
            patientErrorLabel.setManaged(true);
            guestNameField.requestFocus();
            return;
        }

        String normalizedTime = resolveStartTimeInput();
        if (normalizedTime == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Heure de début invalide (format attendu : HH:mm, ex: 09:30).");
            alert.setHeaderText(null);
            alert.showAndWait();
            return;
        }

        // Non-blocking conflict check (only triggers if same practitioner, same patient, or over capacity)
        List<Appointment> conflicts = checkCurrentConflicts();
        if (!conflicts.isEmpty()) {
            Worker selectedWorker = workerCombo.getValue();
            Integer wid = selectedWorker != null ? selectedWorker.getId() : null;
            Integer pid = (!toggleWalkIn.isSelected() && selectedPatient != null) ? selectedPatient.getId() : null;

            boolean hasDoctorConflict = wid != null && wid > 0 && conflicts.stream().anyMatch(c -> wid.equals(c.getWorkerId()));
            boolean hasPatientConflict = pid != null && pid > 0 && conflicts.stream().anyMatch(c -> pid.equals(c.getPatientId()));

            String details = conflicts.stream()
                    .map(c -> "• " + c.getDisplayName() + " : " + c.getFormattedTimeRange()
                            + (c.getWorkerName() != null && !c.getWorkerName().isEmpty() ? " (Dr. " + c.getWorkerName() + ")" : "")
                            + (c.getMotif() != null && !c.getMotif().isEmpty() ? " — " + c.getMotif() : ""))
                    .collect(Collectors.joining("\n"));

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Avertissement de planning");
            if (hasDoctorConflict) {
                confirm.setHeaderText("Le praticien " + selectedWorker.getName() + " a déjà un rendez-vous sur ce créneau :");
            } else if (hasPatientConflict) {
                confirm.setHeaderText("Ce patient a déjà un autre rendez-vous sur ce créneau :");
            } else {
                int maxCap = settingsDAO.getMaxConcurrentAppointments();
                confirm.setHeaderText("La capacité simultanée du cabinet (" + maxCap + " patients) est atteinte pour ce créneau :");
            }
            confirm.setContentText(details + "\n\nVoulez-vous enregistrer ce rendez-vous malgré tout ?");
            Optional<ButtonType> res = confirm.showAndWait();
            if (res.isEmpty() || res.get() != ButtonType.OK) {
                return;
            }
        }

        saveClicked = true;
        closeStage();
    }

    @FXML
    private void handleCancel() {
        closeStage();
    }

    private void closeStage() {
        if (dialogStage != null) {
            dialogStage.close();
        } else if (saveBtn != null && saveBtn.getScene() != null) {
            ((Stage) saveBtn.getScene().getWindow()).close();
        }
    }

    public boolean isSaveClicked() {
        return saveClicked;
    }

    public Appointment getAppointment() {
        if (appointment == null) {
            appointment = new Appointment();
        }

        if (toggleWalkIn.isSelected()) {
            appointment.setPatientId(null);
            appointment.setPatientName(null);
            appointment.setPatientPhone(null);
            appointment.setGuestName(guestNameField.getText() != null ? guestNameField.getText().trim() : "");
            appointment.setGuestPhone(guestPhoneField.getText() != null ? guestPhoneField.getText().trim() : "");
        } else if (selectedPatient != null) {
            appointment.setPatientId(selectedPatient.getId());
            appointment.setPatientName(selectedPatient.getName());
            appointment.setPatientPhone(selectedPatient.getPhone());
            appointment.setGuestName(null);
            appointment.setGuestPhone(null);
        }

        Worker w = workerCombo.getValue();
        appointment.setWorkerId(w != null ? w.getId() : null);
        appointment.setWorkerName(w != null ? w.getName() : null);

        appointment.setDate(datePicker.getValue().format(DATE_FORMATTER));
        appointment.setStartTime(resolveStartTimeInput());
        appointment.setDurationMin(resolveDurationInput());

        String motif = motifArea.getText() != null && !motifArea.getText().trim().isEmpty()
                ? motifArea.getText().trim()
                : "Consultation / Séance";
        appointment.setMotif(motif);
        appointment.setStatus(statusCombo.getValue() != null ? statusCombo.getValue() : Appointment.STATUS_PREVU);

        if (appointment.getCreatedBy() == null || appointment.getCreatedBy().trim().isEmpty()) {
            String username = UserSession.isLoggedIn() ? UserSession.getCurrentUser().getUserName() : "Employé";
            appointment.setCreatedBy(username);
        }

        return appointment;
    }

    private class PatientListCell extends ListCell<Patient> {
        @Override
        protected void updateItem(Patient patient, boolean empty) {
            super.updateItem(patient, empty);

            if (empty || patient == null) {
                if (filteredPatients.isEmpty() && getIndex() == 0) {
                    Label noRes = new Label("Aucun patient inscrit trouvé — utilisez 'Invité' ou '+ Créer fiche'");
                    noRes.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px; -fx-font-style: italic; -fx-padding: 6 10;");
                    setGraphic(noRes);
                    setText(null);
                } else {
                    setGraphic(null);
                    setText(null);
                }
                return;
            }

            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(4, 8, 4, 8));

            VBox textCol = new VBox(2);
            HBox.setHgrow(textCol, Priority.ALWAYS);

            Text nameText = new Text(patient.getName());
            nameText.setStyle("-fx-fill: #0f172a; -fx-font-weight: 600; -fx-font-size: 13px;");
            TextFlow nameFlow = new TextFlow(nameText);

            Label phoneLbl = new Label(SessionFormController.formatPhoneNumber(patient.getPhone()));
            phoneLbl.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");
            textCol.getChildren().addAll(nameFlow, phoneLbl);

            row.getChildren().add(textCol);
            setGraphic(row);
            setText(null);
        }
    }
}

