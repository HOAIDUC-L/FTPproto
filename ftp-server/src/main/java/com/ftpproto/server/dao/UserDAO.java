package com.ftpproto.server.dao;

import com.ftpproto.common.model.User;
import com.ftpproto.server.db.ConnectionPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object quản lý dữ liệu người dùng trong bảng users.
 */
public class UserDAO {
    private static final Logger logger = LoggerFactory.getLogger(UserDAO.class);
    private final ConnectionPool pool;

    public UserDAO() {
        this.pool = ConnectionPool.getInstance();
    }

    public UserDAO(ConnectionPool pool) {
        this.pool = pool;
    }

    public Optional<User> findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = pool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToUser(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Lỗi khi tìm user theo username: {}", username, e);
        }
        return Optional.empty();
    }

    public Optional<User> findById(Long id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = pool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToUser(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Lỗi khi tìm user theo id: {}", id, e);
        }
        return Optional.empty();
    }

    public List<User> findAll() {
        List<User> list = new ArrayList<>();
        String sql = "SELECT * FROM users ORDER BY id ASC";
        try (Connection conn = pool.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapResultSetToUser(rs));
            }
        } catch (SQLException e) {
            logger.error("Lỗi khi lấy danh sách user", e);
        }
        return list;
    }

    public boolean create(User user) {
        String sql = """
            INSERT INTO users (username, password_hash, salt, home_directory, can_read, can_write, can_delete, is_active, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        """;
        try (Connection conn = pool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPasswordHash());
            ps.setString(3, user.getSalt());
            ps.setString(4, user.getHomeDirectory());
            ps.setBoolean(5, user.isCanRead());
            ps.setBoolean(6, user.isCanWrite());
            ps.setBoolean(7, user.isCanDelete());
            ps.setBoolean(8, user.isActive());

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        user.setId(generatedKeys.getLong(1));
                    }
                }
                return true;
            }
        } catch (SQLException e) {
            logger.error("Lỗi khi tạo user: {}", user.getUsername(), e);
        }
        return false;
    }

    public boolean update(User user) {
        String sql = """
            UPDATE users SET 
                home_directory = ?, 
                can_read = ?, 
                can_write = ?, 
                can_delete = ?, 
                is_active = ?, 
                updated_at = CURRENT_TIMESTAMP
        """ + (user.getPasswordHash() != null && !user.getPasswordHash().isEmpty() ? ", password_hash = ?, salt = ? " : " ") +
        "WHERE id = ?";

        try (Connection conn = pool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            ps.setString(idx++, user.getHomeDirectory());
            ps.setBoolean(idx++, user.isCanRead());
            ps.setBoolean(idx++, user.isCanWrite());
            ps.setBoolean(idx++, user.isCanDelete());
            ps.setBoolean(idx++, user.isActive());
            if (user.getPasswordHash() != null && !user.getPasswordHash().isEmpty()) {
                ps.setString(idx++, user.getPasswordHash());
                ps.setString(idx++, user.getSalt());
            }
            ps.setLong(idx, user.getId());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Lỗi khi cập nhật user: {}", user.getUsername(), e);
        }
        return false;
    }

    public boolean delete(Long id) {
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = pool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Lỗi khi xóa user id: {}", id, e);
        }
        return false;
    }

    public boolean setActive(Long id, boolean active) {
        String sql = "UPDATE users SET is_active = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = pool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, active);
            ps.setLong(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Lỗi khi đổi trạng thái user id: {}", id, e);
        }
        return false;
    }

    private User mapResultSetToUser(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getLong("id"));
        u.setUsername(rs.getString("username"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setSalt(rs.getString("salt"));
        u.setHomeDirectory(rs.getString("home_directory"));
        u.setCanRead(rs.getBoolean("can_read"));
        u.setCanWrite(rs.getBoolean("can_write"));
        u.setCanDelete(rs.getBoolean("can_delete"));
        u.setActive(rs.getBoolean("is_active"));
        Timestamp cat = rs.getTimestamp("created_at");
        if (cat != null) u.setCreatedAt(cat.toLocalDateTime());
        Timestamp uat = rs.getTimestamp("updated_at");
        if (uat != null) u.setUpdatedAt(uat.toLocalDateTime());
        return u;
    }
}
