package controller;

import dao.UserDAO;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import model.User;

public class LoginController {

    @FXML
    private TextField userNameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Button loginButton;

    @FXML
    private void handleLogin() {
        String userName = userNameField.getText().trim();
        String password = passwordField.getText().trim();

        if (userName.isEmpty() || password.isEmpty()) {
            showAlert(Alert.AlertType.WARNING,
                    "Champs manquants",
                    "Veuillez remplir le nom d'utilisateur et le mot de passe.");
            return;
        }

        User user = UserDAO.login(userName, password);

        if (user != null) {
            showAlert(Alert.AlertType.INFORMATION,
                    "Connexion réussie",
                    "Bienvenue, " + user.getFullName() + " !\nType: " + user.getUserType());
            // TODO: close login window and open Dashboard
        } else {
            showAlert(Alert.AlertType.ERROR,
                    "Échec de la connexion",
                    "Nom d'utilisateur ou mot de passe incorrect.");
            passwordField.clear();
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
