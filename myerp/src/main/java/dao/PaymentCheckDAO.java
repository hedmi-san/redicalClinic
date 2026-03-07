package dao;

import config.DatabaseConfig;
import model.PaymentCheck;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PaymentCheckDAO {

    public boolean addPayment(PaymentCheck payment) {
        String query = "INSERT INTO paymentCheck (workerId, paymentDate, paidAmount, note) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, payment.getWorkerId());
            pstmt.setString(2, payment.getPaymentDate());
            pstmt.setDouble(3, payment.getPaidAmount());
            pstmt.setString(4, payment.getNote());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error adding payment: " + e.getMessage());
            return false;
        }
    }

    public List<PaymentCheck> getPaymentsByWorkerId(int workerId) {
        List<PaymentCheck> payments = new ArrayList<>();
        String query = "SELECT * FROM paymentCheck WHERE workerId = ? ORDER BY paymentDate DESC";
        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, workerId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    payments.add(mapResultSetToPayment(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching payments: " + e.getMessage());
        }
        return payments;
    }

    public boolean deletePayment(int id) {
        String query = "DELETE FROM paymentCheck WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error deleting payment: " + e.getMessage());
            return false;
        }
    }

    public List<PaymentCheck> getPaymentsByWorkerAndMonth(int workerId, int month, int year) {
        List<PaymentCheck> payments = new ArrayList<>();
        // strftime('%m', date) returns 01-12, so we need to format the month as 2
        // digits
        String monthStr = String.format("%02d", month);
        String yearStr = String.valueOf(year);

        String query = "SELECT * FROM paymentCheck WHERE workerId = ? " +
                "AND strftime('%m', paymentDate) = ? " +
                "AND strftime('%Y', paymentDate) = ? " +
                "ORDER BY paymentDate DESC";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, workerId);
            pstmt.setString(2, monthStr);
            pstmt.setString(3, yearStr);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    payments.add(mapResultSetToPayment(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching monthly payments: " + e.getMessage());
        }
        return payments;
    }

    public double getMonthlyTotalByWorker(int workerId, int month, int year) {
        String monthStr = String.format("%02d", month);
        String yearStr = String.valueOf(year);

        String query = "SELECT SUM(paidAmount) FROM paymentCheck WHERE workerId = ? " +
                "AND strftime('%m', paymentDate) = ? " +
                "AND strftime('%Y', paymentDate) = ?";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, workerId);
            pstmt.setString(2, monthStr);
            pstmt.setString(3, yearStr);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error calculating monthly total: " + e.getMessage());
        }
        return 0.0;
    }

    public double getTotalEarningByWorkerId(int workerId) {
        String query = "SELECT SUM(paidAmount) FROM paymentCheck WHERE workerId = ?";
        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, workerId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error calculating total earning: " + e.getMessage());
        }
        return 0.0;
    }

    public boolean updatePayment(PaymentCheck payment) {
        String query = "UPDATE paymentCheck SET paymentDate = ?, paidAmount = ?, note = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, payment.getPaymentDate());
            pstmt.setDouble(2, payment.getPaidAmount());
            pstmt.setString(3, payment.getNote());
            pstmt.setInt(4, payment.getId());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating payment: " + e.getMessage());
            return false;
        }
    }

    private PaymentCheck mapResultSetToPayment(ResultSet rs) throws SQLException {
        PaymentCheck payment = new PaymentCheck();
        payment.setId(rs.getInt("id"));
        payment.setWorkerId(rs.getInt("workerId"));
        payment.setPaymentDate(rs.getString("paymentDate"));
        payment.setPaidAmount(rs.getDouble("paidAmount"));
        payment.setNote(rs.getString("note"));
        return payment;
    }
}
