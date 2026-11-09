package com.ftpproto.server;

import com.ftpproto.common.model.ActivityLog;
import com.ftpproto.common.model.User;
import com.ftpproto.server.dao.ActivityLogDAO;
import com.ftpproto.server.dao.UserDAO;
import com.ftpproto.server.db.ConnectionPool;
import com.ftpproto.server.db.DatabaseInitializer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class DatabaseConnectionTest {

    @Test
    public void testPostgreSQLConnectionAndDAO() {
        ConnectionPool pool = ConnectionPool.getInstance();
        if (!pool.isAvailable()) {
            System.out.println("Cơ sở dữ liệu PostgreSQL chưa mở tại localhost:5432, bỏ qua kiểm tra trực tiếp.");
            return;
        }

        // Tự động kiểm tra / khởi tạo bảng
        DatabaseInitializer.initializeDatabase();

        UserDAO userDAO = new UserDAO();
        ActivityLogDAO logDAO = new ActivityLogDAO();

        // 1. Kiểm tra tài khoản admin mặc định
        Optional<User> adminOpt = userDAO.findByUsername("admin");
        assertTrue(adminOpt.isPresent(), "Phải tìm thấy tài khoản admin trong PostgreSQL");
        User admin = adminOpt.get();
        assertEquals("admin", admin.getUsername());
        assertTrue(admin.isCanRead());
        assertTrue(admin.isCanWrite());
        assertTrue(admin.isActive());
        assertTrue(com.ftpproto.common.util.PasswordHasher.verify("admin123", admin.getSalt(), admin.getPasswordHash()),
                "Mật khẩu admin123 phải verify thành công với hash và salt trong DB");

        // 2. Ghi và đọc Activity Log
        ActivityLog testLog = new ActivityLog("admin", "LOGIN", null, 0, "127.0.0.1", "SUCCESS", "Đăng nhập thử nghiệm");
        logDAO.log(testLog);

        List<ActivityLog> recent = logDAO.findRecent(10);
        assertNotNull(recent);
        assertFalse(recent.isEmpty());
        assertTrue(recent.stream().anyMatch(l -> "admin".equals(l.getUsername()) && "LOGIN".equals(l.getAction())));
    }
}
