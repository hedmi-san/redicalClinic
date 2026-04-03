package dao;

import config.DatabaseConfig;
import model.Sold;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SoldDAO {

    public List<Sold> getSoldsByMonthYearAndSearch(int month, int year, String searchQuery) {
        List<Sold> solds = new ArrayList<>();
        String monthStr = String.format("%02d", month);
        String yearStr = String.valueOf(year);
        
        String query = "SELECT * FROM sold WHERE strftime('%m', soldDate) = ? AND strftime('%Y', soldDate) = ?";
        
        if (searchQuery != null && !searchQuery.trim().isEmpty()) {
            query += " AND itemName LIKE ?";
        }
        query += " ORDER BY id DESC";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, monthStr);
            pstmt.setString(2, yearStr);
            
            if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                pstmt.setString(3, "%" + searchQuery.trim() + "%");
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    solds.add(mapResultSetToSold(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching sales records: " + e.getMessage());
        }
        return solds;
    }

    public boolean addSold(Sold sold) {
        String query = "INSERT INTO sold (itemName, soldDate, soldPrice, quantity) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, sold.getItemName());
            pstmt.setString(2, sold.getSoldDate());
            pstmt.setDouble(3, sold.getSoldPrice());
            pstmt.setDouble(4, sold.getQuantity());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        sold.setId(generatedKeys.getInt(1));
                    }
                }
                return true;
            }
        } catch (SQLException e) {
            System.err.println("Error adding sale: " + e.getMessage());
        }
        return false;
    }

    public boolean updateSold(Sold sold) {
        String query = "UPDATE sold SET itemName = ?, soldDate = ?, soldPrice = ?, quantity = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, sold.getItemName());
            pstmt.setString(2, sold.getSoldDate());
            pstmt.setDouble(3, sold.getSoldPrice());
            pstmt.setDouble(4, sold.getQuantity());
            pstmt.setInt(5, sold.getId());

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating sale: " + e.getMessage());
        }
        return false;
    }

    public boolean deleteSold(int id) {
        String query = "DELETE FROM sold WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error deleting sale: " + e.getMessage());
        }
        return false;
    }

    public MonthlySummary getMonthlySummary(int month, int year) {
        String monthStr = String.format("%02d", month);
        String yearStr = String.valueOf(year);
        String query = "SELECT COUNT(*) as soldCount, SUM(soldPrice * quantity) as totalRevenue " +
                       "FROM sold WHERE strftime('%m', soldDate) = ? AND strftime('%Y', soldDate) = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, monthStr);
            pstmt.setString(2, yearStr);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new MonthlySummary(
                            rs.getInt("soldCount"),
                            rs.getDouble("totalRevenue"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching monthly sales summary: " + e.getMessage());
        }
        return new MonthlySummary(0, 0.0);
    }

    public static record MonthlySummary(int count, double totalRevenue) {}

    private Sold mapResultSetToSold(ResultSet rs) throws SQLException {
        Sold sold = new Sold();
        sold.setId(rs.getInt("id"));
        sold.setItemName(rs.getString("itemName"));
        sold.setSoldDate(rs.getString("soldDate"));
        sold.setSoldPrice(rs.getDouble("soldPrice"));
        sold.setQuantity(rs.getDouble("quantity"));
        return sold;
    }
}
