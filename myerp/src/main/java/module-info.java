module com.myerp {
    // JavaFX modules needed
    requires transitive javafx.controls;
    requires transitive javafx.graphics;
    requires transitive javafx.base;
    requires javafx.fxml;

    // JDBC for SQLite
    requires transitive java.sql;

    // Apache PDFBox for invoice PDF generation
    requires org.apache.pdfbox;
    requires java.desktop;

    // Open packages to JavaFX for reflection (FXML loading + CSS)
    opens com.myerp to javafx.graphics;
    opens controller to javafx.fxml;

    // Open model package for TableView PropertyValueFactory bindings
    opens model to javafx.base;
    opens util to javafx.base;

    // Export packages for public access
    exports config;
    exports dao;
    exports model;
    exports util;
    exports controller;
    exports com.myerp;
}
