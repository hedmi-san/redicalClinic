package dao;

import config.DatabaseConfig;
import model.Patient;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PatientDAO {

    public List<Patient> getAllPatients() {
        List<Patient> patients = new ArrayList<>();
        String query = "SELECT * FROM patient ORDER BY name ASC";

        try (Connection conn = DatabaseConfig.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                patients.add(mapResultSetToPatient(rs));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching all patients: " + e.getMessage());
        }
        return patients;
    }

    public List<Patient> searchPatients(String searchTerm) {
        List<Patient> patients = new ArrayList<>();
        String query = "SELECT * FROM patient WHERE name LIKE ? OR phone LIKE ? ORDER BY name ASC";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {

            String likeTerm = "%" + searchTerm + "%";
            pstmt.setString(1, likeTerm);
            pstmt.setString(2, likeTerm);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    patients.add(mapResultSetToPatient(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error searching patients: " + e.getMessage());
        }
        return patients;
    }

    public boolean addPatient(Patient patient) {
        String query = "INSERT INTO patient (name, gender, phone, totalCost, totalPaid) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, patient.getName());
            pstmt.setString(2, patient.getGender());
            pstmt.setString(3, patient.getPhone());
            pstmt.setDouble(4, 0.0);
            pstmt.setDouble(5, 0.0);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        patient.setId(generatedKeys.getInt(1));
                    }
                }
                return true;
            }
            return false;
        } catch (SQLException e) {
            System.err.println("Error adding patient: " + e.getMessage());
            return false;
        }
    }

    public boolean updatePatient(Patient patient) {
        String query = "UPDATE patient SET name = ?, gender = ?, phone = ? WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, patient.getName());
            pstmt.setString(2, patient.getGender());
            pstmt.setString(3, patient.getPhone());
            pstmt.setInt(4, patient.getId());

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating patient: " + e.getMessage());
            return false;
        }
    }

    public boolean deletePatient(int id) {
        String query = "DELETE FROM patient WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error deleting patient: " + e.getMessage());
            return false;
        }
    }

    public Patient getPatientById(int id) {
        String query = "SELECT * FROM patient WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToPatient(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching patient by id: " + e.getMessage());
        }
        return null;
    }

    public boolean patientExists(String name, String phone, int excludeId) {
        String query;
        if (phone == null || phone.trim().isEmpty()) {
            query = "SELECT COUNT(*) FROM patient WHERE LOWER(name) = LOWER(?) AND (phone IS NULL OR phone = '') AND id != ?";
        } else {
            query = "SELECT COUNT(*) FROM patient WHERE LOWER(name) = LOWER(?) AND phone = ? AND id != ?";
        }
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, name.trim());
            if (phone == null || phone.trim().isEmpty()) {
                pstmt.setInt(2, excludeId);
            } else {
                pstmt.setString(2, phone.trim());
                pstmt.setInt(3, excludeId);
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error checking if patient exists: " + e.getMessage());
        }
        return false;
    }

    public void updatePatientTotals(int patientId) {
        String query = """
                    UPDATE patient
                    SET totalCost = (SELECT COALESCE(SUM(cost), 0) FROM session WHERE patientId = ? AND therapyPlanId IS NULL) +
                                    (SELECT COALESCE(SUM(cost), 0) FROM therapyPlan WHERE patientId = ?),
                        totalPaid = (SELECT COALESCE(SUM(paidAmount), 0) FROM session WHERE patientId = ? AND therapyPlanId IS NULL) +
                                    (SELECT COALESCE(SUM(cost), 0) FROM therapyPlan WHERE patientId = ?)
                    WHERE id = ?
                """;

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, patientId);
            pstmt.setInt(2, patientId);
            pstmt.setInt(3, patientId);
            pstmt.setInt(4, patientId);
            pstmt.setInt(5, patientId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error updating patient totals: " + e.getMessage());
        }
    }

    private Patient mapResultSetToPatient(ResultSet rs) throws SQLException {
        Patient patient = new Patient();
        patient.setId(rs.getInt("id"));
        patient.setName(rs.getString("name"));
        patient.setGender(rs.getString("gender"));
        patient.setPhone(rs.getString("phone"));
        patient.setTotalCost(rs.getDouble("totalCost"));
        patient.setTotalPaid(rs.getDouble("totalPaid"));
        return patient;
    }
}
