package config;

import dao.PatientDAO;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Coordinates the local client replica lifecycle and the "Flush First, Refresh Second"
 * synchronization pipeline with foreign-key ID remapping and baseline-compared conflict detection.
 */
public class OfflineSyncManager {

    private static final DateTimeFormatter DISPLAY_SYNC_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * Tables replicated from Host to Client in parent-first dependency order.
     */
    private static final String[] ALL_REPLICA_TABLES = {
            "Users", "clinic_settings", "worker", "paymentCheck",
            "bill", "billItem", "patient", "therapyPlan",
            "session", "payment", "appointment", "sold"
    };

    /**
     * Domain tables processed in parent-first dependency order when flushing offline changes to Host.
     */
    private static final String[] FLUSH_TABLE_ORDER = {
            "clinic_settings", "worker", "patient", "therapyPlan",
            "session", "payment", "appointment", "bill",
            "billItem", "sold", "paymentCheck"
    };

    public record SyncConflict(
            String tableName,
            String entityLabel,
            String rowId,
            String description
    ) {
    }

    public record SyncResult(
            boolean success,
            int flushedCount,
            List<SyncConflict> conflicts,
            String errorMessage
    ) {
    }

    private static class ConsolidatedChange {
        final String tableName;
        final String rowId;
        String opType; // INSERT, UPDATE, DELETE
        final String earliestBeforeJson;
        String latestAfterJson;

        ConsolidatedChange(String tableName, String rowId, String opType, String beforeJson, String afterJson) {
            this.tableName = tableName;
            this.rowId = rowId;
            this.opType = opType;
            this.earliestBeforeJson = beforeJson;
            this.latestAfterJson = afterJson;
        }
    }

