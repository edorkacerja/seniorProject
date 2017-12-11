package sample;

import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.sql.*;
import java.util.Base64;
import java.util.Collections;
import java.util.Optional;


public class Controller {

    /**
     * Fields used for encryption/decryption of passwords
     **/
    private static final String ALGO = "AES";
    private static final byte[] keyValue =
            new byte[]{'T', 'h', 'e', 'B', 'e', 's', 't', 'S', 'e', 'c', 'r', 'e', 't', 'K', 'e', 'y'};

    @FXML
    private RadioButton socialNetworkField;
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    private Connection connect;
    private PreparedStatement statement;


    public Controller() {
        try {
            Class.forName("com.mysql.jdbc.Driver");

            // Setup the connection with the DB
            connect = DriverManager
                    .getConnection("jdbc:mysql://localhost:3306/fingerprint_login", "aubgstudent", "aubgstudent");

            // Statements allow to issue SQL queries to the database
            statement = connect.prepareStatement("INSERT INTO `user` (`username`, `password_hash`, `social_network`) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            System.out.println("Database connection successful");
        } catch (ClassNotFoundException | SQLException e) {
            showExceptionAlert(e.toString());
            e.printStackTrace();
        }
    }


    public void registerUserButtonAction(ActionEvent actionEvent) {

        try {
            ObservableList<String> usernameFieldStyleClass = usernameField.getStyleClass();
            ObservableList<String> passwordFieldStyleClass = passwordField.getStyleClass();
            if (usernameField.getText().trim().length() == 0 || passwordField.getText().trim().length() == 0) {

                if (usernameField.getText().trim().length() == 0 && !usernameFieldStyleClass.contains("error")) {
                    usernameFieldStyleClass.add("error");
                } else if (usernameField.getText().trim().length() > 0 && usernameFieldStyleClass.contains("error")) {
                    usernameFieldStyleClass.remove("error");
                }
                if (passwordField.getText().trim().length() == 0 && !passwordFieldStyleClass.contains("error")) {
                    passwordFieldStyleClass.add("error");
                } else if (passwordField.getText().trim().length() > 0 && passwordFieldStyleClass.contains("error")) {
                    passwordFieldStyleClass.remove("error");
                }
            } else {
                if (usernameFieldStyleClass.contains("error")) {
                    usernameFieldStyleClass.removeAll(Collections.singleton("error"));
                }
                if (passwordFieldStyleClass.contains("error")) {
                    passwordFieldStyleClass.removeAll(Collections.singleton("error"));
                }

                String username = usernameField.getText();
                String passwordHash = encrypt(passwordField.getText());
                String socialNetwork = ((RadioButton) socialNetworkField.getToggleGroup().getSelectedToggle()).getText();

                // filter to see if the same data exists.
                PreparedStatement checkDuplicatesStatement = connect.prepareStatement("SELECT id from user where username=? AND social_network=?");
                checkDuplicatesStatement.setString(1, username);
                checkDuplicatesStatement.setString(2, socialNetwork);
                ResultSet rs = checkDuplicatesStatement.executeQuery();
                if (rs.next()) {
                    Optional result = showDuplicateDataAlert();
                    if (result.get() == ButtonType.OK) {
                        // ... user chose OK so remove all the data with those ids from the db
//                        "DELETE FROM `user` WHERE `user`.`id` = 2"
                        int id = rs.getInt("id");
                        Statement stm = connect.createStatement();
                        stm.executeUpdate("UPDATE `user` SET `password_hash`=\"" + passwordHash + "\" WHERE `id`=" + id);
                        showAlert(id);
                    }
                } else {
                    statement.setString(1, username);
                    statement.setString(2, passwordHash);
                    statement.setString(3, socialNetwork);

                    int affectedRows = statement.executeUpdate();

                    if (affectedRows == 0) {
                        throw new SQLException("Creating user failed, no rows affected.");
                    }

                    try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            int id = generatedKeys.getInt(1);
                            showAlert(id);
                        } else {
                            throw new SQLException("Creating user failed, no ID obtained.");
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

    public void showAlert(int id) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("SUCCESS");
        alert.setHeaderText("Your credentails were registered successfully! ");
        alert.setContentText("This is your user ID created for you: " + id + "\n Please use this ID to register your fingerprint with arduino");
        alert.showAndWait();
    }

    public void showExceptionAlert(String exception) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("ERROR");
        alert.setHeaderText("There was an error starting your application! Fix the errors below and start again!");
        alert.setContentText(exception);
        alert.showAndWait();
    }

    public Optional<ButtonType> showDuplicateDataAlert() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Duplicate Data found");
        alert.setHeaderText("There is already a registered account with this username!");
        alert.setContentText("Would you like to replace that account with yours?");
        Optional<ButtonType> result = alert.showAndWait();
        return result;
    }


    /**
     * Encrypt a string with AES algorithm.
     *
     * @param data is a string
     * @return the encrypted string
     */
    public static String encrypt(String data) throws Exception {
        Key key = generateKey();
        Cipher c = Cipher.getInstance(ALGO);
        c.init(Cipher.ENCRYPT_MODE, key);
        byte[] encVal = c.doFinal(data.getBytes());
        return Base64.getEncoder().encodeToString(encVal);
    }


    /**
     * Generate a new encryption key.
     */
    private static Key generateKey() throws Exception {
        return new SecretKeySpec(keyValue, ALGO);
    }

}
