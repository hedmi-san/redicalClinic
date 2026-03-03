package com.myerp;

import config.DatabaseInitializer;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;


public class Myerp {

    public static void main(String[] args) {
        // Initialize the database and create all tables on first run
        DatabaseInitializer.initializeDatabase();

        System.out.println("\nApplication is ready. Starting UI...");
        // TODO: launch JavaFX Application stage here
        
    }
}
