package dao;

import config.DatabaseConfig;
import model.User;

import java.sql.*;

public class UserDAO {

    /**
     * Checks the database for a matching username + password.
     * 
     * @return a User object if credentials are valid, null otherwise.
     */
    public static User login(String userName, String password) {
        String sql = "SELECT * FROM Users WHERE userName = ? AND passWord = ?";
        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userName);
            ps.setString(2, password);

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new User(
                        rs.getInt("id"),
                        rs.getString("fullName"),
                        rs.getString("userName"),
                        rs.getString("passWord"),
                        rs.getString("userType"));
            }
        } catch (SQLException e) {
            System.err.println("Login error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Inserts a default admin user if the Users table is empty.
     * Default credentials: admin / admin123
     */
    public static void seedDefaultAdmin() {
        String checkSql = "SELECT COUNT(*) FROM Users";
        String insertSql = "INSERT INTO Users (fullName, userName, passWord, userType) VALUES (?, ?, ?, ?)";

        try (Connection conn = DatabaseConfig.getConnection();
                Statement checkStmt = conn.createStatement();
                PreparedStatement insertPs = conn.prepareStatement(insertSql)) {

            ResultSet rs = checkStmt.executeQuery(checkSql);
            if (rs.next() && rs.getInt(1) == 0) {
                insertPs.setString(1, "Administrateur");
                insertPs.setString(2, "admin");
                insertPs.setString(3, "admin123");
                insertPs.setString(4, "Admin");
                insertPs.executeUpdate();
                System.out.println("   Default admin user seeded (admin / admin123)");
            }
        } catch (SQLException e) {
            System.err.println("Error seeding admin user: " + e.getMessage());
        }
    }
}
