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
        String query = "INSERT INTO patient (name, phone, totalCost, totalPaid) VALUES (?, ?, ?, ?)";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, patient.getName());
            pstmt.setString(2, patient.getPhone());
            pstmt.setDouble(3, 0.0);
            pstmt.setDouble(4, 0.0);

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
        String query = "UPDATE patient SET name = ?, phone = ? WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, patient.getName());
            pstmt.setString(2, patient.getPhone());
            pstmt.setInt(3, patient.getId());

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

    public void updatePatientTotals(int patientId) {
        String query = """
                    UPDATE patient
                    SET totalCost = (SELECT ifnull(SUM(cost), 0) FROM session WHERE patientId = ?),
                        totalPaid = (SELECT ifnull(SUM(paidAmount), 0) FROM session WHERE patientId = ?)
                    WHERE id = ?
                """;

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, patientId);
            pstmt.setInt(2, patientId);
            pstmt.setInt(3, patientId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error updating patient totals: " + e.getMessage());
        }
    }

    private Patient mapResultSetToPatient(ResultSet rs) throws SQLException {
        Patient patient = new Patient();
        patient.setId(rs.getInt("id"));
        patient.setName(rs.getString("name"));
        patient.setPhone(rs.getString("phone"));
        // Note: totalCost and totalPaid are properties in Patient model computed from
        // sessions list
        // but here we can keep it simple and just use the properties if we want to
        // avoid extra joins in list
        return patient;
    }
}
