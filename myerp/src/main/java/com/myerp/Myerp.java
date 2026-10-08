package com.myerp;

import config.AppConfig;
import config.DatabaseConfig;
import config.DatabaseInitializer;
import controller.WorkstationSetupController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.Locale;

public class Myerp extends Application {

    public static void main(String[] args) {
        Locale.setDefault(Locale.FRENCH);
        launch(args); // starts the JavaFX lifecycle → calls start()
    }

    @Override
    public void start(Stage stage) throws Exception {
        // 1. First-run workstation role configuration
        if (!AppConfig.isConfigured()) {
            FXMLLoader setupLoader = new FXMLLoader(getClass().getResource("/fxml/workstation_setup.fxml"));
            Parent setupRoot = setupLoader.load();
            WorkstationSetupController setupController = setupLoader.getController();

            Stage setupStage = new Stage();
            setupStage.initModality(Modality.APPLICATION_MODAL);
            setupStage.setTitle("Configuration du Poste — Cabinet Nour El Islam");
            setupStage.setResizable(false);
            setupStage.setScene(new Scene(setupRoot));
            setupStage.showAndWait();

            if (!setupController.isConfigured() || !AppConfig.isConfigured()) {
                Platform.exit();
                System.exit(0);
                return;
            }
        }

        // 2. Initialize database if Host
        if (AppConfig.isHost()) {
            DatabaseInitializer.initializeDatabase();
        }

        // 3. Show Login window
        Parent root = FXMLLoader.load(getClass().getResource("/fxml/login.fxml"));
        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

        stage.setTitle("Cabinet Nour El Islam");
        stage.setResizable(false);
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() throws Exception {
        DatabaseConfig.shutdown();
        super.stop();
    }
}
