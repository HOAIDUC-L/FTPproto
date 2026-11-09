package com.ftpproto.server.db;

import com.ftpproto.common.util.PasswordHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Tự động kiểm tra và khởi tạo cấu trúc bảng (schema) cùng tài khoản mặc định.
 */
public class DatabaseInitializer {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);

    public static void initializeDatabase() {
        ConnectionPool pool = ConnectionPool.getInstance();
        if (!pool.isAvailable()) {
            logger.warn("Cơ sở dữ liệu không khả dụng, bỏ qua bước khởi tạo Schema tự động.");
            return;
        }

        try (Connection conn = pool.getConnection()) {
            // 1. Tạo bảng users
            String createUsersSql = """
                CREATE TABLE IF NOT EXISTS users (
                    id SERIAL PRIMARY KEY,
                    username VARCHAR(50) UNIQUE NOT NULL,
                    password_hash VARCHAR(128) NOT NULL,
                    salt VARCHAR(64) NOT NULL,
                    home_directory VARCHAR(255) NOT NULL,
                    can_read BOOLEAN DEFAULT TRUE NOT NULL,
                    can_write BOOLEAN DEFAULT TRUE NOT NULL,
                    can_delete BOOLEAN DEFAULT TRUE NOT NULL,
                    is_active BOOLEAN DEFAULT TRUE NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
                );
            """;

            // 2. Tạo bảng activity_logs
            String createLogsSql = """
                CREATE TABLE IF NOT EXISTS activity_logs (
                    id BIGSERIAL PRIMARY KEY,
                    username VARCHAR(50),
                    action VARCHAR(30) NOT NULL,
                    file_path VARCHAR(500),
                    file_size BIGINT DEFAULT 0,
                    client_ip VARCHAR(50),
                    status VARCHAR(20) NOT NULL,
                    details TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
                );
            """;

            try (Statement stmt = conn.createStatement()) {
                stmt.execute(createUsersSql);
                stmt.execute(createLogsSql);
                logger.info("Đã xác thực và tạo bảng users & activity_logs thành công.");
            }

            // 3. Kiểm tra và đảm bảo tài khoản admin tồn tại và mật khẩu admin123 hợp lệ
            String checkAdminSql = "SELECT id, password_hash, salt FROM users WHERE username = 'admin'";
            Long adminId = null;
            String currentHash = null;
            String currentSalt = null;

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(checkAdminSql)) {
                if (rs.next()) {
                    adminId = rs.getLong("id");
                    currentHash = rs.getString("password_hash");
                    currentSalt = rs.getString("salt");
                }
            }

            String defaultHome = "data" + File.separator + "server_root" + File.separator + "admin";
            if (adminId == null) {
                // Chưa có tài khoản admin -> Tạo mới
                String salt = PasswordHasher.generateSalt();
                String hash = PasswordHasher.hash("admin123", salt);
                String insertAdminSql = """
                    INSERT INTO users (username, password_hash, salt, home_directory, can_read, can_write, can_delete, is_active)
                    VALUES (?, ?, ?, ?, TRUE, TRUE, TRUE, TRUE);
                """;
                try (PreparedStatement ps = conn.prepareStatement(insertAdminSql)) {
                    ps.setString(1, "admin");
                    ps.setString(2, hash);
                    ps.setString(3, salt);
                    ps.setString(4, defaultHome);
                    ps.executeUpdate();
                    logger.info("Đã tạo tài khoản mặc định: admin / admin123 (Home: {})", defaultHome);
                }
            } else if (!PasswordHasher.verify("admin123", currentSalt, currentHash)) {
                // Đã có tài khoản admin nhưng hash cũ không khớp mật khẩu admin123 -> Tự động sửa lại
                String salt = PasswordHasher.generateSalt();
                String hash = PasswordHasher.hash("admin123", salt);
                String fixAdminSql = "UPDATE users SET password_hash = ?, salt = ?, is_active = TRUE WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(fixAdminSql)) {
                    ps.setString(1, hash);
                    ps.setString(2, salt);
                    ps.setLong(3, adminId);
                    ps.executeUpdate();
                    logger.info("Đã đồng bộ lại mật khẩu chuẩn admin123 cho tài khoản admin.");
                }
            }

            // 4. Tạo thư mục đĩa vật lý mặc định nếu chưa có
            File adminDir = new File("data" + File.separator + "server_root" + File.separator + "admin");
            if (!adminDir.exists()) {
                adminDir.mkdirs();
                // Tạo một file chào mừng mẫu
                File welcomeFile = new File(adminDir, "welcome.txt");
                if (!welcomeFile.exists()) {
                    java.nio.file.Files.writeString(welcomeFile.toPath(), 
                            "Chào mừng bạn đến với hệ thống máy chủ FTPproto Server!\r\n" +
                            "Hệ thống phát triển bởi Java Senior Software Engineer.\r\n");
                }
            }

        } catch (Exception e) {
            logger.error("Lỗi khi khởi tạo database: {}", e.getMessage(), e);
        }
    }
}
