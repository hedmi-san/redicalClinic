package dao;

import config.DatabaseConfig;
import model.Worker;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class WorkerDAO {

    public List<Worker> getAllWorkers() {
        List<Worker> workers = new ArrayList<>();
        String query = "SELECT * FROM worker";

        try (Connection conn = DatabaseConfig.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                workers.add(mapResultSetToWorker(rs));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching all workers: " + e.getMessage());
        }
        return workers;
    }

    public List<Worker> searchWorkersByName(String name) {
        List<Worker> workers = new ArrayList<>();
        String query = "SELECT * FROM worker WHERE name LIKE ?";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setString(1, "%" + name + "%");
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    workers.add(mapResultSetToWorker(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error searching workers by name: " + e.getMessage());
        }
        return workers;
    }

    public boolean addWorker(Worker worker) {
        String query = "INSERT INTO worker (name, birthDate, birthPlace, phoneNumber, identityCardNumber, function, famillySituation) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {

            setWorkerPreparedStatement(pstmt, worker);
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;

        } catch (SQLException e) {
            System.err.println("Error adding worker: " + e.getMessage());
            return false;
        }
    }

    public boolean updateWorker(Worker worker) {
        String query = "UPDATE worker SET name = ?, birthDate = ?, birthPlace = ?, phoneNumber = ?, identityCardNumber = ?, function = ?, famillySituation = ? WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {

            setWorkerPreparedStatement(pstmt, worker);
            pstmt.setInt(8, worker.getId());
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;

        } catch (SQLException e) {
            System.err.println("Error updating worker: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteWorker(int id) {
        String query = "DELETE FROM worker WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {

            pstmt.setInt(1, id);
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;

        } catch (SQLException e) {
            System.err.println("Error deleting worker: " + e.getMessage());
            return false;
        }
    }

    private void setWorkerPreparedStatement(PreparedStatement pstmt, Worker worker) throws SQLException {
        pstmt.setString(1, worker.getName());
        pstmt.setString(2, worker.getBirthDate());
        pstmt.setString(3, worker.getBirthPlace());
        pstmt.setString(4, worker.getPhoneNumber());
        pstmt.setString(5, worker.getIdentityCardNumber());
        pstmt.setString(6, worker.getFunction());
        pstmt.setString(7, worker.getFamillySituation());
    }

    private Worker mapResultSetToWorker(ResultSet rs) throws SQLException {
        Worker worker = new Worker();
        worker.setId(rs.getInt("id"));
        worker.setName(rs.getString("name"));
        worker.setBirthDate(rs.getString("birthDate"));
        worker.setBirthPlace(rs.getString("birthPlace"));
        worker.setPhoneNumber(rs.getString("phoneNumber"));
        worker.setIdentityCardNumber(rs.getString("identityCardNumber"));
        worker.setFunction(rs.getString("function"));
        worker.setFamillySituation(rs.getString("famillySituation"));
        return worker;
    }
}
