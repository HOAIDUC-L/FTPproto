package com.ftpproto.server.dao;

import com.ftpproto.common.model.ActivityLog;
import com.ftpproto.server.db.ConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object ghi và đọc nhật ký hoạt động người dùng trong activity_logs.
 */
public class ActivityLogDAO {
    private static final Logger logger = LoggerFactory.getLogger(ActivityLogDAO.class);
    private final ConnectionPool pool;

    public ActivityLogDAO() {
        this.pool = ConnectionPool.getInstance();
    }

    public ActivityLogDAO(ConnectionPool pool) {
        this.pool = pool;
    }

    public void log(ActivityLog log) {
        String sql = """
            INSERT INTO activity_logs (username, action, file_path, file_size, client_ip, status, details, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
        """;
        try (Connection conn = pool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, log.getUsername());
            ps.setString(2, log.getAction());
            ps.setString(3, log.getFilePath());
            ps.setLong(4, log.getFileSize());
            ps.setString(5, log.getClientIp());
            ps.setString(6, log.getStatus());
            ps.setString(7, log.getDetails());

            ps.executeUpdate();
        } catch (Exception e) {
            logger.warn("Không thể lưu activity log vào DB: {}", e.getMessage());
        }
    }

    public List<ActivityLog> findRecent(int limit) {
        List<ActivityLog> list = new ArrayList<>();
        String sql = "SELECT * FROM activity_logs ORDER BY id DESC LIMIT ?";
        try (Connection conn = pool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToLog(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Lỗi khi lấy danh sách activity_logs gần đây", e);
        }
        return list;
    }

    private ActivityLog mapResultSetToLog(ResultSet rs) throws SQLException {
        ActivityLog log = new ActivityLog();
        log.setId(rs.getLong("id"));
        log.setUsername(rs.getString("username"));
        log.setAction(rs.getString("action"));
        log.setFilePath(rs.getString("file_path"));
        log.setFileSize(rs.getLong("file_size"));
        log.setClientIp(rs.getString("client_ip"));
        log.setStatus(rs.getString("status"));
        log.setDetails(rs.getString("details"));
        Timestamp cat = rs.getTimestamp("created_at");
        if (cat != null) log.setCreatedAt(cat.toLocalDateTime());
        return log;
    }
}
