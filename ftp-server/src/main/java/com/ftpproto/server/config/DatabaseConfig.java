package com.ftpproto.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * Đọc và quản lý các thiết lập kết nối cơ sở dữ liệu từ file properties.
 */
public class DatabaseConfig {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);

    private String url;
    private String username;
    private String password;
    private String driverClassName;
    private int maximumPoolSize = 10;
    private int minimumIdle = 2;
    private long connectionTimeout = 10000;

    public DatabaseConfig() {
        loadProperties();
    }

    private void loadProperties() {
        Properties props = new Properties();
        // Thử tìm file db.properties ở ngoài thư mục chạy trước, nếu không có thì đọc từ classpath
        File externalFile = new File("db.properties");
        if (externalFile.exists()) {
            try (InputStream in = new FileInputStream(externalFile)) {
                props.load(in);
                logger.info("Đã tải cấu hình database từ file ngoài: {}", externalFile.getAbsolutePath());
            } catch (Exception e) {
                logger.warn("Không thể đọc db.properties ngoài, chuyển sang classpath", e);
            }
        }

        if (props.isEmpty()) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("db.properties")) {
                if (in != null) {
                    props.load(in);
                    logger.info("Đã tải cấu hình database từ classpath: db.properties");
                }
            } catch (Exception e) {
                logger.error("Lỗi khi đọc db.properties từ classpath", e);
            }
        }

        this.url = props.getProperty("db.url", "jdbc:postgresql://localhost:5432/smartshop_db");
        this.username = props.getProperty("db.username", "smartshop");
        this.password = props.getProperty("db.password", "smartshop_password");
        this.driverClassName = props.getProperty("db.driverClassName", "org.postgresql.Driver");

        try {
            this.maximumPoolSize = Integer.parseInt(props.getProperty("db.maximumPoolSize", "10"));
            this.minimumIdle = Integer.parseInt(props.getProperty("db.minimumIdle", "2"));
            this.connectionTimeout = Long.parseLong(props.getProperty("db.connectionTimeout", "10000"));
        } catch (NumberFormatException ignored) {}
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    public int getMinimumIdle() {
        return minimumIdle;
    }

    public long getConnectionTimeout() {
        return connectionTimeout;
    }
}
