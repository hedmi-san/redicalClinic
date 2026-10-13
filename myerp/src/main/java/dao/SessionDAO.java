package dao;

import config.DatabaseConfig;
import model.Session;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SessionDAO {

    public List<Session> getSessionsByPatientId(int patientId) {
        List<Session> sessions = new ArrayList<>();
        String query = "SELECT * FROM session WHERE patientId = ? AND therapyPlanId IS NULL ORDER BY sessionDate DESC";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, patientId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    sessions.add(mapResultSetToSession(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching sessions for patient " + patientId + ": " + e.getMessage());
        }
        return sessions;
    }

    public List<Session> getSessionsByDate(String date) {
        List<Session> sessions = new ArrayList<>();
        String query = """
                    SELECT s.*, p.name as patientName
                    FROM session s
                    LEFT JOIN patient p ON s.patientId = p.id
                    WHERE s.sessionDate = ?
                    ORDER BY s.id DESC
                """;

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, date);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Session session = mapResultSetToSession(rs);
                    try {
                        session.setPatientName(rs.getString("patientName"));
                    } catch (SQLException ignored) {
                        // patientName might not be in the result set if called from other methods
                    }
                    sessions.add(session);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching sessions for date " + date + ": " + e.getMessage());
        }
        return sessions;
    }

    public boolean addSession(Session session) {
        return addSession(session, session.getPaidAmount(), "System");
    }

    public boolean addSession(Session session, double cashReceived, String username) {
        String query = "INSERT INTO session (patientId, sessionDate, treatment, paied, cost, paidAmount, therapyPlanId) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConfig.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement pstmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
                    pstmt.setInt(1, session.getPatientId());
                    pstmt.setString(2, session.getDate());
                    pstmt.setString(3, session.getTreatment());
                    pstmt.setString(4, session.getPaied());
                    pstmt.setDouble(5, session.getCost());
                    pstmt.setDouble(6, cashReceived);
                    if (session.getTherapyPlanId() != null) {
                        pstmt.setInt(7, session.getTherapyPlanId());
                    } else {
                        pstmt.setNull(7, java.sql.Types.INTEGER);
                    }

                    int affectedRows = pstmt.executeUpdate();
                    if (affectedRows > 0) {
                        try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                            if (generatedKeys.next()) {
                                session.setId(generatedKeys.getInt(1));
                            }
                        }
                    } else {
                        conn.rollback();
                        return false;
                    }
                }

                // Record actual cash payment in ledger if received
                if (cashReceived > 0 && (session.getTherapyPlanId() == null || session.getTherapyPlanId() == 0)) {
                    model.Payment payment = new model.Payment();
                    payment.setPatientId(session.getPatientId());
                    payment.setSessionId(session.getId());
                    payment.setPaymentDate(session.getDate());
                    payment.setAmount(cashReceived);
                    payment.setPaymentType(model.Payment.TYPE_SESSION);
                    payment.setReason("Paiement séance #" + session.getId());
                    payment.setCreatedBy(username != null ? username : "System");
                    new PaymentDAO().recordPayment(conn, payment);
                }

                new PatientDAO().updatePatientTotals(conn, session.getPatientId());
                conn.commit();
                return true;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            System.err.println("Error adding session with payment: " + e.getMessage());
            return false;
        }
    }

    public boolean updateSession(Session session) {
        return updateSession(session, session.getPaidAmount(), "System");
    }

    public boolean updateSession(Session session, double cashReceived, String username) {
        String query = "UPDATE session SET sessionDate = ?, treatment = ?, paied = ?, cost = ?, paidAmount = ? WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                    pstmt.setString(1, session.getDate());
                    pstmt.setString(2, session.getTreatment());
                    pstmt.setString(3, session.getPaied());
                    pstmt.setDouble(4, session.getCost());
                    pstmt.setDouble(5, cashReceived);
                    pstmt.setInt(6, session.getId());

                    int affectedRows = pstmt.executeUpdate();
                    if (affectedRows == 0) {
                        conn.rollback();
                        return false;
                    }
                }

                // Check if payment already exists for this session
                String checkPaymentQuery = "SELECT id, amount FROM payment WHERE sessionId = ? AND paymentType = 'SESSION' AND reversalOf IS NULL";
                Integer existingPaymentId = null;
                Double existingAmount = null;
                try (PreparedStatement pstmt = conn.prepareStatement(checkPaymentQuery)) {
                    pstmt.setInt(1, session.getId());
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            existingPaymentId = rs.getInt("id");
                            existingAmount = rs.getDouble("amount");
                        }
                    }
                }

                if (existingPaymentId != null) {
                    if (cashReceived <= 0) {
                        // Reverse existing payment
                        model.Payment reversal = new model.Payment();
                        reversal.setPatientId(session.getPatientId());
                        reversal.setSessionId(session.getId());
                        reversal.setPaymentDate(session.getDate());
                        reversal.setAmount(-Math.abs(existingAmount));
                        reversal.setPaymentType(model.Payment.TYPE_REVERSAL);
                        reversal.setReversalOf(existingPaymentId);
                        reversal.setReason("Annulation paiement séance #" + session.getId());
                        reversal.setCreatedBy(username != null ? username : "System");
                        new PaymentDAO().recordPayment(conn, reversal);
                    } else if (Math.abs(existingAmount - cashReceived) > 0.001) {
                        // Amount changed: reverse old + insert new
                        model.Payment reversal = new model.Payment();
                        reversal.setPatientId(session.getPatientId());
                        reversal.setSessionId(session.getId());
                        reversal.setPaymentDate(session.getDate());
                        reversal.setAmount(-Math.abs(existingAmount));
                        reversal.setPaymentType(model.Payment.TYPE_REVERSAL);
                        reversal.setReversalOf(existingPaymentId);
                        reversal.setReason("Correction montant séance #" + session.getId());
                        reversal.setCreatedBy(username != null ? username : "System");
                        new PaymentDAO().recordPayment(conn, reversal);

                        model.Payment corrected = new model.Payment();
                        corrected.setPatientId(session.getPatientId());
                        corrected.setSessionId(session.getId());
                        corrected.setPaymentDate(session.getDate());
                        corrected.setAmount(cashReceived);
                        corrected.setPaymentType(model.Payment.TYPE_SESSION);
                        corrected.setReason("Paiement corrigé séance #" + session.getId());
                        corrected.setCreatedBy(username != null ? username : "System");
                        new PaymentDAO().recordPayment(conn, corrected);
                    }
                } else if (cashReceived > 0 && (session.getTherapyPlanId() == null || session.getTherapyPlanId() == 0)) {
                    // No existing payment: insert new payment
                    model.Payment payment = new model.Payment();
                    payment.setPatientId(session.getPatientId());
                    payment.setSessionId(session.getId());
                    payment.setPaymentDate(session.getDate());
                    payment.setAmount(cashReceived);
                    payment.setPaymentType(model.Payment.TYPE_SESSION);
                    payment.setReason("Paiement séance #" + session.getId());
                    payment.setCreatedBy(username != null ? username : "System");
                    new PaymentDAO().recordPayment(conn, payment);
                }

                new PatientDAO().updatePatientTotals(conn, session.getPatientId());
                conn.commit();
                return true;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            System.err.println("Error updating session with payment: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteSession(int id, int patientId) {
        String query = "DELETE FROM session WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, id);
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                new PatientDAO().updatePatientTotals(patientId);
                return true;
            }
            return false;
        } catch (SQLException e) {
            System.err.println("Error deleting session: " + e.getMessage());
            return false;
        }
    }

    public MonthlySummary getMonthlySummary(int month, int year) {
        String monthStr = String.format("%02d", month);
        String yearStr = String.valueOf(year);
        String query = """
                    SELECT COUNT(*) as sessionCount,
                           SUM(cost) as totalCost,
                           SUM(paidAmount) as totalPaid
                    FROM session
                    WHERE SUBSTRING(sessionDate, 6, 2) = ?
                    AND SUBSTRING(sessionDate, 1, 4) = ?
                    AND therapyPlanId IS NULL
                """;

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, monthStr);
            pstmt.setString(2, yearStr);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new MonthlySummary(
                            rs.getInt("sessionCount"),
                            rs.getDouble("totalCost"),
                            rs.getDouble("totalPaid"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching monthly summary: " + e.getMessage());
        }
        return new MonthlySummary(0, 0.0, 0.0);
    }

    public static record MonthlySummary(int count, double totalCost, double totalPaid) {
    }

    /**
     * Fetches sessions joined with patient data, filtered by date and optionally gender.
     * @param date session date (yyyy-MM-dd)
     * @param gender "Male", "Female", or null/empty/"All" for no gender filter
     */
    public List<Session> getSessionsWithPatientInfo(String date, String gender) {
        List<Session> sessions = new ArrayList<>();
        boolean filterGender = gender != null && !gender.isEmpty() && !gender.equalsIgnoreCase("Tout");

        String query = """
                    SELECT s.*, p.name as patientName, p.gender as patientGender
                    FROM session s
                    LEFT JOIN patient p ON s.patientId = p.id
                    WHERE s.sessionDate = ? AND s.therapyPlanId IS NULL
                """ + (filterGender ? " AND LOWER(p.gender) = LOWER(?)" : "") + """
                    
                    ORDER BY s.id DESC
                """;

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, date);
            if (filterGender) {
                pstmt.setString(2, gender);
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Session session = mapResultSetToSession(rs);
                    sessions.add(session);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching sessions with patient info: " + e.getMessage());
        }
        return sessions;
    }

    public List<Session> getSessionsByTherapyPlanId(int therapyPlanId) {
        List<Session> sessions = new ArrayList<>();
        String query = "SELECT * FROM session WHERE therapyPlanId = ? ORDER BY sessionDate DESC";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, therapyPlanId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    sessions.add(mapResultSetToSession(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching sessions for therapy plan " + therapyPlanId + ": " + e.getMessage());
        }
        return sessions;
    }

    public boolean addTherapySession(Session session) {
        // For therapy sessions, paidAmount always equals cost
        session.setPaidAmount(session.getCost());
        return addSession(session);
    }

    private Session mapResultSetToSession(ResultSet rs) throws SQLException {
        Session session = new Session();
        session.setId(rs.getInt("id"));
        session.setPatientId(rs.getInt("patientId"));
        session.setDate(rs.getString("sessionDate"));
        session.setTreatment(rs.getString("treatment"));
        session.setPaied(rs.getString("paied"));
        session.setCost(rs.getDouble("cost"));
        session.setPaidAmount(rs.getDouble("paidAmount"));

        // Map therapyPlanId (nullable)
        int tpId = rs.getInt("therapyPlanId");
        session.setTherapyPlanId(rs.wasNull() ? null : tpId);

        // Try to set patientName if available in result set
        try {
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                if ("patientName".equalsIgnoreCase(metaData.getColumnLabel(i))) {
                    session.setPatientName(rs.getString(i));
                    break;
                }
            }
        } catch (SQLException ignored) {
        }

        return session;
    }
}
