package controller;

import dao.PatientDAO;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;
import model.Patient;
import model.Session;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class SessionFormController {

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
    private StackPane patientFieldContainer;
    @FXML
    private TextField patientSearchField;
    @FXML
    private Button btnClearPatient;
    @FXML
    private Label patientErrorLabel;

    @FXML
    private DatePicker datePicker;
    @FXML
    private TextArea treatmentArea;

    @FXML
    private TextField costField;
    @FXML
    private Label costErrorLabel;

    @FXML
    private TextField paidAmountField;
    @FXML
    private Hyperlink linkPayFull;

    @FXML
    private VBox financialSummaryBox;

    @FXML
    private Button cancelBtn;
    @FXML
    private Button saveBtn;

    private Stage dialogStage;
    private double xOffset = 0;
    private double yOffset = 0;

    private Session session;
    private Patient selectedPatient;
    private boolean saveClicked = false;
    private boolean userManuallyChangedCash = false;

    private final PatientDAO patientDAO = new PatientDAO();
    private final ObservableList<Patient> allPatients = FXCollections.observableArrayList();
    private final ObservableList<Patient> filteredPatients = FXCollections.observableArrayList();

    private final Popup suggestionPopup = new Popup();
    private final ListView<Patient> suggestionListView = new ListView<>();
    private final PauseTransition searchDebounce = new PauseTransition(Duration.millis(200));

    private String currentSearchQuery = "";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final NumberFormat FRENCH_NF = NumberFormat.getNumberInstance(Locale.FRANCE);

    static {
        FRENCH_NF.setMinimumFractionDigits(2);
        FRENCH_NF.setMaximumFractionDigits(2);
    }

    @FXML
    public void initialize() {
        setupSuggestionPopup();
        setupInputControls();
        setupListeners();
        updateSaveButtonState();
        recalculateFinancialSummary();
    }

    /**
     * Initializes draggable chrome and keyboard listeners once the Stage is assigned.
     */
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

    private void setupSuggestionPopup() {
        suggestionPopup.setAutoHide(true);
        suggestionListView.getStyleClass().add("patient-popup-list");
        suggestionListView.setFixedCellSize(52);
        suggestionListView.setItems(filteredPatients);
        suggestionListView.setCellFactory(lv -> new PatientListCell());

        // Select on click
        suggestionListView.setOnMouseClicked(event -> {
            Patient p = suggestionListView.getSelectionModel().getSelectedItem();
            if (p != null) {
                commitPatientSelection(p);
            }
        });

        // Keyboard navigation inside list
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
        // Load all patients from DB
        List<Patient> patients = patientDAO.getAllPatients();
        allPatients.setAll(patients);

        // Date Picker defaults
        datePicker.setValue(LocalDate.now());

        // Default money fields
        costField.setText("0,00");
        paidAmountField.setText("0,00");

        // Focus select-all for seamless typing
        costField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (isFocused) {
                Platform.runLater(costField::selectAll);
            } else {
                formatFieldOnBlur(costField);
            }
        });

        paidAmountField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (isFocused) {
                Platform.runLater(paidAmountField::selectAll);
            } else {
                formatFieldOnBlur(paidAmountField);
            }
        });
    }

    private void setupListeners() {
        // Search text typing with debounce
        patientSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (selectedPatient != null && !newVal.equals(selectedPatient.getName())) {
                // User edited the field after selecting a patient: clear selection
                selectedPatient = null;
                btnClearPatient.setVisible(false);
                recalculateFinancialSummary();
                updateSaveButtonState();
            }

            searchDebounce.setOnFinished(e -> performPatientFilter(newVal));
            searchDebounce.playFromStart();
        });

        // Keyboard control on search field
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
            } else if (event.getCode() == KeyCode.ESCAPE) {
                if (suggestionPopup.isShowing()) {
                    suggestionPopup.hide();
                    event.consume();
                }
            }
        });

        // Hide popup if focus lost from both field and list
        patientSearchField.focusedProperty().addListener((obs, oldVal, isFocused) -> {
            if (!isFocused && !suggestionListView.isFocused()) {
                suggestionPopup.hide();
            }
        });

        // Cost field listeners
        costField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!userManuallyChangedCash) {
                applySuggestedCashAmount();
            }
            recalculateFinancialSummary();
            updateSaveButtonState();
        });

        // Cash field listeners
        paidAmountField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (paidAmountField.isFocused()) {
                userManuallyChangedCash = true;
            }
            recalculateFinancialSummary();
            updateSaveButtonState();
        });

        datePicker.valueProperty().addListener((obs, oldVal, newVal) -> updateSaveButtonState());
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

        // Bind width to search field
        suggestionListView.setPrefWidth(patientSearchField.getWidth());

        if (matches.isEmpty()) {
            // Show single row with "Aucun patient trouvé"
            suggestionListView.setPrefHeight(44);
        } else {
            int visibleCount = Math.min(matches.size(), 5);
            suggestionListView.setPrefHeight(visibleCount * 52 + 2);
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
        patientSearchField.setText(patient.getName());
        btnClearPatient.setVisible(true);
        patientErrorLabel.setVisible(false);
        patientErrorLabel.setManaged(false);
        suggestionPopup.hide();

        applySuggestedCashAmount();
        recalculateFinancialSummary();
        updateSaveButtonState();
    }

    @FXML
    private void handleClearPatient() {
        this.selectedPatient = null;
        patientSearchField.clear();
        btnClearPatient.setVisible(false);
        suggestionPopup.hide();
        recalculateFinancialSummary();
        updateSaveButtonState();
        patientSearchField.requestFocus();
    }

    @FXML
    private void handlePayFull() {
        double cost = parseAmount(costField.getText());
        paidAmountField.setText(formatNumber(cost));
        userManuallyChangedCash = true;
        recalculateFinancialSummary();
        updateSaveButtonState();
    }

    private void applySuggestedCashAmount() {
        if (selectedPatient == null) {
            paidAmountField.setText("0,00");
            return;
        }

        double cost = parseAmount(costField.getText());
        double balance = selectedPatient.getBalance(); // negative = credit, positive = debt

        if (cost <= 0) {
            paidAmountField.setText("0,00");
            return;
        }

        if (balance <= -cost && cost > 0) {
            // Patient has enough advance credit to fully cover session
            paidAmountField.setText("0,00");
        } else if (balance < 0) {
            // Partial credit: pay only the remainder
            double credit = Math.abs(balance);
            double remainingOwed = Math.max(0, cost - credit);
            paidAmountField.setText(formatNumber(remainingOwed));
        } else {
            // Debt or neutral: default to full session cost
            paidAmountField.setText(formatNumber(cost));
        }
    }

    private void recalculateFinancialSummary() {
        if (financialSummaryBox == null) return;
        financialSummaryBox.getChildren().clear();

        if (selectedPatient == null) {
            financialSummaryBox.getStyleClass().setAll("summary-box", "summary-box-empty");
            HBox emptyRow = new HBox(8);
            emptyRow.setAlignment(Pos.CENTER_LEFT);
            Label infoIcon = new Label("ⓘ");
            infoIcon.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 14px;");
            Label emptyText = new Label("Sélectionnez un patient pour voir le solde.");
            emptyText.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");
            emptyRow.getChildren().addAll(infoIcon, emptyText);
            financialSummaryBox.getChildren().add(emptyRow);
            return;
        }

        financialSummaryBox.getStyleClass().setAll("summary-box", "summary-box-active");

        Label title = new Label("RÉSUMÉ FINANCIER");
        title.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: #64748b;");

        double currentBalance = selectedPatient.getBalance();
        double cost = parseAmount(costField.getText());
        double cashReceived = parseAmount(paidAmountField.getText());
        double newBalance = currentBalance + cost - cashReceived;

        // Row 1: Solde actuel
        HBox row1 = createSummaryRow("Solde actuel", formatBalance(currentBalance), getBalanceColor(currentBalance));
        // Row 2: Cette séance
        HBox row2 = createSummaryRow("Cette séance", "+ " + formatNumber(cost) + " DA", "#334155");
        // Row 3: − Espèces reçues
        HBox row3 = createSummaryRow("− Espèces reçues", "− " + formatNumber(cashReceived) + " DA", "#334155");

        Region divider = new Region();
        divider.setStyle("-fx-background-color: #e2e8f0; -fx-pref-height: 1; -fx-max-height: 1;");

        // Result Row
        String resultLabelText;
        String resultValText;
        String resultColor;

        if (newBalance > 0.001) {
            resultLabelText = "Nouveau solde dû :";
            resultValText = formatNumber(newBalance) + " DA";
            resultColor = "#d64545"; // Red
        } else if (newBalance < -0.001) {
            resultLabelText = "Crédit patient :";
            resultValText = formatNumber(Math.abs(newBalance)) + " DA";
            resultColor = "#0284c7"; // Blue
        } else {
            resultLabelText = "Compte soldé :";
            resultValText = "0,00 DA";
            resultColor = "#15803d"; // Green
        }

        HBox resultRow = new HBox(8);
        resultRow.setAlignment(Pos.CENTER_LEFT);
        Label lblResultTitle = new Label(resultLabelText);
        lblResultTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: 600; -fx-text-fill: #0f172a;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label lblResultValue = new Label(resultValText);
        lblResultValue.setStyle("-fx-font-size: 16px; -fx-font-weight: 700; -fx-text-fill: " + resultColor + ";");
        resultRow.getChildren().addAll(lblResultTitle, spacer, lblResultValue);

        financialSummaryBox.getChildren().addAll(title, row1, row2, row3, divider, resultRow);
    }

    private HBox createSummaryRow(String label, String value, String colorHex) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748b;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label val = new Label(value);
        val.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: " + colorHex + ";");
        row.getChildren().addAll(lbl, spacer, val);
        return row;
    }

    private String formatBalance(double bal) {
        if (bal > 0.01) {
            return "Dû " + formatNumber(bal) + " DA";
        } else if (bal < -0.01) {
            return "Crédit " + formatNumber(Math.abs(bal)) + " DA";
        } else {
            return "0,00 DA (Soldé)";
        }
    }

    private String getBalanceColor(double bal) {
        if (bal > 0.01) return "#b91c1c";
        if (bal < -0.01) return "#0284c7";
        return "#15803d";
    }

    private void updateSaveButtonState() {
        boolean hasPatient = selectedPatient != null;
        boolean hasValidCost = parseAmount(costField.getText()) >= 0;
        boolean hasDate = datePicker.getValue() != null;
        saveBtn.setDisable(!hasPatient || !hasValidCost || !hasDate);
    }

    private void formatFieldOnBlur(TextField field) {
        String txt = field.getText();
        if (txt != null && !txt.trim().isEmpty()) {
            double val = parseAmount(txt);
            field.setText(formatNumber(val));
        }
    }

    public void setSession(Session session, int patientId) {
        this.session = session;

        if (session != null) {
            titleLabel.setText("Modifier la séance");
            subtitleLabel.setText("Mettre à jour les informations de la séance");
            datePicker.setValue(LocalDate.parse(session.getDate(), DATE_FORMATTER));
            treatmentArea.setText(session.getTreatment());
            costField.setText(formatNumber(session.getCost()));
            paidAmountField.setText(formatNumber(session.getPaidAmount()));
            userManuallyChangedCash = true;

            int pid = session.getPatientId() > 0 ? session.getPatientId() : patientId;
            if (pid > 0) {
                Patient p = patientDAO.getPatientById(pid);
                if (p != null) {
                    commitPatientSelection(p);
                }
            }
            saveBtn.setText("Mettre à jour");
        } else {
            titleLabel.setText("Nouvelle séance");
            subtitleLabel.setText("Enregistrer un traitement et le règlement du jour");
            datePicker.setValue(LocalDate.now());
            costField.setText("0,00");
            paidAmountField.setText("0,00");
            userManuallyChangedCash = false;

            if (patientId > 0) {
                Patient p = patientDAO.getPatientById(patientId);
                if (p != null) {
                    commitPatientSelection(p);
                }
            }
            saveBtn.setText("Enregistrer");
        }

        recalculateFinancialSummary();
        updateSaveButtonState();
    }

    public boolean isSaveClicked() {
        return saveClicked;
    }

    public double getCashReceived() {
        return parseAmount(paidAmountField.getText());
    }

    public Session getSession() {
        if (session == null) {
            session = new Session();
        }
        if (selectedPatient != null) {
            session.setPatientId(selectedPatient.getId());
            session.setPatientName(selectedPatient.getName());
        } else {
            session.setPatientId(0);
            session.setPatientName(null);
        }
        session.setDate(datePicker.getValue().format(DATE_FORMATTER));
        session.setTreatment(treatmentArea.getText() != null && !treatmentArea.getText().trim().isEmpty()
                ? treatmentArea.getText().trim() : "Séance standard");
        session.setCost(parseAmount(costField.getText()));
        session.setPaidAmount(getCashReceived());
        session.setPaied(session.getPaymentStatus());
        return session;
    }

    @FXML
    private void handleSave() {
        if (selectedPatient == null) {
            patientErrorLabel.setText("Sélectionnez un patient dans la liste");
            patientErrorLabel.setVisible(true);
            patientErrorLabel.setManaged(true);
            patientSearchField.requestFocus();
            return;
        }

        double cost = parseAmount(costField.getText());
        if (cost < 0) {
            costErrorLabel.setText("Le coût doit être supérieur ou égal à 0");
            costErrorLabel.setVisible(true);
            costErrorLabel.setManaged(true);
            costField.requestFocus();
            return;
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

    public static double parseAmount(String text) {
        if (text == null || text.trim().isEmpty()) return 0.0;
        // Strip all unicode space separators (\p{Z}), ASCII whitespace (\s), non-breaking spaces (\u00A0),
        // narrow non-breaking spaces (\u202F), and any trailing currency markers or non-numeric characters
        String s = text.replaceAll("[\\s\\p{Z}\\u00A0\\u202F]", "").replaceAll("[^0-9,.-]", "");
        if (s.isEmpty() || s.equals(".") || s.equals("-") || s.equals(",")) return 0.0;

        int lastComma = s.lastIndexOf(',');
        int lastDot = s.lastIndexOf('.');

        if (lastComma != -1 && lastDot != -1) {
            if (lastComma > lastDot) {
                // French formatted number with dot thousands separator (e.g. 2.500,00)
                s = s.replace(".", "").replace(',', '.');
            } else {
                // US formatted number with comma thousands separator (e.g. 2,500.00)
                s = s.replace(",", "");
            }
        } else if (lastComma != -1) {
            // French decimal comma (e.g. 2500,00)
            s = s.replace(',', '.');
        }

        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public static String formatNumber(double val) {
        return FRENCH_NF.format(val);
    }

    public static String formatPhoneNumber(String phone) {
        if (phone == null || phone.trim().isEmpty()) return "Aucun numéro";
        String digits = phone.replaceAll("\\D+", "");
        if (digits.length() == 10) {
            return digits.substring(0, 4) + " " + digits.substring(4, 6) + " " + digits.substring(6, 8) + " " + digits.substring(8, 10);
        }
        return phone;
    }

    private static String getInitials(String name) {
        if (name == null || name.trim().isEmpty()) return "??";
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2) {
            return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
        }
        return name.substring(0, Math.min(2, name.length())).toUpperCase();
    }

    // =========================================================================
    // Custom ListCell for Floating Popup
    // =========================================================================
    private class PatientListCell extends ListCell<Patient> {
        @Override
        protected void updateItem(Patient patient, boolean empty) {
            super.updateItem(patient, empty);

            if (empty || patient == null) {
                if (filteredPatients.isEmpty() && getIndex() == 0) {
                    Label noRes = new Label("Aucun patient trouvé");
                    noRes.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px; -fx-font-style: italic; -fx-padding: 8 12;");
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
            row.setPadding(new Insets(6, 10, 6, 10));

            // 1. 30px Avatar
            Label avatar = new Label(getInitials(patient.getName()));
            avatar.setMinSize(30, 30);
            avatar.setMaxSize(30, 30);
            avatar.setPrefSize(30, 30);
            avatar.setAlignment(Pos.CENTER);
            avatar.setStyle("-fx-background-color: #e6f6f3; -fx-text-fill: #0f766e; -fx-background-radius: 15; -fx-font-weight: bold; -fx-font-size: 11px;");

            // 2. Name & Phone VBox
            VBox textCol = new VBox(2);
            HBox.setHgrow(textCol, Priority.ALWAYS);

            TextFlow nameFlow = createHighlightedTextFlow(patient.getName(), currentSearchQuery);
            Label phoneLbl = new Label(formatPhoneNumber(patient.getPhone()));
            phoneLbl.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");
            textCol.getChildren().addAll(nameFlow, phoneLbl);

            // 3. Spacer
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            // 4. Balance Badge
            Label badge = new Label();
            badge.setMinWidth(Region.USE_PREF_SIZE);
            double bal = patient.getBalance();
            if (bal > 0.01) {
                badge.setText(String.format("Dû %s DA", formatNumber(bal)));
                badge.setStyle("-fx-background-color: #fde8e8; -fx-text-fill: #b91c1c; -fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 9999px; -fx-padding: 3 8;");
            } else if (bal < -0.01) {
                badge.setText(String.format("Crédit %s DA", formatNumber(Math.abs(bal))));
                badge.setStyle("-fx-background-color: #e0f2fe; -fx-text-fill: #0369a1; -fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 9999px; -fx-padding: 3 8;");
            } else {
                badge.setText("Soldé");
                badge.setStyle("-fx-background-color: #e6f6ec; -fx-text-fill: #15803d; -fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 9999px; -fx-padding: 3 8;");
            }

            row.getChildren().addAll(avatar, textCol, spacer, badge);
            setGraphic(row);
            setText(null);
        }

        private TextFlow createHighlightedTextFlow(String name, String query) {
            TextFlow flow = new TextFlow();
            if (name == null) return flow;

            if (query == null || query.trim().isEmpty()) {
                Text t = new Text(name);
                t.setStyle("-fx-fill: #0f172a; -fx-font-weight: 600; -fx-font-size: 13px;");
                flow.getChildren().add(t);
                return flow;
            }

            String lowerName = name.toLowerCase();
            String lowerQuery = query.trim().toLowerCase();
            int idx = lowerName.indexOf(lowerQuery);

            if (idx < 0) {
                Text t = new Text(name);
                t.setStyle("-fx-fill: #0f172a; -fx-font-weight: 600; -fx-font-size: 13px;");
                flow.getChildren().add(t);
                return flow;
            }

            if (idx > 0) {
                Text before = new Text(name.substring(0, idx));
                before.setStyle("-fx-fill: #0f172a; -fx-font-weight: 600; -fx-font-size: 13px;");
                flow.getChildren().add(before);
            }

            Text match = new Text(name.substring(idx, idx + lowerQuery.length()));
            match.setStyle("-fx-fill: #0f766e; -fx-font-weight: bold; -fx-font-size: 13px;");
            flow.getChildren().add(match);

            if (idx + lowerQuery.length() < name.length()) {
                Text after = new Text(name.substring(idx + lowerQuery.length()));
                after.setStyle("-fx-fill: #0f172a; -fx-font-weight: 600; -fx-font-size: 13px;");
                flow.getChildren().add(after);
            }

            return flow;
        }
    }
}
