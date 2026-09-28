package util;

import model.User;

/**
 * Manages the current authenticated user session in the application.
 */
public class UserSession {

    private static User currentUser;

    private UserSession() {
        // Prevent instantiation
    }

    public static void setCurrentUser(User user) {
        currentUser = user;
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static boolean isLoggedIn() {
        return currentUser != null;
    }

    /**
     * Checks if the active user has administrative privileges.
     */
    public static boolean isAdmin() {
        if (currentUser == null || currentUser.getUserType() == null) {
            return false;
        }
        return "Admin".equalsIgnoreCase(currentUser.getUserType().trim());
    }

    /**
     * Checks if the active user is an employee.
     */
    public static boolean isEmployee() {
        if (currentUser == null || currentUser.getUserType() == null) {
            return false;
        }
        String type = currentUser.getUserType().trim();
        return "Employé".equalsIgnoreCase(type) || "Employee".equalsIgnoreCase(type);
    }

    /**
     * Returns a user-friendly French label for the current user's role.
     */
    public static String getDisplayRole() {
        if (isAdmin()) {
            return "Administrateur";
        } else if (isEmployee()) {
            return "Employé";
        }
        return currentUser != null && currentUser.getUserType() != null ? currentUser.getUserType() : "";
    }

    /**
     * Clears the current session on logout.
     */
    public static void clear() {
        currentUser = null;
    }
}
