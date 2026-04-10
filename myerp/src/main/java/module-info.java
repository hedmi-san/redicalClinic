module com.myerp {
    // JavaFX modules needed
    requires javafx.controls;
    requires javafx.fxml;

    // JDBC for SQLite
    requires java.sql;

    // Apache PDFBox for invoice PDF generation
    requires org.apache.pdfbox;
    requires java.desktop;

    // Open packages to JavaFX for reflection (FXML loading + CSS)
    opens com.myerp to javafx.graphics;
    opens controller to javafx.fxml;

    // Open model package for TableView PropertyValueFactory bindings
    opens model to javafx.base;
}
