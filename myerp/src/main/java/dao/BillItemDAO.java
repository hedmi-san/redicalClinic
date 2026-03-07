package dao;

import config.DatabaseConfig;
import model.BillItem;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BillItemDAO {

    public List<BillItem> getItemsByBillId(int billId) {
        List<BillItem> items = new ArrayList<>();
        String query = "SELECT * FROM billItem WHERE billId = ?";
        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, billId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    items.add(new BillItem(
                            rs.getInt("id"),
                            rs.getInt("billId"),
                            rs.getString("itemName"),
                            rs.getDouble("unitPrice"),
                            rs.getDouble("quantity")));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching bill items: " + e.getMessage());
        }
        return items;
    }

    public boolean addItem(BillItem item) {
        String query = "INSERT INTO billItem (billId, itemName, quantity, unitPrice) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, item.getBillId());
            pstmt.setString(2, item.getItemName());
            pstmt.setDouble(3, item.getQuantity());
            pstmt.setDouble(4, item.getUnitPrice());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error adding bill item: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteItemsByBillId(int billId) {
        String query = "DELETE FROM billItem WHERE billId = ?";
        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, billId);
            return pstmt.executeUpdate() >= 0;
        } catch (SQLException e) {
            System.err.println("Error deleting bill items: " + e.getMessage());
            return false;
        }
    }
}
