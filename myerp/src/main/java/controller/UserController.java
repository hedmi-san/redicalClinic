package controller;

import dao.UserDAO;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import model.User;
import util.UserSession;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

public class UserController implements Initializable {

    @FXML
    private VBox mainContainer;
    @FXML
    private TextField searchField;
    @FXML
    private TableView<User> userTable;
    @FXML
    private TableColumn<User, String> colFullName;
    @FXML
    private TableColumn<User, String> colUserName;
    @FXML
    private TableColumn<User, String> colRole;
    @FXML
    private Button btnAdd;
    @FXML
    private Button btnEdit;
    @FXML
    private Button btnDelete;

    private UserDAO userDAO;
    private ObservableList<User> userList;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Load CSS programmatically
        String cssPath = getClass().getResource("/css/pages/user.css").toExternalForm();
        if (cssPath != null) {
            mainContainer.getStylesheets().add(cssPath);
        }

        userDAO = new UserDAO();
        userList = FXCollections.observableArrayList();

        colFullName.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        colUserName.setCellValueFactory(new PropertyValueFactory<>("userName"));

        // Format role column with clear labels
        colRole.setCellValueFactory(new PropertyValueFactory<>("userType"));
        colRole.setCellFactory(column -> new TableCell<User, String>() {
            @Override
            protected void updateItem(String role, boolean empty) {
                super.updateItem(role, empty);
                if (empty || role == null) {
                    setText(null);
                    setStyle("");
                } else {
                    if ("Admin".equalsIgnoreCase(role.trim())) {
                        setText("Administrateur");
                        setStyle("-fx-text-fill: #2563eb; -fx-font-weight: bold;");
                    } else {
                        setText("Employé");
                        setStyle("-fx-text-fill: #059669; -fx-font-weight: bold;");
                    }
                }
            }
        });

        // Disable column reordering
        colFullName.setReorderable(false);
        colUserName.setReorderable(false);
        colRole.setReorderable(false);

        userTable.setItems(userList);

        // Real-time search listener
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            loadUsers(newValue);
        });

        // Double-click to edit user
        userTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && userTable.getSelectionModel().getSelectedItem() != null) {
                openUserForm(userTable.getSelectionModel().getSelectedItem());
            }
        });

        loadUsers("");
    }

    private void loadUsers(String query) {
        userList.clear();
        List<User> users;
        if (query == null || query.trim().isEmpty()) {
            users = userDAO.getAllUsers();
        } else {
            users = userDAO.searchUsers(query.trim());
        }
        userList.addAll(users);
    }

    @FXML
    void handleAddAction(ActionEvent event) {
        openUserForm(null);
    }

    @FXML
    void handleEditAction(ActionEvent event) {
        User selectedUser = userTable.getSelectionModel().getSelectedItem();
        if (selectedUser == null) {
            showAlert("Aucune sélection", "Veuillez sélectionner un utilisateur à modifier.", Alert.AlertType.WARNING);
            return;
        }
        openUserForm(selectedUser);
    }

    @FXML
    void handleDeleteAction(ActionEvent event) {
        User selectedUser = userTable.getSelectionModel().getSelectedItem();
        if (selectedUser == null) {
            showAlert("Aucune sélection", "Veuillez sélectionner un utilisateur à supprimer.", Alert.AlertType.WARNING);
            return;
        }

        // Guard 1: Prevent self-deletion
        if (UserSession.isLoggedIn() && UserSession.getCurrentUser().getId() == selectedUser.getId()) {
            showAlert("Action impossible", "Vous ne pouvez pas supprimer votre propre compte actuellement connecté.",
                    Alert.AlertType.ERROR);
            return;
        }

        // Guard 2: Prevent deletion of the last administrator
        if ("Admin".equalsIgnoreCase(selectedUser.getUserType()) && userDAO.countAdmins() <= 1) {
            showAlert("Action impossible", "Impossible de supprimer cet utilisateur : le système doit conserver au moins un administrateur.",
                    Alert.AlertType.ERROR);
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmer la suppression");
        alert.setHeaderText("Supprimer l'utilisateur");
        alert.setContentText("Voulez-vous supprimer l'utilisateur : " + selectedUser.getFullName() + " (" + selectedUser.getUserName() + ") ?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            boolean success = userDAO.deleteUser(selectedUser.getId());
            if (success) {
                loadUsers(searchField.getText());
            } else {
                showAlert("Erreur", "Échec de la suppression de l'utilisateur dans la base de données.",
                        Alert.AlertType.ERROR);
            }
        }
    }

    private void openUserForm(User user) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/pages/user_form.fxml"));
            Parent root = loader.load();

            UserFormController controller = loader.getController();
            controller.setUserDAO(userDAO);
            controller.setUserData(user);

            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.setTitle(user == null ? "Ajouter un utilisateur" : "Modifier l'utilisateur");
            dialogStage.setScene(new Scene(root));
            dialogStage.setResizable(false);

            dialogStage.showAndWait();

            if (controller.isSaveSuccessful()) {
                loadUsers(searchField.getText());
            }

        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Erreur", "Échec de l'ouverture du formulaire utilisateur.", Alert.AlertType.ERROR);
        }
    }

    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
