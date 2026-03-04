package controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class HomeController implements Initializable {

    @FXML
    private StackPane contentArea;
    @FXML
    private HBox titleBar;

    @FXML
    private Button btnAccueil;
    @FXML
    private Button btnPatients;
    @FXML
    private Button btnSessions;
    @FXML
    private Button btnWorkers;
    @FXML
    private Button btnBills;

    private Button activeButton;
    private double x = 0;
    private double y = 0;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Setup window dragging on the title bar
        if (titleBar != null) {
            titleBar.setOnMousePressed((MouseEvent event) -> {
                x = event.getSceneX();
                y = event.getSceneY();
            });
            titleBar.setOnMouseDragged((MouseEvent event) -> {
                Stage stage = (Stage) titleBar.getScene().getWindow();
                stage.setX(event.getScreenX() - x);
                stage.setY(event.getScreenY() - y);
            });
        }

        // Load the default page on startup
        handleNavAccueil(null);
    }

    @FXML
    private void handleMinimize(ActionEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setIconified(true);
    }

    @FXML
    private void handleMaximize(ActionEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setMaximized(!stage.isMaximized());
    }

    @FXML
    private void handleClose(ActionEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
    }

    @FXML
    private void handleNavAccueil(ActionEvent event) {
        loadPage("/fxml/pages/dashboard.fxml");
        setActiveButton(btnAccueil);
    }

    @FXML
    private void handleNavPatients(ActionEvent event) {
        loadPage("/fxml/pages/patient.fxml");
        setActiveButton(btnPatients);
    }

    @FXML
    private void handleNavSessions(ActionEvent event) {
        loadPage("/fxml/pages/session.fxml");
        setActiveButton(btnSessions);
    }

    @FXML
    private void handleNavWorkers(ActionEvent event) {
        loadPage("/fxml/pages/worker.fxml");
        setActiveButton(btnWorkers);
    }

    @FXML
    private void handleNavBills(ActionEvent event) {
        loadPage("/fxml/pages/bill.fxml");
        setActiveButton(btnBills);
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        try {
            // Close the current dashboard window
            Node source = (Node) event.getSource();
            Stage currentStage = (Stage) source.getScene().getWindow();
            currentStage.close();

            // Open the Login window
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/login.fxml"));
            Stage loginStage = new Stage();
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

            // Allow dragging the window (if using transparent/undecorated)
            root.setOnMousePressed((MouseEvent mouseEvent) -> {
                x = mouseEvent.getSceneX();
                y = mouseEvent.getSceneY();
            });
            root.setOnMouseDragged((MouseEvent mouseEvent) -> {
                loginStage.setX(mouseEvent.getScreenX() - x);
                loginStage.setY(mouseEvent.getScreenY() - y);
            });

            loginStage.initStyle(StageStyle.TRANSPARENT);
            loginStage.setScene(scene);
            loginStage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Loads an FXML file into the central content area.
     */
    private void loadPage(String fxmlPath) {
        try {
            Parent page = FXMLLoader.load(getClass().getResource(fxmlPath));
            contentArea.getChildren().clear();
            contentArea.getChildren().add(page);
        } catch (IOException e) {
            System.err.println("Could not load page: " + fxmlPath);
            e.printStackTrace();
        }
    }

    /**
     * Updates the CSS class to visually indicate which menu item is active.
     */
    private void setActiveButton(Button button) {
        if (activeButton != null) {
            activeButton.getStyleClass().remove("nav-btn-active");
        }
        activeButton = button;
        if (activeButton != null) {
            activeButton.getStyleClass().add("nav-btn-active");
        }
    }
}
