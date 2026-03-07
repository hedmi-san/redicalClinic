package dao;

import config.DatabaseConfig;
import model.Bill;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BillDAO {

    public List<Bill> getAllBills() {
        List<Bill> bills = new ArrayList<>();
        String query = "SELECT * FROM bill ORDER BY billDate DESC";
        try (Connection conn = DatabaseConfig.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                bills.add(new Bill(
                        rs.getInt("id"),
                        rs.getString("billDate"),
                        rs.getDouble("totalCost")));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching bills: " + e.getMessage());
        }
        return bills;
    }

    public int addBill(Bill bill) {
        String query = "INSERT INTO bill (billDate, totalCost) VALUES (?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, bill.getBillDate());
            pstmt.setDouble(2, bill.getTotalCost());
            pstmt.executeUpdate();
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error adding bill: " + e.getMessage());
        }
        return -1;
    }

    public boolean deleteBill(int id) {
        String query = "DELETE FROM bill WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error deleting bill: " + e.getMessage());
            return false;
        }
    }

    public boolean updateBillTotal(int billId, double total) {
        String query = "UPDATE bill SET totalCost = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setDouble(1, total);
            pstmt.setInt(2, billId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating bill total: " + e.getMessage());
            return false;
        }
    }
}
