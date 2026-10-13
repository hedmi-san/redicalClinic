package dao;

import config.DatabaseConfig;
import model.Payment;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class PaymentDAO {

    public record CashTypeSummary(String paymentType, int count, double total) {}

    public record DailyCashRegister(double grossCollected, double refunds, double reversals,
                                    double netCash, List<CashTypeSummary> typeSummaries) {}

    public boolean recordPayment(Payment payment) {
        try (Connection conn = DatabaseConfig.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                boolean success = recordPayment(conn, payment);
                if (success) {
                    new PatientDAO().updatePatientTotals(conn, payment.getPatientId());
                    conn.commit();
                    return true;
                } else {
                    conn.rollback();
                    return false;
                }
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            System.err.println("Error recording payment: " + e.getMessage());
            return false;
        }
    }

    public boolean recordPayment(Connection conn, Payment payment) throws SQLException {
        String query = """
                    INSERT INTO payment (patientId, sessionId, paymentDate, amount, paymentType, reversalOf, reason, createdBy)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement pstmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, payment.getPatientId());
            if (payment.getSessionId() != null && payment.getSessionId() > 0) {
                pstmt.setInt(2, payment.getSessionId());
            } else {
                pstmt.setNull(2, Types.INTEGER);
            }
            pstmt.setString(3, payment.getPaymentDate() != null ? payment.getPaymentDate() : LocalDate.now().toString());
            pstmt.setDouble(4, payment.getAmount());
            pstmt.setString(5, payment.getPaymentType());
            if (payment.getReversalOf() != null && payment.getReversalOf() > 0) {
                pstmt.setInt(6, payment.getReversalOf());
            } else {
                pstmt.setNull(6, Types.INTEGER);
            }
            pstmt.setString(7, payment.getReason());
            pstmt.setString(8, payment.getCreatedBy() != null ? payment.getCreatedBy() : "System");

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        payment.setId(generatedKeys.getInt(1));
                    }
                }
                return true;
            }
            return false;
        }
    }

    public List<Payment> getPaymentsByPatientId(int patientId) {
        List<Payment> list = new ArrayList<>();
        String query = """
                    SELECT p.*,
                           (SELECT COUNT(*) FROM payment rev WHERE rev.reversalOf = p.id) as is_reversed
                    FROM payment p
                    WHERE p.patientId = ?
                    ORDER BY p.id DESC
                """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, patientId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Payment payment = mapResultSet(rs);
                    payment.setReversed(rs.getInt("is_reversed") > 0);
                    list.add(payment);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching payments for patient " + patientId + ": " + e.getMessage());
        }
        return list;
    }

    public List<Payment> getPaymentsByDate(String date) {
        List<Payment> list = new ArrayList<>();
        String query = """
                    SELECT p.*, pt.name as patientName,
                           (SELECT COUNT(*) FROM payment rev WHERE rev.reversalOf = p.id) as is_reversed
                    FROM payment p
                    LEFT JOIN patient pt ON p.patientId = pt.id
                    WHERE p.paymentDate = ?
                    ORDER BY p.id DESC
                """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, date);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Payment payment = mapResultSet(rs);
                    payment.setPatientName(rs.getString("patientName"));
                    payment.setReversed(rs.getInt("is_reversed") > 0);
                    list.add(payment);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching payments by date " + date + ": " + e.getMessage());
        }
        return list;
    }

    public DailyCashRegister getDailyCashRegister(String date) {
        List<CashTypeSummary> typeSummaries = new ArrayList<>();
        double grossCollected = 0.0;
        double refunds = 0.0;
        double reversals = 0.0;
        double netCash = 0.0;

        String query = """
                    SELECT paymentType, COUNT(*) as cnt, SUM(amount) as total
                    FROM payment
                    WHERE paymentDate = ?
                    GROUP BY paymentType
                """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, date);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String type = rs.getString("paymentType");
                    int count = rs.getInt("cnt");
                    double total = rs.getDouble("total");
                    typeSummaries.add(new CashTypeSummary(type, count, total));

                    netCash += total;
                    if (Payment.TYPE_REFUND.equalsIgnoreCase(type)) {
                        refunds += Math.abs(total);
                    } else if (Payment.TYPE_REVERSAL.equalsIgnoreCase(type)) {
                        reversals += Math.abs(total);
                    } else if (total > 0) {
                        grossCollected += total;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error calculating daily cash register: " + e.getMessage());
        }

        return new DailyCashRegister(grossCollected, refunds, reversals, netCash, typeSummaries);
    }

    public double getMonthlyTotalCollected(int month, int year) {
        String query = """
                    SELECT SUM(amount) as monthlyNet
                    FROM payment
                    WHERE SUBSTRING(CAST(paymentDate AS VARCHAR), 6, 2) = ?
                      AND SUBSTRING(CAST(paymentDate AS VARCHAR), 1, 4) = ?
                """;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, String.format("%02d", month));
            pstmt.setString(2, String.valueOf(year));
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("monthlyNet");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error calculating monthly collected total: " + e.getMessage());
        }
        return 0.0;
    }

    public boolean reversePayment(int originalPaymentId, String reason, String username) {
        String findQuery = "SELECT * FROM payment WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                Payment original = null;
                try (PreparedStatement pstmt = conn.prepareStatement(findQuery)) {
                    pstmt.setInt(1, originalPaymentId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            original = mapResultSet(rs);
                        }
                    }
                }

                if (original == null) {
                    throw new IllegalArgumentException("Paiement introuvable: #" + originalPaymentId);
                }
                if (original.getAmount() <= 0) {
                    throw new IllegalStateException("Une écriture négative ou une extourne ne peut être extournée.");
                }

                // Check if already reversed
                String checkReversed = "SELECT COUNT(*) FROM payment WHERE reversalOf = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(checkReversed)) {
                    pstmt.setInt(1, originalPaymentId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            throw new IllegalStateException("Ce paiement a déjà été extourné.");
                        }
                    }
                }

                // Create compensating row
                Payment reversal = new Payment();
                reversal.setPatientId(original.getPatientId());
                reversal.setSessionId(original.getSessionId());
                reversal.setPaymentDate(LocalDate.now().toString());
                reversal.setAmount(-Math.abs(original.getAmount()));
                reversal.setPaymentType(Payment.TYPE_REVERSAL);
                reversal.setReversalOf(originalPaymentId);
                reversal.setReason(reason != null && !reason.trim().isEmpty() ? reason.trim() : "Extourne du paiement #" + originalPaymentId);
                reversal.setCreatedBy(username);

                recordPayment(conn, reversal);
                new PatientDAO().updatePatientTotals(conn, original.getPatientId());

                conn.commit();
                return true;
            } catch (Exception e) {
                conn.rollback();
                System.err.println("Error executing reversal: " + e.getMessage());
                throw new RuntimeException(e.getMessage(), e);
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            System.err.println("Database error during reversal: " + e.getMessage());
            return false;
        }
    }

    public boolean correctPayment(int originalPaymentId, double newAmount, String reason, String username) {
        if (newAmount <= 0) {
            throw new IllegalArgumentException("Le nouveau montant doit être strictement positif.");
        }

        try (Connection conn = DatabaseConfig.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                // 1. Fetch original
                Payment original = null;
                try (PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM payment WHERE id = ?")) {
                    pstmt.setInt(1, originalPaymentId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            original = mapResultSet(rs);
                        }
                    }
                }

                if (original == null) {
                    throw new IllegalArgumentException("Paiement introuvable: #" + originalPaymentId);
                }

                // 2. Reverse original
                Payment reversal = new Payment();
                reversal.setPatientId(original.getPatientId());
                reversal.setSessionId(original.getSessionId());
                reversal.setPaymentDate(LocalDate.now().toString());
                reversal.setAmount(-Math.abs(original.getAmount()));
                reversal.setPaymentType(Payment.TYPE_REVERSAL);
                reversal.setReversalOf(originalPaymentId);
                reversal.setReason("Correction de #" + originalPaymentId + ": " + reason);
                reversal.setCreatedBy(username);
                recordPayment(conn, reversal);

                // 3. Insert corrected payment
                Payment corrected = new Payment();
                corrected.setPatientId(original.getPatientId());
                corrected.setSessionId(original.getSessionId());
                corrected.setPaymentDate(LocalDate.now().toString());
                corrected.setAmount(newAmount);
                corrected.setPaymentType(original.getPaymentType());
                corrected.setReason("Correction du paiement #" + originalPaymentId + ": " + reason);
                corrected.setCreatedBy(username);
                recordPayment(conn, corrected);

                new PatientDAO().updatePatientTotals(conn, original.getPatientId());
                conn.commit();
                return true;
            } catch (Exception e) {
                conn.rollback();
                throw new RuntimeException(e.getMessage(), e);
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            System.err.println("Database error during correction: " + e.getMessage());
            return false;
        }
    }

    public boolean refundCredit(int patientId, double refundAmount, String reason, String username) {
        if (refundAmount <= 0) {
            throw new IllegalArgumentException("Le montant du remboursement doit être strictement positif.");
        }

        try (Connection conn = DatabaseConfig.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                model.Patient patient = new PatientDAO().getPatientById(patientId);
                if (patient == null) {
                    throw new IllegalArgumentException("Patient introuvable: #" + patientId);
                }
                // Balance < 0 means credit in favor of the patient
                double availableCredit = patient.getBalance() < 0 ? Math.abs(patient.getBalance()) : 0.0;
                if (refundAmount > availableCredit + 0.001) {
                    throw new IllegalArgumentException(String.format("Le remboursement (%.2f DA) dépasse le crédit disponible (%.2f DA).",
                            refundAmount, availableCredit));
                }

                Payment refund = new Payment();
                refund.setPatientId(patientId);
                refund.setSessionId(null);
                refund.setPaymentDate(LocalDate.now().toString());
                refund.setAmount(-Math.abs(refundAmount));
                refund.setPaymentType(Payment.TYPE_REFUND);
                refund.setReason(reason != null && !reason.trim().isEmpty() ? reason.trim() : "Remboursement avance patient");
                refund.setCreatedBy(username);

                recordPayment(conn, refund);
                new PatientDAO().updatePatientTotals(conn, patientId);

                conn.commit();
                return true;
            } catch (Exception e) {
                conn.rollback();
                throw new RuntimeException(e.getMessage(), e);
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            System.err.println("Database error during refund: " + e.getMessage());
            return false;
        }
    }

    private Payment mapResultSet(ResultSet rs) throws SQLException {
        Payment p = new Payment();
        p.setId(rs.getInt("id"));
        p.setPatientId(rs.getInt("patientId"));
        int sid = rs.getInt("sessionId");
        p.setSessionId(rs.wasNull() ? null : sid);
        p.setPaymentDate(rs.getString("paymentDate"));
        p.setCreatedAt(rs.getString("createdAt"));
        p.setAmount(rs.getDouble("amount"));
        p.setPaymentType(rs.getString("paymentType"));
        int rof = rs.getInt("reversalOf");
        p.setReversalOf(rs.wasNull() ? null : rof);
        p.setReason(rs.getString("reason"));
        p.setCreatedBy(rs.getString("createdBy"));
        return p;
    }
}
