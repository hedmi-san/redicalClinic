package config;

import org.h2.api.Trigger;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

// H2 Database Trigger attached to client local replica tables to capture INSERT, UPDATE, and DELETE
// mutations into offline_change_log without modifying DAO classes.
public class OfflineChangeTrigger implements Trigger {

    private static final AtomicBoolean CAPTURE_ENABLED = new AtomicBoolean(true);
    private static final ConcurrentHashMap<String, List<String>> COLUMN_CACHE = new ConcurrentHashMap<>();

    private String tableName;
    private List<String> columnNames = List.of();

    public static void setCaptureEnabled(boolean enabled) {
        CAPTURE_ENABLED.set(enabled);
    }

    public static boolean isCaptureEnabled() {
        return CAPTURE_ENABLED.get();
    }

    @Override
    public void init(Connection conn, String schemaName, String triggerName,
                     String tableName, boolean before, int type) throws SQLException {
        this.tableName = tableName;
        loadColumnNames(conn);
    }

    private void loadColumnNames(Connection conn) throws SQLException {
        String key = tableName.toUpperCase(Locale.ROOT);
        List<String> cached = COLUMN_CACHE.get(key);
        if (cached != null && !cached.isEmpty()) {
            this.columnNames = cached;
            return;
        }
        List<String> cols = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM " + tableName + " WHERE 1=0")) {
            ResultSetMetaData meta = rs.getMetaData();
            int count = meta.getColumnCount();
            for (int i = 1; i <= count; i++) {
                cols.add(meta.getColumnName(i));
            }
        }
        List<String> immutableCols = List.copyOf(cols);
        COLUMN_CACHE.put(key, immutableCols);
        this.columnNames = immutableCols;
    }

    @Override
    public void fire(Connection conn, Object[] oldRow, Object[] newRow) throws SQLException {
        if (!CAPTURE_ENABLED.get()) {
            return;
        }
        if (columnNames.isEmpty()) {
            loadColumnNames(conn);
        }

        String opType;
        String rowId;
        String beforeJson = null;
        String afterJson = null;

        if (oldRow == null && newRow != null) {
            opType = "INSERT";
            rowId = String.valueOf(newRow[0]);
            afterJson = serializeRow(columnNames, newRow);
        } else if (oldRow != null && newRow != null) {
            opType = "UPDATE";
            rowId = String.valueOf(newRow[0]);
            beforeJson = serializeRow(columnNames, oldRow);
            afterJson = serializeRow(columnNames, newRow);
        } else if (oldRow != null) {
            opType = "DELETE";
            rowId = String.valueOf(oldRow[0]);
            beforeJson = serializeRow(columnNames, oldRow);
        } else {
            return;
        }

        String sql = "INSERT INTO offline_change_log (table_name, op_type, row_id, before_json, after_json) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            ps.setString(2, opType);
            ps.setString(3, rowId);
            ps.setString(4, beforeJson);
            ps.setString(5, afterJson);
            ps.executeUpdate();
        }
    }

    @Override
    public void close() {
    }

    @Override
    public void remove() {
    }

    public static String normalizeValue(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof Number num) {
            try {
                BigDecimal bd = new BigDecimal(num.toString()).stripTrailingZeros();
                return bd.toPlainString();
            } catch (NumberFormatException ignored) {
            }
        }
        return String.valueOf(val);
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    // Serializes a table row into a deterministic JSON object mapping columnName -> string value or null.
    public static String serializeRow(List<String> columns, Object[] values) {
        StringBuilder sb = new StringBuilder("{");
        int len = Math.min(columns.size(), values.length);
        for (int i = 0; i < len; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("\"").append(escapeJson(columns.get(i))).append("\":");
            Object val = values[i];
            if (val == null) {
                sb.append("null");
            } else {
                sb.append("\"").append(escapeJson(normalizeValue(val))).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    // Serializes a current JDBC ResultSet row into the exact same JSON format as serializeRow.
    public static String serializeResultSetRow(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int count = meta.getColumnCount();
        List<String> cols = new ArrayList<>(count);
        Object[] vals = new Object[count];
        for (int i = 1; i <= count; i++) {
            cols.add(meta.getColumnName(i));
            vals[i - 1] = rs.getObject(i);
        }
        return serializeRow(cols, vals);
    }

    // Parses a JSON object created by serializeRow back into an ordered Map of column -> value (or null).
    public static Map<String, String> parseRowJson(String json) {
        Map<String, String> map = new LinkedHashMap<>();
        if (json == null) {
            return map;
        }
        String trimmed = json.trim();
        if (trimmed.length() <= 2 || !trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return map;
        }

        int i = 1;
        int end = trimmed.length() - 1;
        while (i < end) {
            while (i < end && (Character.isWhitespace(trimmed.charAt(i)) || trimmed.charAt(i) == ',')) {
                i++;
            }
            if (i >= end) {
                break;
            }

            if (trimmed.charAt(i) != '"') {
                break;
            }
            StringBuilder keyBuf = new StringBuilder();
            i = readJsonString(trimmed, i + 1, end, keyBuf);

            while (i < end && (Character.isWhitespace(trimmed.charAt(i)) || trimmed.charAt(i) == ':')) {
                i++;
            }
            if (i >= end) {
                break;
            }

            if (trimmed.charAt(i) == '"') {
                StringBuilder valBuf = new StringBuilder();
                i = readJsonString(trimmed, i + 1, end, valBuf);
                map.put(keyBuf.toString(), valBuf.toString());
            } else if (trimmed.regionMatches(i, "null", 0, 4)) {
                map.put(keyBuf.toString(), null);
                i += 4;
            } else {
                int nextComma = trimmed.indexOf(',', i);
                if (nextComma < 0 || nextComma > end) {
                    nextComma = end;
                }
                map.put(keyBuf.toString(), trimmed.substring(i, nextComma).trim());
                i = nextComma;
            }
        }
        return map;
    }

    private static int readJsonString(String s, int start, int end, StringBuilder out) {
        int i = start;
        while (i < end) {
            char c = s.charAt(i++);
            if (c == '\\' && i < end) {
                char next = s.charAt(i++);
                switch (next) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    default -> out.append(next);
                }
            } else if (c == '"') {
                break;
            } else {
                out.append(c);
            }
        }
        return i;
    }
}
