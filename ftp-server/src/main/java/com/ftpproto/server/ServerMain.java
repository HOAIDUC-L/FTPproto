package com.ftpproto.server;

import com.formdev.flatlaf.FlatDarkLaf;
import com.ftpproto.server.config.ServerConfig;
import com.ftpproto.server.core.FTPServer;
import com.ftpproto.server.dao.ActivityLogDAO;
import com.ftpproto.server.dao.UserDAO;
import com.ftpproto.server.db.DatabaseInitializer;
import com.ftpproto.server.ui.ServerDashboardFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

/**
 * Điểm khởi chạy ứng dụng FTP Server và Giao diện Quản trị viên (Admin Dashboard).
 */
public class ServerMain {
    private static final Logger logger = LoggerFactory.getLogger(ServerMain.class);

    public static void main(String[] args) {
        // Cài đặt FlatLaf Look and Feel hiện đại
        try {
            FlatDarkLaf.setup();
        } catch (Exception e) {
            logger.warn("Không thể thiết lập FlatDarkLaf, sử dụng giao diện mặc định", e);
        }

        // Tự động khởi tạo cấu trúc Database (bảng users, activity_logs) & tài khoản mẫu
        logger.info("Đang kiểm tra và khởi tạo cơ sở dữ liệu...");
        DatabaseInitializer.initializeDatabase();

        // Khởi tạo các thành phần Core
        ServerConfig config = new ServerConfig();
        UserDAO userDAO = new UserDAO();
        ActivityLogDAO activityLogDAO = new ActivityLogDAO();
        FTPServer server = new FTPServer(config, userDAO, activityLogDAO);

        // Khởi tạo và hiển thị giao diện trên luồng Swing EDT
        SwingUtilities.invokeLater(() -> {
            ServerDashboardFrame frame = new ServerDashboardFrame(server, userDAO, activityLogDAO);
            frame.setVisible(true);
            logger.info("Server Admin Dashboard đã sẵn sàng!");
        });
    }
}
