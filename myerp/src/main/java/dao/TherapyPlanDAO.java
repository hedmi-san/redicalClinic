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

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating therapy plan: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteTherapyPlan(int id) {
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
        return plan;
    }
}
