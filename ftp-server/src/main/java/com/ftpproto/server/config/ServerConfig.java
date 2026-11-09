package com.ftpproto.server.config;

import java.io.File;

/**
 * Cấu hình tham số vận hành của FTP Server.
 */
public class ServerConfig {
    private int port = 2121;
    private String rootDirectory = "data" + File.separator + "server_root";
    private int maxConnections = 50;
    private int idleTimeoutSeconds = 300;
    private String serverName = "FTPproto Server 1.0";

    public ServerConfig() {}

    public ServerConfig(int port, String rootDirectory) {
        this.port = port;
        this.rootDirectory = rootDirectory;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getRootDirectory() {
        return rootDirectory;
    }

    public void setRootDirectory(String rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public int getIdleTimeoutSeconds() {
        return idleTimeoutSeconds;
    }

    public void setIdleTimeoutSeconds(int idleTimeoutSeconds) {
        this.idleTimeoutSeconds = idleTimeoutSeconds;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }
}