    /**
     * Returns true if a local replica database file exists and has completed at least one sync from the Host.
     */
    public static boolean hasInitializedReplica() {
        if (!AppConfig.isReplicaInitialized()) {
            return false;
        }
        Path replicaPath = Paths.get(DatabaseConfig.getReplicaDatabasePath());
        try {
            return Files.exists(replicaPath) && Files.size(replicaPath) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns true if the local replica has un-flushed offline changes in offline_change_log.
     */
    public static boolean hasPendingOfflineChanges() {
        Path replicaPath = Paths.get(DatabaseConfig.getReplicaDatabasePath());
        if (!Files.exists(replicaPath)) {
            return false;
        }
        try (Connection replicaConn = DatabaseConfig.getReplicaConnection()) {
            return countPendingChanges(replicaConn) > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public static int countPendingChanges(Connection replicaConn) throws SQLException {
        try (Statement stmt = replicaConn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM offline_change_log")) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    /**
     * Executes the full "Flush First, Refresh Second" synchronization pipeline between the Client's
     * local replica and the Host database.
     */
    public static synchronized SyncResult synchronizeWithHost() {
        try (Connection hostConn = DatabaseConfig.getHostConnection();
             Connection replicaConn = DatabaseConfig.getReplicaConnection()) {
            return synchronizeWithHost(hostConn, replicaConn);
        } catch (Exception e) {
            System.err.println("⚠️ Offline synchronization failed: " + e.getMessage());
            return new SyncResult(false, 0, List.of(), e.getMessage());
        }
    }

    /**
     * Executes Phase 1 (Flush offline queue to Host) followed by Phase 2 (Refresh local replica from Host)
     * on the provided connections.
     */
    public static synchronized SyncResult synchronizeWithHost(Connection hostConn, Connection replicaConn) throws SQLException {
        config.DatabaseInitializer.initializeReplicaSchema(replicaConn);
        SyncResult flushResult = flushOfflineQueueToHost(hostConn, replicaConn);
        if (!flushResult.success()) {
            return flushResult;
        }
        refreshReplicaFromHost(hostConn, replicaConn);
        return flushResult;
    }

    /**
     * Phase 1: Flushes all pending mutations from replicaConn's offline_change_log to hostConn
     * in foreign-key dependency order, remapping local IDs and resolving concurrent edit conflicts
     * using "Host wins, flag the conflict".
     */
    public static synchronized SyncResult flushOfflineQueueToHost(Connection hostConn, Connection replicaConn) throws SQLException {
        DatabaseInitializer.initializeReplicaSchema(replicaConn);

        Map<String, Integer> snapshotMaxIds = loadSnapshotMaxIds(replicaConn);
        Map<String, ConsolidatedChange> consolidated = loadAndConsolidateChanges(replicaConn, snapshotMaxIds);

        if (consolidated.isEmpty()) {
            clearChangeLog(replicaConn);
            return new SyncResult(true, 0, List.of(), null);
        }

        List<SyncConflict> conflicts = new ArrayList<>();
        Map<String, Integer> idRemap = new HashMap<>();
        Set<Integer> affectedPatientIds = new LinkedHashSet<>();
        int appliedCount = 0;

        boolean origHostAutoCommit = hostConn.getAutoCommit();
        hostConn.setAutoCommit(false);
        try {
            // 1. Replay all consolidated INSERTs and UPDATEs in parent-first dependency order
            for (String table : FLUSH_TABLE_ORDER) {
                List<ConsolidatedChange> tableChanges = new ArrayList<>();
                for (ConsolidatedChange c : consolidated.values()) {
                    if (c.tableName.equalsIgnoreCase(table) && !"DELETE".equals(c.opType)) {
                        tableChanges.add(c);
                    }
                }

                // Sort numeric IDs ascending so self-referential FKs (e.g. payment.reversalOf) are inserted in order
                tableChanges.sort(Comparator.comparingInt(c -> parseIntOrZero(c.rowId)));

                for (ConsolidatedChange change : tableChanges) {
                    if ("INSERT".equals(change.opType)) {
                        Map<String, String> rowMap = OfflineChangeTrigger.parseRowJson(change.latestAfterJson);
                        remapForeignKeys(table, rowMap, idRemap);
                        int localId = parseIntOrZero(change.rowId);
                        int newHostId = insertRowIntoHost(hostConn, table, rowMap);
                        if (localId > 0 && newHostId > 0) {
                            idRemap.put(remapKey(table, localId), newHostId);
                            putIgnoreCase(rowMap, "id", String.valueOf(newHostId));
                        }
                        trackAffectedPatient(table, rowMap, affectedPatientIds);
                        appliedCount++;
                    } else if ("UPDATE".equals(change.opType)) {
                        Map<String, String> beforeMap = OfflineChangeTrigger.parseRowJson(change.earliestBeforeJson);
                        Map<String, String> afterMap = OfflineChangeTrigger.parseRowJson(change.latestAfterJson);
                        remapForeignKeys(table, afterMap, idRemap);

                        trackAffectedPatient(table, beforeMap, affectedPatientIds);
                        trackAffectedPatient(table, afterMap, affectedPatientIds);

                        // Ignore patient updates that only touched derived totalCost / totalPaid
                        if ("patient".equalsIgnoreCase(table) && rowsMatch("patient", beforeMap, afterMap)) {
                            continue;
                        }

                        Map<String, String> hostCurrentMap = fetchCurrentHostRow(hostConn, table, change.rowId);
                        if (hostCurrentMap == null) {
                            // Row was deleted on Host while edited on Client -> Host wins
                            conflicts.add(new SyncConflict(
                                    table,
                                    formatEntityLabel(table, beforeMap, change.rowId),
                                    change.rowId,
                                    formatEntityLabel(table, beforeMap, change.rowId)
                                            + " a été supprimé sur le poste principal pendant l'absence du réseau (priorité au serveur)."
                            ));
                        } else if (rowsMatch(table, beforeMap, hostCurrentMap) || rowsMatch(table, afterMap, hostCurrentMap)) {
                            // Host did not modify this row (or made identical change) -> apply Client update
                            updateRowOnHost(hostConn, table, change.rowId, afterMap);
                            appliedCount++;
                        } else {
                            // Both modified the row -> Host wins, flag conflict
                            conflicts.add(new SyncConflict(
                                    table,
                                    formatEntityLabel(table, hostCurrentMap, change.rowId),
                                    change.rowId,
                                    formatEntityLabel(table, hostCurrentMap, change.rowId)
                                            + " a été modifié simultanément sur le poste principal et ce poste (version du serveur conservée)."
                            ));
                        }
                    }
                }
            }

            // 2. Process consolidated DELETEs in reverse (child-first) dependency order
            for (int i = FLUSH_TABLE_ORDER.length - 1; i >= 0; i--) {
                String table = FLUSH_TABLE_ORDER[i];
                for (ConsolidatedChange change : consolidated.values()) {
                    if (change.tableName.equalsIgnoreCase(table) && "DELETE".equals(change.opType)) {
                        Map<String, String> beforeMap = OfflineChangeTrigger.parseRowJson(change.earliestBeforeJson);
                        trackAffectedPatient(table, beforeMap, affectedPatientIds);

                        Map<String, String> hostCurrentMap = fetchCurrentHostRow(hostConn, table, change.rowId);
                        if (hostCurrentMap == null) {
                            // Already deleted on Host
                            continue;
                        }
                        if (rowsMatch(table, beforeMap, hostCurrentMap)) {
                            deleteRowOnHost(hostConn, table, change.rowId);
                            appliedCount++;
                        } else {
                            // Host modified the row that Client tried to delete -> Host wins, flag conflict
                            conflicts.add(new SyncConflict(
                                    table,
                                    formatEntityLabel(table, hostCurrentMap, change.rowId),
                                    change.rowId,
                                    formatEntityLabel(table, hostCurrentMap, change.rowId)
                                            + " a été modifié sur le poste principal alors qu'il avait été supprimé hors-ligne (version du serveur conservée)."
                            ));
                        }
                    }
                }
            }

            // 3. Recompute derived patient totals (totalCost, totalPaid) on Host for all touched patients
            PatientDAO patientDAO = new PatientDAO();
            for (Integer pid : affectedPatientIds) {
                if (pid != null && pid > 0) {
                    patientDAO.updatePatientTotals(hostConn, pid);
                }
            }

            hostConn.commit();
        } catch (Exception e) {
            hostConn.rollback();
            throw e;
        } finally {
            hostConn.setAutoCommit(origHostAutoCommit);
        }

        // Clear offline_change_log only after Host commit succeeds
        clearChangeLog(replicaConn);
        return new SyncResult(true, appliedCount, conflicts, null);
    }

    /**
     * Phase 2: Refreshes all clinic tables in the local replica from the Host database.
     * Refuses to overwrite the replica if offline_change_log still contains un-flushed entries.
     */
    public static synchronized void refreshReplicaFromHost(Connection hostConn, Connection replicaConn) throws SQLException {
        DatabaseInitializer.initializeReplicaSchema(replicaConn);

        int pending = countPendingChanges(replicaConn);
        if (pending > 0) {
            throw new IllegalStateException(
                    "Refus d'écraser la réplique locale : " + pending + " modification(s) hors-ligne non synchronisée(s) sont encore en attente."
            );
        }

        boolean prevCapture = OfflineChangeTrigger.isCaptureEnabled();
        OfflineChangeTrigger.setCaptureEnabled(false);
        boolean origAutoCommit = replicaConn.getAutoCommit();
        replicaConn.setAutoCommit(false);

        try (Statement replicaStmt = replicaConn.createStatement()) {
            replicaStmt.execute("SET REFERENTIAL_INTEGRITY FALSE");
            try {
                for (int i = ALL_REPLICA_TABLES.length - 1; i >= 0; i--) {
                    replicaStmt.execute("DELETE FROM " + ALL_REPLICA_TABLES[i]);
                }
                replicaStmt.execute("DELETE FROM offline_snapshot_meta");

                for (String table : ALL_REPLICA_TABLES) {
                    copyTable(hostConn, replicaConn, table);
                    if (!"clinic_settings".equalsIgnoreCase(table)) {
                        int maxId = queryMaxId(replicaConn, table);
                        try (PreparedStatement metaPs = replicaConn.prepareStatement(
                                "INSERT INTO offline_snapshot_meta (table_name, max_id) VALUES (?, ?)")) {
                            metaPs.setString(1, table.toUpperCase(Locale.ROOT));
                            metaPs.setInt(2, maxId);
                            metaPs.executeUpdate();
                        }
                        replicaStmt.execute("ALTER TABLE " + table + " ALTER COLUMN id RESTART WITH " + (maxId + 1));
                    }
                }
            } finally {
                replicaStmt.execute("SET REFERENTIAL_INTEGRITY TRUE");
            }

            replicaConn.commit();
            dao.ClinicSettingsDAO.invalidateCache();
            String nowStr = LocalDateTime.now().format(DISPLAY_SYNC_FMT);
            AppConfig.setReplicaInitialized(true);
            AppConfig.setReplicaLastSync(nowStr);
        } catch (Exception e) {
            replicaConn.rollback();
            throw e;
        } finally {
            replicaConn.setAutoCommit(origAutoCommit);
            OfflineChangeTrigger.setCaptureEnabled(prevCapture);
        }
    }

    private static Map<String, Integer> loadSnapshotMaxIds(Connection replicaConn) throws SQLException {
        Map<String, Integer> map = new HashMap<>();
        try (Statement stmt = replicaConn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT table_name, max_id FROM offline_snapshot_meta")) {
            while (rs.next()) {
                map.put(rs.getString("table_name").toUpperCase(Locale.ROOT), rs.getInt("max_id"));
            }
        }
        return map;
    }

    private static Map<String, ConsolidatedChange> loadAndConsolidateChanges(
            Connection replicaConn, Map<String, Integer> snapshotMaxIds) throws SQLException {
        Map<String, ConsolidatedChange> consolidated = new LinkedHashMap<>();
        String sql = "SELECT seq_id, table_name, op_type, row_id, before_json, after_json FROM offline_change_log ORDER BY seq_id ASC";
        try (Statement stmt = replicaConn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String table = rs.getString("table_name");
                String opType = rs.getString("op_type");
                String rowId = rs.getString("row_id");
                String beforeJson = rs.getString("before_json");
                String afterJson = rs.getString("after_json");

                String key = table.toUpperCase(Locale.ROOT) + ":" + rowId;
                ConsolidatedChange existing = consolidated.get(key);

                int maxSnapId = snapshotMaxIds.getOrDefault(table.toUpperCase(Locale.ROOT), 0);
                int numericId = parseIntOrZero(rowId);
                boolean isOfflineCreatedId = !"clinic_settings".equalsIgnoreCase(table) && numericId > maxSnapId;

                if (existing == null) {
                    if ("UPDATE".equals(opType) && isOfflineCreatedId) {
                        consolidated.put(key, new ConsolidatedChange(table, rowId, "INSERT", null, afterJson));
                    } else if ("DELETE".equals(opType) && isOfflineCreatedId) {
                        // Deleted a row that was created offline -> no-op
                    } else {
                        consolidated.put(key, new ConsolidatedChange(table, rowId, opType, beforeJson, afterJson));
                    }
                } else {
                    if ("INSERT".equals(existing.opType)) {
                        if ("UPDATE".equals(opType)) {
                            existing.latestAfterJson = afterJson;
                        } else if ("DELETE".equals(opType)) {
                            // Created and deleted while offline -> cancel out completely
                            consolidated.remove(key);
                        }
                    } else if ("UPDATE".equals(existing.opType)) {
                        if ("UPDATE".equals(opType)) {
                            existing.latestAfterJson = afterJson;
                        } else if ("DELETE".equals(opType)) {
                            existing.opType = "DELETE";
                            existing.latestAfterJson = null;
                        }
                    }
                }
            }
        }
        return consolidated;
    }

    private static void remapForeignKeys(String table, Map<String, String> rowMap, Map<String, Integer> idRemap) {
        if (rowMap == null) return;
        remapColumnIfPresent(rowMap, "patientId", "patient", idRemap);
        remapColumnIfPresent(rowMap, "therapyPlanId", "therapyPlan", idRemap);
        remapColumnIfPresent(rowMap, "sessionId", "session", idRemap);
        remapColumnIfPresent(rowMap, "workerId", "worker", idRemap);
        remapColumnIfPresent(rowMap, "billId", "bill", idRemap);
        remapColumnIfPresent(rowMap, "reversalOf", "payment", idRemap);

        // Also remap embedded #id references in payment.reason (e.g. "Paiement séance #5" or "Forfait thérapie #3")
        if ("payment".equalsIgnoreCase(table)) {
            String reason = getIgnoreCase(rowMap, "reason");
            String paymentType = getIgnoreCase(rowMap, "paymentType");
            if (reason != null && reason.contains("#")) {
                int hashIdx = reason.lastIndexOf('#');
                String suffix = reason.substring(hashIdx + 1).trim();
                int oldRefId = parseIntOrZero(suffix);
                if (oldRefId > 0) {
                    String targetTable = "THERAPY_PLAN".equalsIgnoreCase(paymentType) ? "therapyPlan" : "session";
                    Integer newRefId = idRemap.get(remapKey(targetTable, oldRefId));
                    if (newRefId != null) {
                        putIgnoreCase(rowMap, "reason", reason.substring(0, hashIdx + 1) + newRefId);
                    }
                }
            }
        }
    }

    private static void remapColumnIfPresent(Map<String, String> rowMap, String colName, String parentTable, Map<String, Integer> idRemap) {
        for (Map.Entry<String, String> entry : rowMap.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(colName) && entry.getValue() != null) {
                int localFk = parseIntOrZero(entry.getValue());
                if (localFk > 0) {
                    Integer mappedHostId = idRemap.get(remapKey(parentTable, localFk));
                    if (mappedHostId != null) {
                        entry.setValue(String.valueOf(mappedHostId));
                    }
                }
            }
        }
    }

    private static int insertRowIntoHost(Connection hostConn, String table, Map<String, String> rowMap) throws SQLException {
        if ("clinic_settings".equalsIgnoreCase(table)) {
            String key = getIgnoreCase(rowMap, "settingKey");
            String val = getIgnoreCase(rowMap, "settingValue");
            try (PreparedStatement ps = hostConn.prepareStatement(
                    "MERGE INTO clinic_settings (settingKey, settingValue) KEY(settingKey) VALUES (?, ?)")) {
                ps.setString(1, key);
                ps.setString(2, val);
                ps.executeUpdate();
            }
            return 0;
        }

        List<String> cols = new ArrayList<>();
        List<String> vals = new ArrayList<>();
        for (Map.Entry<String, String> entry : rowMap.entrySet()) {
            if (!"id".equalsIgnoreCase(entry.getKey())) {
                cols.add(entry.getKey());
                vals.add(entry.getValue());
            }
        }

        String colList = String.join(", ", cols);
        String placeholders = String.join(", ", Collections.nCopies(cols.size(), "?"));
        String sql = "INSERT INTO " + table + " (" + colList + ") VALUES (" + placeholders + ")";

        try (PreparedStatement ps = hostConn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < vals.size(); i++) {
                ps.setString(i + 1, vals.get(i));
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        return 0;
    }

    private static void updateRowOnHost(Connection hostConn, String table, String rowId, Map<String, String> afterMap) throws SQLException {
        String pkCol = "clinic_settings".equalsIgnoreCase(table) ? "settingKey" : "id";
        List<String> assignments = new ArrayList<>();
        List<String> vals = new ArrayList<>();

        for (Map.Entry<String, String> entry : afterMap.entrySet()) {
            String col = entry.getKey();
            if (col.equalsIgnoreCase(pkCol)) {
                continue;
            }
            // Skip derived patient columns; they are recomputed via PatientDAO.updatePatientTotals
            if ("patient".equalsIgnoreCase(table)
                    && (col.equalsIgnoreCase("totalCost") || col.equalsIgnoreCase("totalPaid"))) {
                continue;
            }
            assignments.add(col + " = ?");
            vals.add(entry.getValue());
        }

        if (assignments.isEmpty()) {
            return;
        }

        String sql = "UPDATE " + table + " SET " + String.join(", ", assignments) + " WHERE " + pkCol + " = ?";
        try (PreparedStatement ps = hostConn.prepareStatement(sql)) {
            for (int i = 0; i < vals.size(); i++) {
                ps.setString(i + 1, vals.get(i));
            }
            ps.setString(vals.size() + 1, rowId);
            ps.executeUpdate();
        }
    }

    private static void deleteRowOnHost(Connection hostConn, String table, String rowId) throws SQLException {
        String pkCol = "clinic_settings".equalsIgnoreCase(table) ? "settingKey" : "id";
        String sql = "DELETE FROM " + table + " WHERE " + pkCol + " = ?";
        try (PreparedStatement ps = hostConn.prepareStatement(sql)) {
            ps.setString(1, rowId);
            ps.executeUpdate();
        }
    }

    private static Map<String, String> fetchCurrentHostRow(Connection hostConn, String table, String rowId) throws SQLException {
        String pkCol = "clinic_settings".equalsIgnoreCase(table) ? "settingKey" : "id";
        String sql = "SELECT * FROM " + table + " WHERE " + pkCol + " = ?";
        try (PreparedStatement ps = hostConn.prepareStatement(sql)) {
            ps.setString(1, rowId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String json = OfflineChangeTrigger.serializeResultSetRow(rs);
                    return OfflineChangeTrigger.parseRowJson(json);
                }
            }
        }
        return null;
    }

    private static boolean rowsMatch(String table, Map<String, String> mapA, Map<String, String> mapB) {
        if (mapA == null || mapB == null) {
            return false;
        }
        for (Map.Entry<String, String> entryA : mapA.entrySet()) {
            String col = entryA.getKey();
            if ("patient".equalsIgnoreCase(table)
                    && (col.equalsIgnoreCase("totalCost") || col.equalsIgnoreCase("totalPaid"))) {
                continue;
            }
            if (col.equalsIgnoreCase("createdAt")) {
                continue;
            }
            String valA = normalizeComparable(entryA.getValue());
            String valB = normalizeComparable(getIgnoreCase(mapB, col));
            if (!Objects.equals(valA, valB)) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeComparable(String val) {
        if (val == null) return null;
        String trimmed = val.trim();
        try {
            java.math.BigDecimal bd = new java.math.BigDecimal(trimmed).stripTrailingZeros();
            return bd.toPlainString();
        } catch (NumberFormatException ignored) {
            return trimmed;
        }
    }

    private static void trackAffectedPatient(String table, Map<String, String> rowMap, Set<Integer> affectedPatientIds) {
        if (rowMap == null) return;
        if ("patient".equalsIgnoreCase(table)) {
            int id = parseIntOrZero(getIgnoreCase(rowMap, "id"));
            if (id > 0) affectedPatientIds.add(id);
        } else {
            int pid = parseIntOrZero(getIgnoreCase(rowMap, "patientId"));
            if (pid > 0) affectedPatientIds.add(pid);
        }
    }

    private static void copyTable(Connection sourceConn, Connection targetConn, String table) throws SQLException {
        try (Statement readStmt = sourceConn.createStatement();
             ResultSet rs = readStmt.executeQuery("SELECT * FROM " + table)) {
            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();
            List<String> colNames = new ArrayList<>(colCount);
            for (int i = 1; i <= colCount; i++) {
                colNames.add(meta.getColumnName(i));
            }
            String placeholders = String.join(", ", Collections.nCopies(colCount, "?"));
            String insertSql = "INSERT INTO " + table + " (" + String.join(", ", colNames) + ") VALUES (" + placeholders + ")";
            try (PreparedStatement insertPs = targetConn.prepareStatement(insertSql)) {
                while (rs.next()) {
                    for (int i = 1; i <= colCount; i++) {
                        insertPs.setObject(i, rs.getObject(i));
                    }
                    insertPs.addBatch();
                }
                insertPs.executeBatch();
            }
        }
    }

    private static int queryMaxId(Connection conn, String table) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COALESCE(MAX(id), 0) FROM " + table)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    private static void clearChangeLog(Connection replicaConn) throws SQLException {
        try (Statement stmt = replicaConn.createStatement()) {
            stmt.execute("DELETE FROM offline_change_log");
        }
    }

    private static String remapKey(String table, int localId) {
        return table.toUpperCase(Locale.ROOT) + ":" + localId;
    }

    private static String getIgnoreCase(Map<String, String> map, String key) {
        if (map == null) return null;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static void putIgnoreCase(Map<String, String> map, String key, String value) {
        for (String existingKey : new ArrayList<>(map.keySet())) {
            if (existingKey.equalsIgnoreCase(key)) {
                map.put(existingKey, value);
                return;
            }
        }
        map.put(key, value);
    }

    private static int parseIntOrZero(String s) {
        if (s == null) return 0;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String formatEntityLabel(String table, Map<String, String> rowMap, String rowId) {
        String friendlyType = switch (table.toLowerCase(Locale.ROOT)) {
            case "patient" -> "Patient";
            case "appointment" -> "Rendez-vous";
            case "session" -> "Séance";
            case "therapyplan" -> "Forfait thérapie";
            case "payment" -> "Paiement";
            case "bill" -> "Facture";
            case "sold" -> "Vente";
            case "worker" -> "Employé";
            default -> table;
        };
        String name = getIgnoreCase(rowMap, "name");
        if (name == null) name = getIgnoreCase(rowMap, "guestName");
        if (name == null) name = getIgnoreCase(rowMap, "itemName");
        if (name != null && !name.isBlank()) {
            return friendlyType + " \"" + name + "\" (#" + rowId + ")";
        }
        return friendlyType + " #" + rowId;
    }
}
