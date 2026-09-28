package com.myerp;

import config.DatabaseConfig;
import config.DatabaseInitializer;
import dao.UserDAO;
import model.User;
import util.UserSession;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class UserManagementVerificationTest {

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  RUNNING USER MANAGEMENT & ROLES VERIFICATION   ");
        System.out.println("==================================================");

        try {
            // 1. Initialize DB & Test Pre-Launch Backup
            System.out.println("\n[Test 1] Testing Database Initialization & Backup Routine...");
            DatabaseInitializer.initializeDatabase();

            Path dbPath = Paths.get(DatabaseConfig.getDatabasePath());
            if (!Files.exists(dbPath)) {
                throw new AssertionError("clinic.db does not exist!");
            }
            System.out.println("  ✓ Database exists at: " + dbPath);

            Path backupDir = dbPath.getParent().resolve("backups");
            if (Files.exists(backupDir) && Files.list(backupDir).count() > 0) {
                System.out.println("  ✓ Backup directory verified with backup files created in: " + backupDir);
            } else {
                System.out.println("  ℹ First launch: backup directory will be populated on subsequent starts.");
            }

            // Trigger a second initialization to verify backup creation of existing DB
            DatabaseInitializer.initializeDatabase();
            if (!Files.exists(backupDir) || Files.list(backupDir).count() == 0) {
                throw new AssertionError("Pre-launch backup was not created for existing clinic.db!");
            }
            System.out.println("  ✓ Pre-launch backup successfully verified on existing database.");

            // 2. Test UserDAO Authentication & Admin Seeding
            System.out.println("\n[Test 2] Testing Default Admin Seeding & Authentication...");
            User admin = UserDAO.login("admin", "admin123");
            if (admin == null) {
                throw new AssertionError("Failed to log in as default admin!");
            }
            if (!"Admin".equalsIgnoreCase(admin.getUserType())) {
                throw new AssertionError("Admin userType should be 'Admin' but was: " + admin.getUserType());
            }
            System.out.println("  ✓ Logged in as default admin: " + admin.getFullName() + " [Role: " + admin.getUserType() + "]");

            // 3. Test UserSession
            System.out.println("\n[Test 3] Testing UserSession Context & Roles...");
            UserSession.setCurrentUser(admin);
            if (!UserSession.isLoggedIn()) throw new AssertionError("UserSession.isLoggedIn() should be true");
            if (!UserSession.isAdmin()) throw new AssertionError("UserSession.isAdmin() should be true for Admin");
            if (UserSession.isEmployee()) throw new AssertionError("UserSession.isEmployee() should be false for Admin");
            if (!"Administrateur".equals(UserSession.getDisplayRole())) {
                throw new AssertionError("Expected 'Administrateur' but got: " + UserSession.getDisplayRole());
            }
            System.out.println("  ✓ Admin UserSession verified: " + UserSession.getDisplayRole());

            User empTestSession = new User(999, "Sara Test", "sara", "pass", "Employé");
            UserSession.setCurrentUser(empTestSession);
            if (UserSession.isAdmin()) throw new AssertionError("UserSession.isAdmin() should be false for Employé");
            if (!UserSession.isEmployee()) throw new AssertionError("UserSession.isEmployee() should be true for Employé");
            if (!"Employé".equals(UserSession.getDisplayRole())) {
                throw new AssertionError("Expected 'Employé' but got: " + UserSession.getDisplayRole());
            }
            System.out.println("  ✓ Employee UserSession verified: " + UserSession.getDisplayRole());

            UserSession.clear();
            if (UserSession.isLoggedIn()) throw new AssertionError("UserSession.isLoggedIn() should be false after clear()");
            System.out.println("  ✓ UserSession clear() verified.");

            // 4. Test UserDAO CRUD & Search Operations
            System.out.println("\n[Test 4] Testing UserDAO CRUD & Search Operations...");
            UserDAO userDAO = new UserDAO();

            int initialAdminCount = userDAO.countAdmins();
            if (initialAdminCount < 1) throw new AssertionError("countAdmins() should be >= 1");
            System.out.println("  ✓ Current admin count: " + initialAdminCount);

            // Clean up any previous test user
            List<User> existing = userDAO.searchUsers("test_emp_unique");
            for (User u : existing) {
                userDAO.deleteUser(u.getId());
            }

            // Create new user
            User newEmp = new User(0, "Agent Verification", "test_emp_unique", "secret123", "Employé");
            boolean created = userDAO.addUser(newEmp);
            if (!created) throw new AssertionError("Failed to create new user!");
            System.out.println("  ✓ User 'test_emp_unique' created successfully.");

            // Check uniqueness check
            if (!userDAO.isUsernameTaken("test_emp_unique", -1)) {
                throw new AssertionError("isUsernameTaken should return true for existing username!");
            }
            if (userDAO.isUsernameTaken("completely_non_existent_username", -1)) {
                throw new AssertionError("isUsernameTaken should return false for unused username!");
            }
            System.out.println("  ✓ Username uniqueness check verified.");

            // Find created user
            List<User> searchResults = userDAO.searchUsers("test_emp_unique");
            if (searchResults.isEmpty()) throw new AssertionError("User search failed to find 'test_emp_unique'");
            User createdUser = searchResults.get(0);
            System.out.println("  ✓ User search returned: " + createdUser.getFullName() + " (id=" + createdUser.getId() + ")");

            // Update user
            createdUser.setFullName("Agent Verification Updated");
            boolean updated = userDAO.updateUser(createdUser);
            if (!updated) throw new AssertionError("Failed to update user!");
            List<User> updatedResults = userDAO.searchUsers("Agent Verification Updated");
            if (updatedResults.isEmpty()) throw new AssertionError("Failed to find updated user by new name");
            System.out.println("  ✓ User updated successfully.");

            // Delete user
            boolean deleted = userDAO.deleteUser(createdUser.getId());
            if (!deleted) throw new AssertionError("Failed to delete user!");
            List<User> afterDelete = userDAO.searchUsers("test_emp_unique");
            if (!afterDelete.isEmpty()) throw new AssertionError("User still exists after deletion!");
            System.out.println("  ✓ User deleted successfully.");

            System.out.println("\n==================================================");
            System.out.println("   ALL 4 VERIFICATION TESTS PASSED SUCCESSFULLY!  ");
            System.out.println("==================================================");
            System.exit(0);

        } catch (Exception e) {
            System.err.println("\n❌ VERIFICATION TEST FAILED: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
