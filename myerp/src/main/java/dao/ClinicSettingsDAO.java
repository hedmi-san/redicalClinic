package dao;

import config.DatabaseConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ClinicSettingsDAO {

    public static final String KEY_WORKING_DAYS = "schedule.working_days";
    public static final String KEY_START_TIME = "schedule.start_time";
    public static final String KEY_END_TIME = "schedule.end_time";
    public static final String KEY_DEFAULT_DURATION = "schedule.default_duration_min";
    public static final String KEY_MAX_CONCURRENT = "schedule.max_concurrent";

    public static final String DEFAULT_WORKING_DAYS = "SAT,SUN,MON,TUE,WED,THU";
    public static final String DEFAULT_START_TIME = "08:00";
    public static final String DEFAULT_END_TIME = "17:00";
    public static final int DEFAULT_DURATION_MIN = 30;
    public static final int DEFAULT_MAX_CONCURRENT = 5;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static volatile Map<String, String> settingsCache = null;

    public static void invalidateCache() {
        settingsCache = null;
    }

    private Map<String, String> getOrLoadCache() {
        Map<String, String> local = settingsCache;
        if (local != null) {
            return local;
        }
        synchronized (ClinicSettingsDAO.class) {
            if (settingsCache != null) {
                return settingsCache;
            }
            Map<String, String> loaded = new ConcurrentHashMap<>();
            String sql = "SELECT settingKey, settingValue FROM clinic_settings";
            try (Connection conn = DatabaseConfig.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    String k = rs.getString("settingKey");
                    String v = rs.getString("settingValue");
                    if (k != null && v != null) {
                        loaded.put(k.trim(), v.trim());
                    }
                }
                settingsCache = loaded;
            } catch (SQLException e) {
                System.err.println("Error loading clinic settings cache: " + e.getMessage());
            }
            return loaded;
        }
    }

    public String getSetting(String key, String defaultValue) {
        String val = getOrLoadCache().get(key);
        if (val != null && !val.isEmpty()) {
            return val;
        }
        return defaultValue;
    }

    public boolean setSetting(String key, String value) {
        String updateSql = "UPDATE clinic_settings SET settingValue = ? WHERE settingKey = ?";
        String insertSql = "INSERT INTO clinic_settings (settingKey, settingValue) VALUES (?, ?)";
        try (Connection conn = DatabaseConfig.getConnection()) {
            boolean saved = false;
            try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                updateStmt.setString(1, value);
                updateStmt.setString(2, key);
                int updated = updateStmt.executeUpdate();
                if (updated > 0) {
                    saved = true;
                }
            }
            if (!saved) {
                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    insertStmt.setString(1, key);
                    insertStmt.setString(2, value);
                    saved = insertStmt.executeUpdate() > 0;
                }
            }
            if (saved) {
                Map<String, String> local = settingsCache;
                if (local != null && value != null) {
                    local.put(key, value.trim());
                } else {
                    invalidateCache();
                }
            }
            return saved;
        } catch (SQLException e) {
            System.err.println("Error saving clinic setting '" + key + "': " + e.getMessage());
            return false;
        }
    }

    public Set<DayOfWeek> getWorkingDays() {
        String raw = getSetting(KEY_WORKING_DAYS, DEFAULT_WORKING_DAYS);
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        if (raw != null && !raw.trim().isEmpty()) {
            for (String token : raw.split(",")) {
                DayOfWeek day = parseDayToken(token.trim());
                if (day != null) {
                    days.add(day);
                }
            }
        }
        if (days.isEmpty()) {
            days.addAll(EnumSet.of(
                    DayOfWeek.SATURDAY,
                    DayOfWeek.SUNDAY,
                    DayOfWeek.MONDAY,
                    DayOfWeek.TUESDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY
            ));
        }
        return days;
    }

    public boolean setWorkingDays(Set<DayOfWeek> workingDays) {
        if (workingDays == null || workingDays.isEmpty()) {
            return false;
        }
        String serialized = workingDays.stream()
                .map(ClinicSettingsDAO::toShortToken)
                .collect(Collectors.joining(","));
        return setSetting(KEY_WORKING_DAYS, serialized);
    }

    public LocalTime getStartTime() {
        String raw = getSetting(KEY_START_TIME, DEFAULT_START_TIME);
        try {
            return LocalTime.parse(raw, TIME_FMT);
        } catch (Exception e) {
            return LocalTime.of(8, 0);
        }
    }

    public boolean setStartTime(LocalTime startTime) {
        if (startTime == null) {
            return false;
        }
        return setSetting(KEY_START_TIME, startTime.format(TIME_FMT));
    }

    public LocalTime getEndTime() {
        String raw = getSetting(KEY_END_TIME, DEFAULT_END_TIME);
        try {
            return LocalTime.parse(raw, TIME_FMT);
        } catch (Exception e) {
            return LocalTime.of(17, 0);
        }
    }

    public boolean setEndTime(LocalTime endTime) {
        if (endTime == null) {
            return false;
        }
        return setSetting(KEY_END_TIME, endTime.format(TIME_FMT));
    }

    public int getDefaultDurationMin() {
        String raw = getSetting(KEY_DEFAULT_DURATION, String.valueOf(DEFAULT_DURATION_MIN));
        try {
            int val = Integer.parseInt(raw.trim());
            return val > 0 ? val : DEFAULT_DURATION_MIN;
        } catch (NumberFormatException e) {
            return DEFAULT_DURATION_MIN;
        }
    }

    public boolean setDefaultDurationMin(int durationMin) {
        if (durationMin <= 0) {
            return false;
        }
        return setSetting(KEY_DEFAULT_DURATION, String.valueOf(durationMin));
    }

    public int getMaxConcurrentAppointments() {
        String raw = getSetting(KEY_MAX_CONCURRENT, String.valueOf(DEFAULT_MAX_CONCURRENT));
        try {
            int val = Integer.parseInt(raw.trim());
            return val > 0 ? val : DEFAULT_MAX_CONCURRENT;
        } catch (NumberFormatException e) {
            return DEFAULT_MAX_CONCURRENT;
        }
    }

    public boolean setMaxConcurrentAppointments(int maxConcurrent) {
        if (maxConcurrent <= 0) {
            return false;
        }
        return setSetting(KEY_MAX_CONCURRENT, String.valueOf(maxConcurrent));
    }

    public static DayOfWeek parseDayToken(String token) {
        if (token == null) {
            return null;
        }
        return switch (token.toUpperCase()) {
            case "MON", "MONDAY" -> DayOfWeek.MONDAY;
            case "TUE", "TUESDAY" -> DayOfWeek.TUESDAY;
            case "WED", "WEDNESDAY" -> DayOfWeek.WEDNESDAY;
            case "THU", "THURSDAY" -> DayOfWeek.THURSDAY;
            case "FRI", "FRIDAY" -> DayOfWeek.FRIDAY;
            case "SAT", "SATURDAY" -> DayOfWeek.SATURDAY;
            case "SUN", "SUNDAY" -> DayOfWeek.SUNDAY;
            default -> null;
        };
    }

    public static String toShortToken(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "MON";
            case TUESDAY -> "TUE";
            case WEDNESDAY -> "WED";
            case THURSDAY -> "THU";
            case FRIDAY -> "FRI";
            case SATURDAY -> "SAT";
            case SUNDAY -> "SUN";
        };
    }
}
