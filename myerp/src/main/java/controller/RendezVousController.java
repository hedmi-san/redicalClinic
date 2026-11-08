package controller;

import dao.AppointmentDAO;
import dao.ClinicSettingsDAO;
import dao.PatientDAO;
import dao.SessionDAO;
import dao.WorkerDAO;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.StringConverter;
import model.Appointment;
import model.Patient;
import model.Session;
import model.Worker;
import util.ArabicTextHelper;
import util.UserSession;

import java.io.IOException;
import java.net.URL;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

public class RendezVousController implements Initializable, Refreshable {

    private enum ViewMode {
        DAY, WEEK, MONTH
    }

    @FXML
    private Label clinicHoursSubtitle;

    @FXML
    private ToggleButton btnModeDay;
    @FXML
    private ToggleButton btnModeWeek;
    @FXML
    private ToggleButton btnModeMonth;

    @FXML
    private Label periodTitleLabel;
    @FXML
    private DatePicker jumpDatePicker;

    @FXML
    private ComboBox<Worker> workerFilterCombo;
    @FXML
    private ComboBox<String> statusFilterCombo;

    @FXML
    private Label upcomingCountLabel;
    @FXML
    private Label completedCountLabel;
    @FXML
    private Label canceledCountLabel;
    @FXML
    private Label periodScopeLabel;

    @FXML
    private StackPane scheduleViewContainer;

    @FXML
    private VBox dailyViewBox;
    @FXML
    private TableView<Appointment> dailyTable;
    @FXML
    private TableColumn<Appointment, Appointment> colTime;
    @FXML
    private TableColumn<Appointment, Appointment> colPatient;
    @FXML
    private TableColumn<Appointment, Appointment> colMotif;
    @FXML
    private TableColumn<Appointment, Appointment> colStatus;
    @FXML
    private TableColumn<Appointment, Appointment> colActions;

    @FXML
    private VBox weeklyViewBox;
    @FXML
    private GridPane weeklyGrid;

    @FXML
    private VBox monthlyViewBox;
    @FXML
    private GridPane monthlyHeaderGrid;
    @FXML
    private GridPane monthlyGrid;

    private final AppointmentDAO appointmentDAO = new AppointmentDAO();
    private final ClinicSettingsDAO settingsDAO = new ClinicSettingsDAO();
    private final PatientDAO patientDAO = new PatientDAO();
    private final SessionDAO sessionDAO = new SessionDAO();
    private final WorkerDAO workerDAO = new WorkerDAO();

    private final ObservableList<Appointment> dailyAppointments = FXCollections.observableArrayList();
    private final Set<Integer> overlappingIdsOnCurrentDay = new HashSet<>();

    private ViewMode currentMode = ViewMode.DAY;
    private LocalDate selectedDate = LocalDate.now();
    private boolean updatingDatePickerProgrammatically = false;

