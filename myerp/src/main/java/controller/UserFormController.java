package controller;

import dao.UserDAO;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import model.User;

import java.net.URL;
import java.util.ResourceBundle;

public class UserFormController implements Initializable {

    @FXML
    private VBox rootVBox;
    @FXML
    private Label lblTitle;
    @FXML
    private TextField txtFullName;
    @FXML
    private TextField txtUserName;
    @FXML
    private PasswordField txtPassword;
    @FXML
    private TextField txtPasswordVisible;
    @FXML
    private Button btnTogglePassword;
    @FXML
    private ComboBox<String> cbRole;
    @FXML
    private Label lblPasswordHint;

    private UserDAO userDAO;
    private User userToEdit;
    private boolean isSaveSuccessful = false;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        String cssPath = getClass().getResource("/css/pages/user_form.css").toExternalForm();
        if (cssPath != null) {
            rootVBox.getStylesheets().add(cssPath);
        }

        cbRole.setItems(FXCollections.observableArrayList("Admin", "Employé"));
        cbRole.setValue("Employé");

        // Sync text between the hidden TextField and the PasswordField
        if (txtPassword != null && txtPasswordVisible != null) {
            txtPasswordVisible.textProperty().bindBidirectional(txtPassword.textProperty());
        }
    }

    public void setUserDAO(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    public void setUserData(User user) {
        this.userToEdit = user;
        if (user != null) {
            lblTitle.setText("Modifier l'Utilisateur");
            txtFullName.setText(user.getFullName());
            txtUserName.setText(user.getUserName());

            String role = "Admin".equalsIgnoreCase(user.getUserType()) ? "Admin" : "Employé";
            cbRole.setValue(role);

            // Show password hint for edit mode
            if (lblPasswordHint != null) {
                lblPasswordHint.setVisible(true);
                lblPasswordHint.setManaged(true);
            }
        } else {
            lblTitle.setText("Ajouter un Utilisateur");
            cbRole.setValue("Employé");
            if (lblPasswordHint != null) {
                lblPasswordHint.setVisible(false);
                lblPasswordHint.setManaged(false);
            }
        }
    }

    @FXML
    private void togglePasswordVisibility() {
        if (txtPasswordVisible.isVisible()) {
            txtPasswordVisible.setVisible(false);
            txtPasswordVisible.setManaged(false);
            txtPassword.setVisible(true);
            txtPassword.setManaged(true);
        } else {
            txtPasswordVisible.setVisible(true);
            txtPasswordVisible.setManaged(true);
            txtPassword.setVisible(false);
            txtPassword.setManaged(false);
        }
    }

    @FXML
    void handleSave(ActionEvent event) {
        String fullName = txtFullName.getText() != null ? txtFullName.getText().trim() : "";
        String userName = txtUserName.getText() != null ? txtUserName.getText().trim() : "";
        String password = txtPassword.getText() != null ? txtPassword.getText().trim() : "";
        String role = cbRole.getValue();

        // 1. Validate required fields
        if (fullName.isEmpty()) {
            showAlert("Champ requis", "Veuillez saisir le nom complet de l'utilisateur.", Alert.AlertType.WARNING);
            return;
        }

        if (userName.isEmpty()) {
            showAlert("Champ requis", "Veuillez saisir le nom d'utilisateur.", Alert.AlertType.WARNING);
            return;
        }

        if (role == null || role.trim().isEmpty()) {
            showAlert("Champ requis", "Veuillez sélectionner un rôle.", Alert.AlertType.WARNING);
            return;
        }

        if (userToEdit == null && password.isEmpty()) {
            showAlert("Champ requis", "Veuillez saisir un mot de passe pour le nouvel utilisateur.", Alert.AlertType.WARNING);
            return;
        }

        // 2. Validate username uniqueness
        int excludeId = userToEdit != null ? userToEdit.getId() : -1;
        if (userDAO != null && userDAO.isUsernameTaken(userName, excludeId)) {
            showAlert("Nom d'utilisateur déjà pris",
                    "Le nom d'utilisateur '" + userName + "' est déjà utilisé par un autre compte. Veuillez en choisir un autre.",
                    Alert.AlertType.WARNING);
            return;
        }

        // 3. Guard against demoting the last remaining administrator
        if (userToEdit != null && "Admin".equalsIgnoreCase(userToEdit.getUserType()) && !"Admin".equalsIgnoreCase(role)) {
            if (userDAO != null && userDAO.countAdmins() <= 1) {
                showAlert("Action impossible",
                        "Impossible de modifier le rôle : le système doit conserver au moins un administrateur.",
                        Alert.AlertType.ERROR);
                return;
            }
        }

        // 4. Save or update
        if (userToEdit == null) {
            User newUser = new User(0, fullName, userName, password, role);
            isSaveSuccessful = userDAO.addUser(newUser);
        } else {
            userToEdit.setFullName(fullName);
            userToEdit.setUserName(userName);
            userToEdit.setUserType(role);
            if (!password.isEmpty()) {
                userToEdit.setPassWord(password);
            }
            isSaveSuccessful = userDAO.updateUser(userToEdit);
        }

        if (isSaveSuccessful) {
            closeDialog();
        } else {
            showAlert("Erreur", "Une erreur est survenue lors de l'enregistrement de l'utilisateur.", Alert.AlertType.ERROR);
        }
    }

    @FXML
    void handleCancel(ActionEvent event) {
        closeDialog();
    }

    public boolean isSaveSuccessful() {
        return isSaveSuccessful;
    }

    private void closeDialog() {
        Stage stage = (Stage) rootVBox.getScene().getWindow();
        stage.close();
    }

    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
