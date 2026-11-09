package com.ftpproto.server.db;

import com.ftpproto.server.config.DatabaseConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Quản lý Connection Pool tới cơ sở dữ liệu qua thư viện HikariCP.
 */
public class ConnectionPool {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionPool.class);
    private static ConnectionPool instance;

    private final HikariDataSource dataSource;
    private final boolean available;

    private ConnectionPool(DatabaseConfig config) {
        HikariDataSource ds = null;
        boolean ok = false;
        try {
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(config.getUrl());
            hikariConfig.setUsername(config.getUsername());
            hikariConfig.setPassword(config.getPassword());
            hikariConfig.setDriverClassName(config.getDriverClassName());
            hikariConfig.setMaximumPoolSize(config.getMaximumPoolSize());
            hikariConfig.setMinimumIdle(config.getMinimumIdle());
            hikariConfig.setConnectionTimeout(config.getConnectionTimeout());
            hikariConfig.setPoolName("FTPproto-HikariPool");

            // Tối ưu hóa hiệu năng kết nối
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            ds = new HikariDataSource(hikariConfig);
            // Thử lấy connection để xác minh
            try (Connection conn = ds.getConnection()) {
                logger.info("Kết nối cơ sở dữ liệu qua HikariCP thành công: {}", config.getUrl());
                ok = true;
            }
        } catch (Exception e) {
            logger.error("Không thể khởi tạo HikariCP DataSource: {}", e.getMessage());
            ok = false;
        }
        this.dataSource = ds;
        this.available = ok;
    }

    public static synchronized ConnectionPool getInstance() {
        if (instance == null) {
            instance = new ConnectionPool(new DatabaseConfig());
        }
        return instance;
    }

    public static synchronized void init(DatabaseConfig config) {
        if (instance != null) {
            instance.shutdown();
        }
        instance = new ConnectionPool(config);
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || !available) {
            throw new SQLException("HikariCP DataSource không sẵn sàng kết nối!");
        }
        return dataSource.getConnection();
    }

    public boolean isAvailable() {
        return available && dataSource != null && !dataSource.isClosed();
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            logger.info("Đang đóng HikariCP DataSource...");
            dataSource.close();
        }
    }
}