    private static final DateTimeFormatter ISO_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FRENCH_DAY_FMT = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH);
    private static final DateTimeFormatter FRENCH_SHORT_FMT = DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH);
    private static final DateTimeFormatter FRENCH_MONTH_FMT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH);

    // Algerian clinic week ordering: Saturday -> Friday
    private static final List<DayOfWeek> CLINIC_WEEK_ORDER = List.of(
            DayOfWeek.SATURDAY,
            DayOfWeek.SUNDAY,
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY
    );

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupViewModeToggle();
        setupFilters();
        setupDailyTable();

        updatingDatePickerProgrammatically = true;
        jumpDatePicker.setValue(selectedDate);
        updatingDatePickerProgrammatically = false;

        jumpDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (!updatingDatePickerProgrammatically && newVal != null) {
                selectedDate = newVal;
                reloadSchedule();
            }
        });

        reloadFilterWorkers();
        reloadSchedule();
    }

    @Override
    public void refresh() {
        reloadFilterWorkers();
        reloadSchedule();
    }

    private void setupViewModeToggle() {
        ToggleGroup modeGroup = new ToggleGroup();
        btnModeDay.setToggleGroup(modeGroup);
        btnModeWeek.setToggleGroup(modeGroup);
        btnModeMonth.setToggleGroup(modeGroup);

        modeGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null) {
                if (oldToggle != null) {
                    oldToggle.setSelected(true);
                }
                return;
            }
            if (newToggle == btnModeDay) {
                switchToMode(ViewMode.DAY);
            } else if (newToggle == btnModeWeek) {
                switchToMode(ViewMode.WEEK);
            } else if (newToggle == btnModeMonth) {
                switchToMode(ViewMode.MONTH);
            }
        });
    }

    private void switchToMode(ViewMode mode) {
        this.currentMode = mode;
        dailyViewBox.setVisible(mode == ViewMode.DAY);
        dailyViewBox.setManaged(mode == ViewMode.DAY);

        weeklyViewBox.setVisible(mode == ViewMode.WEEK);
        weeklyViewBox.setManaged(mode == ViewMode.WEEK);

        monthlyViewBox.setVisible(mode == ViewMode.MONTH);
        monthlyViewBox.setManaged(mode == ViewMode.MONTH);

        reloadSchedule();
    }

    private void setupFilters() {
        workerFilterCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Worker w) {
                return w == null ? "Tous les praticiens" : w.getName();
            }

            @Override
            public Worker fromString(String s) {
                return null;
            }
        });
        workerFilterCombo.valueProperty().addListener((obs, o, n) -> reloadSchedule());

        statusFilterCombo.setItems(FXCollections.observableArrayList(
                "TOUS",
                Appointment.STATUS_PREVU,
                Appointment.STATUS_CONFIRME,
                Appointment.STATUS_TERMINE,
                Appointment.STATUS_ANNULE,
                Appointment.STATUS_ABSENT
        ));
        statusFilterCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(String s) {
                if (s == null || "TOUS".equals(s)) return "Tous les statuts";
                return Appointment.formatStatusLabel(s);
            }

            @Override
            public String fromString(String s) {
                return s;
            }
        });
        statusFilterCombo.setValue("TOUS");
        statusFilterCombo.valueProperty().addListener((obs, o, n) -> reloadSchedule());
    }

    private void reloadFilterWorkers() {
        Worker current = workerFilterCombo.getValue();
        List<Worker> items = new ArrayList<>();
        items.add(null);
        items.addAll(workerDAO.getAllWorkers());
        workerFilterCombo.setItems(FXCollections.observableArrayList(items));
        if (current != null) {
            for (Worker w : items) {
                if (w != null && w.getId() == current.getId()) {
                    workerFilterCombo.setValue(w);
                    return;
                }
            }
        }
        workerFilterCombo.getSelectionModel().selectFirst();
    }

    private List<Appointment> applyActiveFilters(List<Appointment> rawList) {
        Worker selectedWorker = workerFilterCombo.getValue();
        String selectedStatus = statusFilterCombo.getValue();

        return rawList.stream().filter(a -> {
            if (selectedWorker != null) {
                if (a.getWorkerId() == null || a.getWorkerId() != selectedWorker.getId()) {
                    return false;
                }
            }
            if (selectedStatus != null && !"TOUS".equals(selectedStatus)) {
                if (!selectedStatus.equalsIgnoreCase(a.getStatus())) {
                    return false;
                }
            }
            return true;
        }).collect(Collectors.toList());
    }

    private void reloadSchedule() {
        updateClinicSubtitle();

        updatingDatePickerProgrammatically = true;
        jumpDatePicker.setValue(selectedDate);
        updatingDatePickerProgrammatically = false;

        switch (currentMode) {
            case DAY -> renderDailyView();
            case WEEK -> renderWeeklyView();
            case MONTH -> renderMonthlyView();
        }
    }

    private void updateClinicSubtitle() {
        Set<DayOfWeek> workingDays = settingsDAO.getWorkingDays();
        LocalTime openTime = settingsDAO.getStartTime();
        LocalTime closeTime = settingsDAO.getEndTime();
        int defDur = settingsDAO.getDefaultDurationMin();

        String daysText;
        if (workingDays.contains(DayOfWeek.SATURDAY) && workingDays.contains(DayOfWeek.THURSDAY)
                && !workingDays.contains(DayOfWeek.FRIDAY) && workingDays.size() == 6) {
            daysText = "samedi → jeudi";
        } else {
            daysText = CLINIC_WEEK_ORDER.stream()
                    .filter(workingDays::contains)
                    .map(d -> formatFullFrenchDay(d).toLowerCase(Locale.FRENCH))
                    .collect(Collectors.joining(", "));
        }

        clinicHoursSubtitle.setText(String.format(
                "Ouvert %s · %s–%s · rendez-vous de %d min",
                daysText, openTime, closeTime, defDur
        ));
    }

    // =========================================================================
    // 1. DAILY VIEW
    // =========================================================================

    private void setupDailyTable() {
        colTime.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue()));
        colPatient.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue()));
        colMotif.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue()));
        colStatus.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue()));
        colActions.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue()));

        // Empty state placeholder
        VBox emptyBox = new VBox(8);
        emptyBox.setAlignment(Pos.CENTER);
        Label emptyIcon = new Label("📅");
        emptyIcon.setStyle("-fx-font-size: 28px;");
        Label emptyTitle = new Label("Aucun rendez-vous pour cette date");
        emptyTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: #334155;");
        Label emptySub = new Label("Cliquez sur « + Nouveau RDV » pour planifier un rendez-vous");
        emptySub.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");
        emptyBox.getChildren().addAll(emptyIcon, emptyTitle, emptySub);
        dailyTable.setPlaceholder(emptyBox);

        // 1. Time Column
        colTime.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Appointment a, boolean empty) {
                super.updateItem(a, empty);
                if (empty || a == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                VBox box = new VBox(2);
                box.setAlignment(Pos.CENTER_LEFT);

                HBox top = new HBox(6);
                top.setAlignment(Pos.CENTER_LEFT);
                Label timeLbl = new Label(a.getStartTime() != null ? a.getStartTime() : "—");
                timeLbl.setStyle("-fx-font-weight: 700; -fx-text-fill: #0f172a; -fx-font-size: 13px;");
                top.getChildren().add(timeLbl);

                if (overlappingIdsOnCurrentDay.contains(a.getId())) {
                    Label warn = new Label("⚠️");
                    warn.setTooltip(new Tooltip("Chevauchement d'horaire détecté"));
                    top.getChildren().add(warn);
                }

                Label durLbl = new Label(a.getDurationMin() + " min");
                durLbl.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");

                box.getChildren().addAll(top, durLbl);
                setGraphic(box);
                setText(null);
            }
        });

        // 2. Patient Column (Name + Badge on Top, Phone on Bottom, with Arabic RTL support)
        colPatient.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Appointment a, boolean empty) {
                super.updateItem(a, empty);
                if (empty || a == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                VBox box = new VBox(2);
                box.setAlignment(Pos.CENTER_LEFT);

                HBox top = new HBox(8);
                top.setAlignment(Pos.CENTER_LEFT);

                String name = a.getDisplayName();
                Label nameLbl = new Label(name);
                nameLbl.setStyle("-fx-font-weight: 700; -fx-text-fill: #0f172a; -fx-font-size: 13px;");
                if (ArabicTextHelper.containsArabic(name)) {
                    nameLbl.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
                }

                Label badge = new Label();
                badge.getStyleClass().add("badge-patient-type");
                if (a.isWalkIn()) {
                    badge.setText("Invité");
                } else if (a.isFemale()) {
                    badge.setText("Inscrite");
                } else {
                    badge.setText("Inscrit");
                }

                top.getChildren().addAll(nameLbl, badge);

                String phone = a.getDisplayPhone();
                Label phoneLbl = new Label(phone != null && !phone.isBlank()
                        ? SessionFormController.formatPhoneNumber(phone)
                        : "—");
                phoneLbl.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");

                box.getChildren().addAll(top, phoneLbl);
                setGraphic(box);
                setText(null);
            }
        });

        // 3. Motif & Practitioner Column (Motif on Top, Doctor on Bottom, with Arabic RTL support)
        colMotif.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Appointment a, boolean empty) {
                super.updateItem(a, empty);
                if (empty || a == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                VBox box = new VBox(2);
                box.setAlignment(Pos.CENTER_LEFT);

                String motif = a.getMotif() != null && !a.getMotif().isBlank() ? a.getMotif() : "—";
                Label motifLbl = new Label(motif);
                motifLbl.setStyle("-fx-text-fill: #1e293b; -fx-font-size: 13px; -fx-font-weight: 600;");
                if (ArabicTextHelper.containsArabic(motif)) {
                    motifLbl.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
                }

                HBox practitionerRow = new HBox(5);
                practitionerRow.setAlignment(Pos.CENTER_LEFT);
                String worker = a.getWorkerName() != null && !a.getWorkerName().isBlank()
                        ? a.getWorkerName()
                        : "—";
                Label workerIcon = new Label("👤");
                workerIcon.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748b;");
                Label workerLbl = new Label(worker);
                workerLbl.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");
                practitionerRow.getChildren().addAll(workerIcon, workerLbl);

                box.getChildren().addAll(motifLbl, practitionerRow);
                setGraphic(box);
                setText(null);
            }
        });

        // 4. Status Column (Clean Rounded Pill)
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Appointment a, boolean empty) {
                super.updateItem(a, empty);
                if (empty || a == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                Label pill = createStatusPill(a.getStatus());
                setGraphic(pill);
                setText(null);
            }
        });

        // 5. Actions Column (Primary Dynamic Button + [⋯] Options Menu)
        colActions.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Appointment a, boolean empty) {
                super.updateItem(a, empty);
                if (empty || a == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }

                HBox actionsPane = new HBox(8);
                actionsPane.setAlignment(Pos.CENTER_LEFT);

                // 1. Primary Action Button (Dynamic by Status)
                Button btnPrimary = new Button();
                btnPrimary.getStyleClass().add("btn-row-action");

                String st = a.getStatus() != null ? a.getStatus().toUpperCase() : Appointment.STATUS_PREVU;
                switch (st) {
                    case Appointment.STATUS_PREVU -> {
                        btnPrimary.setText("Confirmer");
                        btnPrimary.setOnAction(e -> {
                            appointmentDAO.updateStatus(a.getId(), Appointment.STATUS_CONFIRME);
                            reloadSchedule();
                        });
                    }
                    case Appointment.STATUS_CONFIRME -> {
                        btnPrimary.setText("✓  Séance");
                        btnPrimary.setOnAction(e -> handleConvertToSession(a));
                    }
                    case Appointment.STATUS_TERMINE -> {
                        btnPrimary.setText("Voir");
                        btnPrimary.setOnAction(e -> openAppointmentModal(a, null, selectedDate));
                    }
                    default -> {
                        btnPrimary.setText("Réactiver");
                        btnPrimary.setOnAction(e -> {
                            appointmentDAO.updateStatus(a.getId(), Appointment.STATUS_PREVU);
                            reloadSchedule();
                        });
                    }
                }

                // 2. More Options Button [⋯]
                Button btnMore = new Button("⋯");
                btnMore.getStyleClass().add("btn-row-more");

                btnMore.setOnAction(e -> {
                    ContextMenu menu = new ContextMenu();

                    MenuItem miEdit = new MenuItem("Modifier");
                    miEdit.setOnAction(ev -> openAppointmentModal(a, null, selectedDate));
                    menu.getItems().add(miEdit);

                    if (a.isWalkIn()) {
                        MenuItem miCreatePatient = new MenuItem("Créer fiche patient");
                        miCreatePatient.setOnAction(ev -> handleCreatePatientFromWalkIn(a));
                        menu.getItems().add(miCreatePatient);
                    }

                    if (!a.isConvertedToSession()
                            && !Appointment.STATUS_ANNULE.equalsIgnoreCase(a.getStatus())
                            && !Appointment.STATUS_ABSENT.equalsIgnoreCase(a.getStatus())) {

                        MenuItem miAbsent = new MenuItem("Marquer absent");
                        miAbsent.setOnAction(ev -> {
                            appointmentDAO.updateStatus(a.getId(), Appointment.STATUS_ABSENT);
                            reloadSchedule();
                        });

                        MenuItem miCancel = new MenuItem("Annuler le rendez-vous");
                        miCancel.setOnAction(ev -> {
                            appointmentDAO.updateStatus(a.getId(), Appointment.STATUS_ANNULE);
                            reloadSchedule();
                        });

                        menu.getItems().addAll(miAbsent, miCancel);
                    }

                    menu.getItems().add(new SeparatorMenuItem());

                    MenuItem miDelete = new MenuItem("Supprimer");
                    miDelete.setStyle("-fx-text-fill: #dc2626;");
                    miDelete.setOnAction(ev -> confirmAndDeleteAppointment(a));
                    menu.getItems().add(miDelete);

                    menu.show(btnMore, Side.BOTTOM, 0, 0);
                });

                actionsPane.getChildren().addAll(btnPrimary, btnMore);
                setGraphic(actionsPane);
                setText(null);
            }
        });

        dailyTable.setRowFactory(tv -> {
            TableRow<Appointment> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    openAppointmentModal(row.getItem(), null, selectedDate);
                }
            });
            return row;
        });

        dailyTable.setItems(dailyAppointments);
    }

    private void renderDailyView() {
        String formattedTitle = selectedDate.format(FRENCH_DAY_FMT);
        periodTitleLabel.setText(formattedTitle);
        if (periodScopeLabel != null) {
            periodScopeLabel.setText("Ce jour");
        }

        String isoDate = selectedDate.format(ISO_DATE_FMT);
        List<Appointment> rawDayList = appointmentDAO.getAppointmentsByDate(isoDate);

        // Compute overlapping appointment IDs for this date (real conflicts only: same practitioner, same patient, or exceeding clinic capacity)
        overlappingIdsOnCurrentDay.clear();
        int maxCap = settingsDAO.getMaxConcurrentAppointments();

        for (int i = 0; i < rawDayList.size(); i++) {
            Appointment a = rawDayList.get(i);
            if (!a.isActiveForConflictCheck()) continue;

            int concurrentCount = 1; // counts 'a' itself
            List<Appointment> concurrentWithA = new ArrayList<>();

            for (int j = 0; j < rawDayList.size(); j++) {
                if (i == j) continue;
                Appointment b = rawDayList.get(j);
                if (!b.isActiveForConflictCheck()) continue;

                if (a.getStartMinutesOfDay() < b.getEndMinutesOfDay()
                        && b.getStartMinutesOfDay() < a.getEndMinutesOfDay()) {
                    concurrentCount++;
                    concurrentWithA.add(b);

                    // 1. Same practitioner double-booked
                    if (a.getWorkerId() != null && a.getWorkerId() > 0
                            && b.getWorkerId() != null
                            && a.getWorkerId().equals(b.getWorkerId())) {
                        overlappingIdsOnCurrentDay.add(a.getId());
                        overlappingIdsOnCurrentDay.add(b.getId());
                    }

                    // 2. Same registered patient double-booked
                    if (a.getPatientId() != null && a.getPatientId() > 0
                            && b.getPatientId() != null
                            && a.getPatientId().equals(b.getPatientId())) {
                        overlappingIdsOnCurrentDay.add(a.getId());
                        overlappingIdsOnCurrentDay.add(b.getId());
                    }
                }
            }

            // 3. Exceeded clinic simultaneous capacity
            if (concurrentCount > maxCap) {
                overlappingIdsOnCurrentDay.add(a.getId());
                for (Appointment ov : concurrentWithA) {
                    overlappingIdsOnCurrentDay.add(ov.getId());
                }
            }
        }

        List<Appointment> filtered = applyActiveFilters(rawDayList);
        dailyAppointments.setAll(filtered);
        updateSummaryCards(filtered);
    }

    // =========================================================================
    // 2. WEEKLY VIEW (7 Columns: Saturday -> Friday)
    // =========================================================================

    private void renderWeeklyView() {
        LocalDate weekStart = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        periodTitleLabel.setText(weekStart.format(FRENCH_SHORT_FMT) + " – " + weekEnd.format(FRENCH_SHORT_FMT) + " " + weekEnd.getYear());
        if (periodScopeLabel != null) {
            periodScopeLabel.setText("Cette semaine");
        }

        List<Appointment> rawWeek = appointmentDAO.getAppointmentsByDateRange(
                weekStart.format(ISO_DATE_FMT),
                weekEnd.format(ISO_DATE_FMT)
        );
        List<Appointment> filteredWeek = applyActiveFilters(rawWeek);
        updateSummaryCards(filteredWeek);

        Map<String, List<Appointment>> byDate = filteredWeek.stream()
                .collect(Collectors.groupingBy(Appointment::getDate));

        Set<DayOfWeek> workingDays = settingsDAO.getWorkingDays();
        LocalDate today = LocalDate.now();

        weeklyGrid.getChildren().clear();
        weeklyGrid.getColumnConstraints().clear();
        weeklyGrid.getRowConstraints().clear();

        for (int col = 0; col < 7; col++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / 7.0);
            cc.setHgrow(Priority.ALWAYS);
            weeklyGrid.getColumnConstraints().add(cc);
        }
        RowConstraints rc = new RowConstraints();
        rc.setVgrow(Priority.ALWAYS);
        weeklyGrid.getRowConstraints().add(rc);

        for (int i = 0; i < 7; i++) {
            LocalDate dayDate = weekStart.plusDays(i);
            String iso = dayDate.format(ISO_DATE_FMT);
            boolean isWorking = workingDays.contains(dayDate.getDayOfWeek());
            boolean isToday = dayDate.equals(today);

            VBox colBox = new VBox(0);
            colBox.getStyleClass().add(isWorking ? "week-day-column" : "week-day-column-off");

            // Day Header (Click jumps to Daily View for that day)
            VBox header = new VBox(2);
            header.getStyleClass().add("week-day-header");
            if (isToday) {
                header.getStyleClass().add("week-day-header-today");
            }

            HBox headerTop = new HBox(4);
            headerTop.setAlignment(Pos.CENTER_LEFT);

            Label dayNameLbl = new Label(formatShortFrenchDay(dayDate.getDayOfWeek()).toUpperCase() + " " + dayDate.getDayOfMonth());
            dayNameLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: "
                    + (isToday ? "#0d9488" : (isWorking ? "#0f172a" : "#94a3b8")) + ";");
            headerTop.getChildren().add(dayNameLbl);

            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);
            headerTop.getChildren().add(sp);

            List<Appointment> dayItems = byDate.getOrDefault(iso, List.of());
            if (!isWorking) {
                Label offTag = new Label("Repos");
                offTag.setStyle("-fx-font-size: 10px; -fx-text-fill: #94a3b8;");
                headerTop.getChildren().add(offTag);
            } else if (!dayItems.isEmpty()) {
                Label countBadge = new Label(String.valueOf(dayItems.size()));
                countBadge.setStyle("-fx-font-size: 10px; -fx-font-weight: 700; -fx-text-fill: #0d9488; -fx-background-color: #ccfbf1; -fx-padding: 1 5; -fx-background-radius: 999px;");
                headerTop.getChildren().add(countBadge);
            }

            header.getChildren().add(headerTop);
            header.setOnMouseClicked(e -> {
                selectedDate = dayDate;
                btnModeDay.setSelected(true);
            });

            // Cards Container
            VBox cardsContainer = new VBox(8);
            cardsContainer.setPadding(new Insets(8));
            VBox.setVgrow(cardsContainer, Priority.ALWAYS);

            if (dayItems.isEmpty()) {
                Label emptyLbl = new Label(isWorking ? "Aucun RDV" : "Cabinet fermé");
                emptyLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-font-style: italic;");
                cardsContainer.getChildren().add(emptyLbl);
            } else {
                for (Appointment a : dayItems) {
                    cardsContainer.getChildren().add(createWeeklyAppointmentCard(a));
                }
            }

            // Show "+ RDV" only on working days
            if (isWorking) {
                Button addOnDayBtn = new Button("+ RDV");
                addOnDayBtn.getStyleClass().add("btn-week-add");
                addOnDayBtn.setMaxWidth(Double.MAX_VALUE);
                addOnDayBtn.setOnAction(e -> openAppointmentModal(null, null, dayDate));
                cardsContainer.getChildren().add(addOnDayBtn);
            }

            colBox.getChildren().addAll(header, cardsContainer);
            weeklyGrid.add(colBox, i, 0);
        }
    }

    private VBox createWeeklyAppointmentCard(Appointment a) {
        VBox card = new VBox(4);
        card.getStyleClass().add("week-appointment-card");

        HBox top = new HBox(6);
        top.setAlignment(Pos.CENTER_LEFT);
        Label timeLbl = new Label(a.getStartTime() + " (" + a.getDurationMin() + "m)");
        timeLbl.setStyle("-fx-font-weight: 700; -fx-font-size: 11px; -fx-text-fill: #0d9488;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label statusPill = createStatusPill(a.getStatus());
        statusPill.setStyle(statusPill.getStyle() + "; -fx-font-size: 9px; -fx-padding: 1 6;");
        top.getChildren().addAll(timeLbl, spacer, statusPill);

        String name = a.getDisplayName();
        Label nameLbl = new Label(name);
        nameLbl.setStyle("-fx-font-weight: 700; -fx-font-size: 12px; -fx-text-fill: #0f172a;");
        if (ArabicTextHelper.containsArabic(name)) {
            nameLbl.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
        }

        String motif = a.getMotif() != null && !a.getMotif().isBlank() ? a.getMotif() : "—";
        Label motifLbl = new Label(motif);
        motifLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
        if (ArabicTextHelper.containsArabic(motif)) {
            motifLbl.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
        }

        card.getChildren().addAll(top, nameLbl, motifLbl);
        card.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                openAppointmentModal(a, null, LocalDate.parse(a.getDate(), ISO_DATE_FMT));
            }
        });
        return card;
    }

    // =========================================================================
    // 3. MONTHLY VIEW (Calendar Grid with Count Badges)
    // =========================================================================

    private void renderMonthlyView() {
        YearMonth ym = YearMonth.from(selectedDate);
        periodTitleLabel.setText(capitalize(ym.atDay(1).format(FRENCH_MONTH_FMT)));
        if (periodScopeLabel != null) {
            periodScopeLabel.setText("Ce mois");
        }

        LocalDate firstOfMonth = ym.atDay(1);
        LocalDate lastOfMonth = ym.atEndOfMonth();

        LocalDate gridStart = firstOfMonth.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));
        LocalDate gridEnd = lastOfMonth.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));

        List<Appointment> monthAppointments = applyActiveFilters(
                appointmentDAO.getAppointmentsByDateRange(
                        firstOfMonth.format(ISO_DATE_FMT),
                        lastOfMonth.format(ISO_DATE_FMT)
                )
        );
        updateSummaryCards(monthAppointments);

        Map<String, List<Appointment>> byDate = monthAppointments.stream()
                .collect(Collectors.groupingBy(Appointment::getDate));

        // Header Row
        monthlyHeaderGrid.getChildren().clear();
        monthlyHeaderGrid.getColumnConstraints().clear();
        for (int c = 0; c < 7; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / 7.0);
            monthlyHeaderGrid.getColumnConstraints().add(cc);

            DayOfWeek dow = CLINIC_WEEK_ORDER.get(c);
            Label hdr = new Label(formatShortFrenchDay(dow).toUpperCase());
            hdr.setMaxWidth(Double.MAX_VALUE);
            hdr.getStyleClass().add("month-hdr-label");
            monthlyHeaderGrid.add(hdr, c, 0);
        }

        // Calendar Cells
        monthlyGrid.getChildren().clear();
        monthlyGrid.getColumnConstraints().clear();
        monthlyGrid.getRowConstraints().clear();
        for (int c = 0; c < 7; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / 7.0);
            cc.setHgrow(Priority.ALWAYS);
            monthlyGrid.getColumnConstraints().add(cc);
        }

        Set<DayOfWeek> workingDays = settingsDAO.getWorkingDays();
        LocalDate today = LocalDate.now();
        LocalDate cursor = gridStart;
        int row = 0;
        while (!cursor.isAfter(gridEnd)) {
            RowConstraints rc = new RowConstraints();
            rc.setMinHeight(65);
            rc.setPrefHeight(72);
            rc.setMaxHeight(78);
            rc.setVgrow(Priority.ALWAYS);
            monthlyGrid.getRowConstraints().add(rc);

            for (int col = 0; col < 7; col++) {
                LocalDate cellDate = cursor;
                boolean inMonth = YearMonth.from(cellDate).equals(ym);
                boolean isToday = cellDate.equals(today);
                boolean isWorking = workingDays.contains(cellDate.getDayOfWeek());

                VBox cell = new VBox(4);
                if (!inMonth) {
                    cell.getStyleClass().add("month-day-cell-outside");
                } else {
                    cell.getStyleClass().add("month-day-cell");
                    if (isToday) {
                        cell.getStyleClass().add("month-day-cell-today");
                    }
                }

                HBox topRow = new HBox();
                topRow.setAlignment(Pos.CENTER_LEFT);
                Label dayNum = new Label(String.valueOf(cellDate.getDayOfMonth()));
                dayNum.setStyle("-fx-font-weight: 700; -fx-font-size: 13px; -fx-text-fill: "
                        + (isToday ? "#0d9488" : (inMonth ? "#0f172a" : "#94a3b8")) + ";");
                Region sp = new Region();
                HBox.setHgrow(sp, Priority.ALWAYS);
                topRow.getChildren().addAll(dayNum, sp);

                if (inMonth && !isWorking) {
                    Label offTag = new Label("Repos");
                    offTag.setStyle("-fx-font-size: 10px; -fx-text-fill: #94a3b8; -fx-background-color: #f1f5f9; -fx-padding: 1 5; -fx-background-radius: 4px;");
                    topRow.getChildren().add(offTag);
                }
                cell.getChildren().add(topRow);

                if (inMonth) {
                    List<Appointment> dayList = byDate.getOrDefault(cellDate.format(ISO_DATE_FMT), List.of());
                    if (!dayList.isEmpty()) {
                        Label totalBadge = new Label(dayList.size() + (dayList.size() > 1 ? " RDVs" : " RDV"));
                        totalBadge.getStyleClass().add("month-rdv-badge");
                        cell.getChildren().add(totalBadge);
                    }
                }

                cell.setOnMouseClicked(e -> {
                    selectedDate = cellDate;
                    btnModeDay.setSelected(true);
                });

                monthlyGrid.add(cell, col, row);
                cursor = cursor.plusDays(1);
            }
            row++;
        }
    }

    // =========================================================================
    // NAVIGATION & ACTIONS
    // =========================================================================

    @FXML
    private void handlePrevPeriod() {
        selectedDate = switch (currentMode) {
            case DAY -> selectedDate.minusDays(1);
            case WEEK -> selectedDate.minusWeeks(1);
            case MONTH -> selectedDate.minusMonths(1);
        };
        reloadSchedule();
    }

    @FXML
    private void handleNextPeriod() {
        selectedDate = switch (currentMode) {
            case DAY -> selectedDate.plusDays(1);
            case WEEK -> selectedDate.plusWeeks(1);
            case MONTH -> selectedDate.plusMonths(1);
        };
        reloadSchedule();
    }

    @FXML
    private void handleToday() {
        selectedDate = LocalDate.now();
        reloadSchedule();
    }

    @FXML
    private void handleRefresh() {
        refresh();
    }

    @FXML
    private void handleAddAppointment() {
        openAppointmentModal(null, null, selectedDate);
    }

    private void confirmAndDeleteAppointment(Appointment appointment) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Supprimer le rendez-vous");
        confirm.setHeaderText("Supprimer le rendez-vous de " + appointment.getDisplayName() + " ?");
        confirm.setContentText("Cette action supprimera uniquement le rendez-vous de l'agenda (aucun impact sur le solde patient).");
        Optional<ButtonType> res = confirm.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.OK) {
            if (appointmentDAO.deleteAppointment(appointment.getId())) {
                reloadSchedule();
            }
        }
    }

    private void openAppointmentModal(Appointment existing, Integer preselectedPatientId, LocalDate defaultDate) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pages/appointment_form.fxml"));
            Parent root = loader.load();
            AppointmentFormController controller = loader.getController();

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.TRANSPARENT);
            Scene scene = new Scene(root);
            scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
            stage.setScene(scene);
            stage.setResizable(false);

            controller.setAppointment(existing, preselectedPatientId, defaultDate);
            controller.initStage(stage);

            stage.sizeToScene();
            stage.showAndWait();

            if (controller.isSaveClicked()) {
                Appointment updated = controller.getAppointment();
                if (existing == null || existing.getId() == 0) {
                    appointmentDAO.addAppointment(updated);
                } else {
                    appointmentDAO.updateAppointment(updated);
                }
                reloadSchedule();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleCreatePatientFromWalkIn(Appointment walkInAppointment) {
        Patient created = promptCreatePatientFromGuest(walkInAppointment.getGuestName(), walkInAppointment.getGuestPhone());
        if (created != null && created.getId() > 0) {
            appointmentDAO.linkPatientToAppointment(walkInAppointment.getId(), created.getId());
            reloadSchedule();
        }
    }

    private Patient promptCreatePatientFromGuest(String guestName, String guestPhone) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pages/patient_form.fxml"));
            Parent root = loader.load();

            PatientFormController controller = loader.getController();
            controller.setWalkInPrefill(guestName, guestPhone);

            Stage stage = new Stage();
            stage.setTitle("Créer Fiche Patient");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();

            if (controller.isSaveClicked()) {
                Patient newPatient = controller.getPatient();
                if (patientDAO.addPatient(newPatient)) {
                    return newPatient;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void handleConvertToSession(Appointment appointment) {
        if (appointment.isConvertedToSession()) {
            showAlert("Déjà converti", "Ce rendez-vous a déjà été converti en séance (#" + appointment.getSessionId() + ").");
            return;
        }

        Integer patientId = appointment.getPatientId();
        if (patientId == null || patientId <= 0) {
            // Walk-in appointment: create/link the Patient record first
            Patient registered = promptCreatePatientFromGuest(appointment.getGuestName(), appointment.getGuestPhone());
            if (registered == null || registered.getId() <= 0) {
                return;
            }
            patientId = registered.getId();
            appointmentDAO.linkPatientToAppointment(appointment.getId(), patientId);
            appointment.setPatientId(patientId);
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pages/session_form.fxml"));
            Parent root = loader.load();
            SessionFormController controller = loader.getController();

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.TRANSPARENT);
            Scene scene = new Scene(root);
            scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
            stage.setScene(scene);
            stage.setResizable(false);

            controller.setPrefillFromAppointment(patientId, appointment.getDate(), appointment.getMotif());
            controller.initStage(stage);

            stage.sizeToScene();
            stage.showAndWait();

            if (controller.isSaveClicked()) {
                Session newSession = controller.getSession();
                double cashReceived = controller.getCashReceived();
                String username = UserSession.isLoggedIn() ? UserSession.getCurrentUser().getUserName() : "Employé";
                if (sessionDAO.addSession(newSession, cashReceived, username)) {
                    appointmentDAO.markConvertedToSession(appointment.getId(), newSession.getId());
                    reloadSchedule();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleConfigureHours() {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Configuration des Horaires du Cabinet");
        dialog.setHeaderText("Jours ouvrés, horaires d'ouverture, durée et capacité d'accueil simultanée");

        ButtonType saveBtnType = new ButtonType("Enregistrer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);

        VBox content = new VBox(14);
        content.setPadding(new Insets(16, 20, 10, 20));

        Label daysTitle = new Label("Jours ouvrés de la semaine :");
        daysTitle.setStyle("-fx-font-weight: 700; -fx-text-fill: #1e293b;");

        Set<DayOfWeek> currentDays = settingsDAO.getWorkingDays();
        Map<DayOfWeek, CheckBox> dayChecks = new LinkedHashMap<>();
        FlowPane daysFlow = new FlowPane(12, 8);
        for (DayOfWeek dow : CLINIC_WEEK_ORDER) {
            CheckBox cb = new CheckBox(formatFullFrenchDay(dow));
            cb.setSelected(currentDays.contains(dow));
            dayChecks.put(dow, cb);
            daysFlow.getChildren().add(cb);
        }

        GridPane timeGrid = new GridPane();
        timeGrid.setHgap(12);
        timeGrid.setVgap(10);

        TextField startField = new TextField(settingsDAO.getStartTime().toString());
        startField.setPromptText("08:00");
        TextField endField = new TextField(settingsDAO.getEndTime().toString());
        endField.setPromptText("17:00");
        ComboBox<String> durationBox = new ComboBox<>(FXCollections.observableArrayList("15", "20", "30", "45", "60"));
        durationBox.setEditable(true);
        durationBox.setValue(String.valueOf(settingsDAO.getDefaultDurationMin()));

        ComboBox<String> capacityBox = new ComboBox<>(FXCollections.observableArrayList("1", "2", "3", "4", "5", "6", "8", "10", "15"));
        capacityBox.setEditable(true);
        capacityBox.setValue(String.valueOf(settingsDAO.getMaxConcurrentAppointments()));

        timeGrid.add(new Label("Heure d'ouverture (HH:mm) :"), 0, 0);
        timeGrid.add(startField, 1, 0);
        timeGrid.add(new Label("Heure de fermeture (HH:mm) :"), 0, 1);
        timeGrid.add(endField, 1, 1);
        timeGrid.add(new Label("Durée RDV par défaut (min) :"), 0, 2);
        timeGrid.add(durationBox, 1, 2);
        timeGrid.add(new Label("Capacité simultanée (patients max) :"), 0, 3);
        timeGrid.add(capacityBox, 1, 3);

        content.getChildren().addAll(daysTitle, daysFlow, new Separator(), timeGrid);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(btn -> {
            if (btn == saveBtnType) {
                Set<DayOfWeek> selectedDays = EnumSet.noneOf(DayOfWeek.class);
                dayChecks.forEach((d, cb) -> {
                    if (cb.isSelected()) selectedDays.add(d);
                });
                if (!selectedDays.isEmpty()) {
                    settingsDAO.setWorkingDays(selectedDays);
                }
                try {
                    settingsDAO.setStartTime(LocalTime.parse(startField.getText().trim()));
                } catch (Exception ignored) {
                }
                try {
                    settingsDAO.setEndTime(LocalTime.parse(endField.getText().trim()));
                } catch (Exception ignored) {
                }
                try {
                    String durStr = durationBox.getEditor().getText().trim();
                    if (durStr.isEmpty() && durationBox.getValue() != null) durStr = durationBox.getValue();
                    int d = Integer.parseInt(durStr);
                    if (d > 0) settingsDAO.setDefaultDurationMin(d);
                } catch (Exception ignored) {
                }
                try {
                    String capStr = capacityBox.getEditor().getText().trim();
                    if (capStr.isEmpty() && capacityBox.getValue() != null) capStr = capacityBox.getValue();
                    int c = Integer.parseInt(capStr);
                    if (c > 0) settingsDAO.setMaxConcurrentAppointments(c);
                } catch (Exception ignored) {
                }
                return true;
            }
            return false;
        });

        Optional<Boolean> res = dialog.showAndWait();
        if (res.isPresent() && res.get()) {
            reloadSchedule();
        }
    }

    private void updateSummaryCards(List<Appointment> appointments) {
        long upcoming = appointments.stream()
                .filter(a -> Appointment.STATUS_PREVU.equalsIgnoreCase(a.getStatus())
                        || Appointment.STATUS_CONFIRME.equalsIgnoreCase(a.getStatus()))
                .count();
        long completed = appointments.stream()
                .filter(a -> Appointment.STATUS_TERMINE.equalsIgnoreCase(a.getStatus()))
                .count();
        long canceled = appointments.stream()
                .filter(a -> Appointment.STATUS_ANNULE.equalsIgnoreCase(a.getStatus())
                        || Appointment.STATUS_ABSENT.equalsIgnoreCase(a.getStatus()))
                .count();

        upcomingCountLabel.setText(String.valueOf(upcoming));
        completedCountLabel.setText(String.valueOf(completed));
        canceledCountLabel.setText(String.valueOf(canceled));
    }

    private Label createStatusPill(String status) {
        Label pill = new Label();
        pill.getStyleClass().add("rdv-pill");
        String s = status != null ? status.toUpperCase() : Appointment.STATUS_PREVU;
        switch (s) {
            case Appointment.STATUS_CONFIRME -> {
                pill.setText("Confirmé");
                pill.getStyleClass().add("rdv-pill-confirme");
            }
            case Appointment.STATUS_TERMINE -> {
                pill.setText("Terminé");
                pill.getStyleClass().add("rdv-pill-termine");
            }
            case Appointment.STATUS_ANNULE -> {
                pill.setText("Annulé");
                pill.getStyleClass().add("rdv-pill-annule");
            }
            case Appointment.STATUS_ABSENT -> {
                pill.setText("Absent");
                pill.getStyleClass().add("rdv-pill-absent");
            }
            default -> {
                pill.setText("Prévu");
                pill.getStyleClass().add("rdv-pill-prevu");
            }
        }
        return pill;
    }

    private String formatShortFrenchDay(DayOfWeek day) {
        return switch (day) {
            case SATURDAY -> "Sam";
            case SUNDAY -> "Dim";
            case MONDAY -> "Lun";
            case TUESDAY -> "Mar";
            case WEDNESDAY -> "Mer";
            case THURSDAY -> "Jeu";
            case FRIDAY -> "Ven";
        };
    }

    private String formatFullFrenchDay(DayOfWeek day) {
        return switch (day) {
            case SATURDAY -> "Samedi";
            case SUNDAY -> "Dimanche";
            case MONDAY -> "Lundi";
            case TUESDAY -> "Mardi";
            case WEDNESDAY -> "Mercredi";
            case THURSDAY -> "Jeudi";
            case FRIDAY -> "Vendredi";
        };
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
