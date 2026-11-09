package com.ftpproto.server.event;

import java.time.LocalDateTime;

/**
 * Sự kiện ghi nhật ký raw commands và responses trên Server.
 */
public class LogEvent {
    public enum Direction {
        INCOMING, // Command received from client
        OUTGOING, // Response sent to client
        SYSTEM    // Internal server notification
    }

    private final LocalDateTime timestamp;
    private final String clientIp;
    private final Direction direction;
    private final String content;
    private final boolean error;

    public LogEvent(String clientIp, Direction direction, String content) {
        this(clientIp, direction, content, false);
    }

    public LogEvent(String clientIp, Direction direction, String content, boolean error) {
        this.timestamp = LocalDateTime.now();
        this.clientIp = clientIp != null ? clientIp : "SERVER";
        this.direction = direction;
        this.content = content;
        this.error = error;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getClientIp() {
        return clientIp;
    }

    public Direction getDirection() {
        return direction;
    }

    public String getContent() {
        return content;
    }

    public boolean isError() {
        return error;
    }
}
