package com.ftpproto.server.event;

import java.time.LocalDateTime;

/**
 * Sự kiện kết nối/ngắt kết nối của client.
 */
public class ConnectionEvent {
    public enum Type {
        CONNECTED,
        AUTHENTICATED,
        DISCONNECTED,
        KICKED
    }

    private final String sessionId;
    private final String clientIp;
    private final int clientPort;
    private final String username;
    private final LocalDateTime loginTime;
    private final String currentDirectory;
    private final String activityStatus;
    private final Type type;

    public ConnectionEvent(Type type, String sessionId, String clientIp, int clientPort,
                           String username, LocalDateTime loginTime, String currentDirectory, String activityStatus) {
        this.type = type;
        this.sessionId = sessionId;
        this.clientIp = clientIp;
        this.clientPort = clientPort;
        this.username = username;
        this.loginTime = loginTime;
        this.currentDirectory = currentDirectory;
        this.activityStatus = activityStatus;
    }

    public Type getType() {
        return type;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getClientIp() {
        return clientIp;
    }

    public int getClientPort() {
        return clientPort;
    }

    public String getUsername() {
        return username;
    }

    public LocalDateTime getLoginTime() {
        return loginTime;
    }

    public String getCurrentDirectory() {
        return currentDirectory;
    }

    public String getActivityStatus() {
        return activityStatus;
    }
}
