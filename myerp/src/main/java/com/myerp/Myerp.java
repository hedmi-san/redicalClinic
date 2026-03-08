package com.myerp;

import config.DatabaseInitializer;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Locale;

public class Myerp extends Application {

    public static void main(String[] args) {
        Locale.setDefault(Locale.FRENCH);
        DatabaseInitializer.initializeDatabase();
        launch(args); // starts the JavaFX lifecycle → calls start()
    }

    @Override
    public void start(Stage stage) throws Exception {
        Parent root = FXMLLoader.load(
                getClass().getResource("/fxml/login.fxml"));

        Scene scene = new Scene(root);
        scene.getStylesheets().add(
                getClass().getResource("/css/style.css").toExternalForm());

        stage.setTitle("Cabinet Nour El Islam");
        stage.setResizable(false);
        stage.setScene(scene);
        stage.show(); // ← opens the window
    }
}
