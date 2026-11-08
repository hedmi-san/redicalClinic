package dao;

import config.DatabaseConfig;
import model.Appointment;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class AppointmentDAO {

    private static final String SELECT_WITH_JOINS = """
            SELECT a.*,
                   p.name AS patientName,
                   p.phone AS patientPhone,
                   p.gender AS patientGender,
                   w.name AS workerName
            FROM appointment a
            LEFT JOIN patient p ON a.patientId = p.id
            LEFT JOIN worker w ON a.workerId = w.id
            """;

    public boolean addAppointment(Appointment appointment) {
        String sql = """
                INSERT INTO appointment (
                    patientId, guestName, guestPhone, workerId,
                    date, startTime, durationMin, motif, status, sessionId, createdBy
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            bindAppointmentParameters(pstmt, appointment);
            int affected = pstmt.executeUpdate();
            if (affected > 0) {
                try (ResultSet keys = pstmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        appointment.setId(keys.getInt(1));
                    }
                }
                return true;
            }
            return false;
        } catch (SQLException e) {
            System.err.println("Error adding appointment: " + e.getMessage());
            return false;
        }
    }

    public boolean updateAppointment(Appointment appointment) {
        String sql = """
                UPDATE appointment
                SET patientId = ?,
                    guestName = ?,
                    guestPhone = ?,
                    workerId = ?,
                    date = ?,
                    startTime = ?,
                    durationMin = ?,
                    motif = ?,
                    status = ?,
                    sessionId = ?,
                    createdBy = ?
                WHERE id = ?
                """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            bindAppointmentParameters(pstmt, appointment);
            pstmt.setInt(12, appointment.getId());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating appointment: " + e.getMessage());
            return false;
        }
    }

    public boolean updateStatus(int appointmentId, String newStatus) {
        String sql = "UPDATE appointment SET status = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newStatus);
            pstmt.setInt(2, appointmentId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating appointment status: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteAppointment(int appointmentId) {
        String sql = "DELETE FROM appointment WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, appointmentId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error deleting appointment: " + e.getMessage());
            return false;
        }
    }

    public boolean linkPatientToAppointment(int appointmentId, int patientId) {
        String sql = "UPDATE appointment SET patientId = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, patientId);
            pstmt.setInt(2, appointmentId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error linking patient to appointment: " + e.getMessage());
            return false;
        }
    }

    public boolean markConvertedToSession(int appointmentId, int sessionId) {
        String sql = "UPDATE appointment SET sessionId = ?, status = ? WHERE id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, sessionId);
            pstmt.setString(2, Appointment.STATUS_TERMINE);
            pstmt.setInt(3, appointmentId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error marking appointment converted to session: " + e.getMessage());
            return false;
        }
    }

    public Appointment getAppointmentById(int id) {
        String sql = SELECT_WITH_JOINS + " WHERE a.id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToAppointment(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching appointment by id: " + e.getMessage());
        }
        return null;
    }

    public List<Appointment> getAppointmentsByDate(String date) {
        List<Appointment> list = new ArrayList<>();
        String sql = SELECT_WITH_JOINS + " WHERE a.date = ? ORDER BY a.startTime ASC, a.id ASC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, date);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToAppointment(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching appointments for date " + date + ": " + e.getMessage());
        }
        return list;
    }

    public List<Appointment> getAppointmentsByDateRange(String startDate, String endDate) {
        List<Appointment> list = new ArrayList<>();
        String sql = SELECT_WITH_JOINS + " WHERE a.date >= ? AND a.date <= ? ORDER BY a.date ASC, a.startTime ASC, a.id ASC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, startDate);
            pstmt.setString(2, endDate);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToAppointment(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching appointments for range " + startDate + " to " + endDate + ": " + e.getMessage());
        }
        return list;
    }

    public List<Appointment> getAppointmentsByPatientId(int patientId) {
        List<Appointment> list = new ArrayList<>();
        String sql = SELECT_WITH_JOINS + " WHERE a.patientId = ? ORDER BY a.date DESC, a.startTime DESC";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, patientId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToAppointment(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching appointments for patient " + patientId + ": " + e.getMessage());
        }
        return list;
    }

    /**
     * Returns the next upcoming active appointment (status PREVU or CONFIRME) on or after today.
     */
    public Appointment getNextUpcomingAppointmentForPatient(int patientId) {
        String today = LocalDate.now().toString();
        String sql = SELECT_WITH_JOINS + """
                WHERE a.patientId = ?
                  AND a.date >= ?
                  AND a.status IN ('PREVU', 'CONFIRME')
                ORDER BY a.date ASC, a.startTime ASC
                LIMIT 1
                """;
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, patientId);
            pstmt.setString(2, today);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToAppointment(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching next upcoming appointment for patient " + patientId + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Finds conflicting appointments on the same date whose time intervals intersect with the given appointment.
     * A conflict is triggered if:
     * 1. The same assigned practitioner is double-booked on overlapping times.
     * 2. The same registered patient is double-booked on overlapping times.
     * 3. Total active concurrent appointments on the slot exceed the clinic's maximum concurrent capacity (rooms/workers).
     * Multiple appointments at the same time for different practitioners or unassigned slots within clinic capacity are NOT conflicts.
     */
    public List<Appointment> findOverlappingAppointments(
            String date,
            String startTime,
            int durationMin,
            Integer workerId,
            int excludeAppointmentId) {
        return findOverlappingAppointments(date, startTime, durationMin, workerId, null, excludeAppointmentId);
    }

    public List<Appointment> findOverlappingAppointments(
            String date,
            String startTime,
            int durationMin,
            Integer workerId,
            Integer patientId,
            int excludeAppointmentId) {

        List<Appointment> conflicts = new ArrayList<>();
        if (date == null || startTime == null) {
            return conflicts;
        }

        int candidateStart = parseMinutesOfDay(startTime);
        int candidateEnd = candidateStart + Math.max(1, durationMin);

        List<Appointment> dayAppointments = getAppointmentsByDate(date);
        List<Appointment> overlappingActive = new ArrayList<>();

        for (Appointment existing : dayAppointments) {
            if (existing.getId() == excludeAppointmentId) {
                continue;
            }
            if (!existing.isActiveForConflictCheck()) {
                continue;
            }

            int existingStart = existing.getStartMinutesOfDay();
            int existingEnd = existing.getEndMinutesOfDay();

            if (candidateStart < existingEnd && existingStart < candidateEnd) {
                overlappingActive.add(existing);

                // 1. Same practitioner conflict (doctor double-booked)
                if (workerId != null && workerId > 0
                        && existing.getWorkerId() != null
                        && workerId.equals(existing.getWorkerId())) {
                    if (!conflicts.contains(existing)) {
                        conflicts.add(existing);
                    }
                }

                // 2. Same registered patient conflict (patient double-booked)
                if (patientId != null && patientId > 0
                        && existing.getPatientId() != null
                        && patientId.equals(existing.getPatientId())) {
                    if (!conflicts.contains(existing)) {
                        conflicts.add(existing);
                    }
                }
            }
        }

        // 3. Clinic concurrent capacity check (exceeding total rooms / concurrent capacity)
        ClinicSettingsDAO settingsDAO = new ClinicSettingsDAO();
        int maxCapacity = settingsDAO.getMaxConcurrentAppointments();
        if (overlappingActive.size() + 1 > maxCapacity) {
            for (Appointment existing : overlappingActive) {
                if (!conflicts.contains(existing)) {
                    conflicts.add(existing);
                }
            }
        }

        return conflicts;
    }

    private static int parseMinutesOfDay(String timeStr) {
        try {
            LocalTime lt = LocalTime.parse(timeStr.trim(), DateTimeFormatter.ofPattern("HH:mm"));
            return lt.getHour() * 60 + lt.getMinute();
        } catch (Exception e) {
            return 0;
        }
    }

    private void bindAppointmentParameters(PreparedStatement pstmt, Appointment a) throws SQLException {
        if (a.getPatientId() != null && a.getPatientId() > 0) {
            pstmt.setInt(1, a.getPatientId());
        } else {
            pstmt.setNull(1, Types.INTEGER);
        }

        pstmt.setString(2, a.getGuestName());
        pstmt.setString(3, a.getGuestPhone());

        if (a.getWorkerId() != null && a.getWorkerId() > 0) {
            pstmt.setInt(4, a.getWorkerId());
        } else {
            pstmt.setNull(4, Types.INTEGER);
        }

        pstmt.setString(5, a.getDate());
        pstmt.setString(6, a.getStartTime());
        pstmt.setInt(7, a.getDurationMin() > 0 ? a.getDurationMin() : 30);
        pstmt.setString(8, a.getMotif());
        pstmt.setString(9, a.getStatus() != null ? a.getStatus() : Appointment.STATUS_PREVU);

        if (a.getSessionId() != null && a.getSessionId() > 0) {
            pstmt.setInt(10, a.getSessionId());
        } else {
            pstmt.setNull(10, Types.INTEGER);
        }

        pstmt.setString(11, a.getCreatedBy() != null && !a.getCreatedBy().trim().isEmpty()
                ? a.getCreatedBy().trim()
                : "Employé");
    }

    private Appointment mapResultSetToAppointment(ResultSet rs) throws SQLException {
        Appointment a = new Appointment();
        a.setId(rs.getInt("id"));

        int pid = rs.getInt("patientId");
        a.setPatientId(rs.wasNull() ? null : pid);

        a.setGuestName(rs.getString("guestName"));
        a.setGuestPhone(rs.getString("guestPhone"));

        int wid = rs.getInt("workerId");
        a.setWorkerId(rs.wasNull() ? null : wid);

        a.setDate(rs.getString("date"));
        a.setStartTime(rs.getString("startTime"));
        a.setDurationMin(rs.getInt("durationMin"));
        a.setMotif(rs.getString("motif"));
        a.setStatus(rs.getString("status"));

        int sid = rs.getInt("sessionId");
        a.setSessionId(rs.wasNull() ? null : sid);

        a.setCreatedBy(rs.getString("createdBy"));
        a.setCreatedAt(rs.getString("createdAt"));

        try {
            a.setPatientName(rs.getString("patientName"));
        } catch (SQLException ignored) {
        }
        try {
            a.setPatientPhone(rs.getString("patientPhone"));
        } catch (SQLException ignored) {
        }
        try {
            a.setPatientGender(rs.getString("patientGender"));
        } catch (SQLException ignored) {
        }
        try {
            a.setWorkerName(rs.getString("workerName"));
        } catch (SQLException ignored) {
        }

        return a;
    }
}

