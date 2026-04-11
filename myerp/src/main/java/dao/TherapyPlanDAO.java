package dao;

import config.DatabaseConfig;
import model.TherapyPlan;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TherapyPlanDAO {

    public List<TherapyPlan> getTherapyPlansByPatientId(int patientId) {
        List<TherapyPlan> plans = new ArrayList<>();
        String query = "SELECT * FROM therapyPlan WHERE patientId = ? ORDER BY date DESC";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, patientId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    plans.add(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching therapy plans for patient " + patientId + ": " + e.getMessage());
        }
        return plans;
    }

    public boolean addTherapyPlan(TherapyPlan plan) {
        String query = "INSERT INTO therapyPlan (patientId, date, cost) VALUES (?, ?, ?)";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, plan.getPatientId());
            pstmt.setString(2, plan.getDate());
            pstmt.setDouble(3, plan.getCost());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        plan.setId(generatedKeys.getInt(1));
                    }
                }
                new PatientDAO().updatePatientTotals(plan.getPatientId());
                return true;
            }
            return false;
        } catch (SQLException e) {
            System.err.println("Error adding therapy plan: " + e.getMessage());
            return false;
        }
    }

    public boolean updateTherapyPlan(TherapyPlan plan) {
        String query = "UPDATE therapyPlan SET date = ?, cost = ? WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, plan.getDate());
            pstmt.setDouble(2, plan.getCost());
            pstmt.setInt(3, plan.getId());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                new PatientDAO().updatePatientTotals(plan.getPatientId());
                return true;
            }
            return false;
        } catch (SQLException e) {
            System.err.println("Error updating therapy plan: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteTherapyPlan(int id, int patientId) {
        // Delete associated sessions first, then the plan
        String deleteSessionsQuery = "DELETE FROM session WHERE therapyPlanId = ?";
        String deletePlanQuery = "DELETE FROM therapyPlan WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement pstmt = conn.prepareStatement(deleteSessionsQuery)) {
                    pstmt.setInt(1, id);
                    pstmt.executeUpdate();
                }
                try (PreparedStatement pstmt = conn.prepareStatement(deletePlanQuery)) {
                    pstmt.setInt(1, id);
                    pstmt.executeUpdate();
                }
                conn.commit();
                new PatientDAO().updatePatientTotals(patientId);
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            System.err.println("Error deleting therapy plan: " + e.getMessage());
            return false;
        }
    }

    private TherapyPlan mapResultSet(ResultSet rs) throws SQLException {
        TherapyPlan plan = new TherapyPlan();
        plan.setId(rs.getInt("id"));
        plan.setPatientId(rs.getInt("patientId"));
        plan.setDate(rs.getString("date"));
        plan.setCost(rs.getDouble("cost"));
        
        try {
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                if ("patientName".equalsIgnoreCase(metaData.getColumnLabel(i))) {
                    plan.setPatientName(rs.getString(i));
                    break;
                }
            }
        } catch (SQLException ignored) {
        }
        
        return plan;
    }

    public List<TherapyPlan> getTherapyPlansWithPatientInfo(String date, String gender) {
        List<TherapyPlan> plans = new ArrayList<>();
        boolean filterGender = gender != null && !gender.isEmpty() && !gender.equalsIgnoreCase("Tout");

        String query = """
                    SELECT t.*, p.name as patientName, p.gender as patientGender
                    FROM therapyPlan t
                    LEFT JOIN patient p ON t.patientId = p.id
                    WHERE t.date = ?
                """ + (filterGender ? " AND LOWER(p.gender) = LOWER(?)" : "") + """
                    
                    ORDER BY t.id DESC
                """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, date);
            if (filterGender) {
                pstmt.setString(2, gender);
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    plans.add(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching therapy plans with patient info: " + e.getMessage());
        }
        return plans;
    }

    public double getTotalCostByDate(String date) {
        String query = "SELECT SUM(cost) as totalCost FROM therapyPlan WHERE date = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, date);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("totalCost");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching total cost by date: " + e.getMessage());
        }
        return 0;
    }

    public double getMonthlyTotalCost(int month, int year) {
        String query = "SELECT SUM(cost) as totalCost FROM therapyPlan WHERE strftime('%m', date) = ? AND strftime('%Y', date) = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, String.format("%02d", month));
            pstmt.setString(2, String.valueOf(year));
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("totalCost");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching monthly total cost: " + e.getMessage());
        }
        return 0;
    }
}
