package dao;

import config.DatabaseConfig;
import model.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

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
                return mapResultSetToUser(rs);
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

    /**
     * Fetches all registered users ordered by id.
     */
    public List<User> getAllUsers() {
        List<User> users = new ArrayList<>();
        String query = "SELECT * FROM Users ORDER BY id ASC";

        try (Connection conn = DatabaseConfig.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                users.add(mapResultSetToUser(rs));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching all users: " + e.getMessage());
        }
        return users;
    }

    /**
     * Searches users matching the query in full name or username.
     */
    public List<User> searchUsers(String query) {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM Users WHERE fullName LIKE ? OR userName LIKE ? ORDER BY id ASC";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            String term = "%" + query.trim() + "%";
            ps.setString(1, term);
            ps.setString(2, term);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    users.add(mapResultSetToUser(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error searching users: " + e.getMessage());
        }
        return users;
    }

    /**
     * Adds a new user to the database.
     */
    public boolean addUser(User user) {
        String sql = "INSERT INTO Users (fullName, userName, passWord, userType) VALUES (?, ?, ?, ?)";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, user.getFullName());
            ps.setString(2, user.getUserName());
            ps.setString(3, user.getPassWord());
            ps.setString(4, user.getUserType());

            int rows = ps.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("Error adding user: " + e.getMessage());
            return false;
        }
    }

    /**
     * Updates an existing user. If the password is provided and not empty, updates password too.
     */
    public boolean updateUser(User user) {
        boolean updatePassword = user.getPassWord() != null && !user.getPassWord().trim().isEmpty();
        String sql = updatePassword
                ? "UPDATE Users SET fullName = ?, userName = ?, passWord = ?, userType = ? WHERE id = ?"
                : "UPDATE Users SET fullName = ?, userName = ?, userType = ? WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            if (updatePassword) {
                ps.setString(1, user.getFullName());
                ps.setString(2, user.getUserName());
                ps.setString(3, user.getPassWord());
                ps.setString(4, user.getUserType());
                ps.setInt(5, user.getId());
            } else {
                ps.setString(1, user.getFullName());
                ps.setString(2, user.getUserName());
                ps.setString(3, user.getUserType());
                ps.setInt(4, user.getId());
            }

            int rows = ps.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("Error updating user: " + e.getMessage());
            return false;
        }
    }

    /**
     * Deletes a user by ID.
     */
    public boolean deleteUser(int userId) {
        String sql = "DELETE FROM Users WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            int rows = ps.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("Error deleting user: " + e.getMessage());
            return false;
        }
    }

    /**
     * Checks if a username is already taken by another user.
     */
    public boolean isUsernameTaken(String userName, int excludeId) {
        String sql = "SELECT COUNT(*) FROM Users WHERE LOWER(userName) = LOWER(?) AND id != ?";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userName.trim());
            ps.setInt(2, excludeId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error checking username uniqueness: " + e.getMessage());
        }
        return false;
    }

    /**
     * Returns the total number of users with the Admin role.
     */
    public int countAdmins() {
        String sql = "SELECT COUNT(*) FROM Users WHERE LOWER(userType) = 'admin'";

        try (Connection conn = DatabaseConfig.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("Error counting admins: " + e.getMessage());
        }
        return 0;
    }

    private static User mapResultSetToUser(ResultSet rs) throws SQLException {
        return new User(
                rs.getInt("id"),
                rs.getString("fullName"),
                rs.getString("userName"),
                rs.getString("passWord"),
                rs.getString("userType"));
    }
}
