package com.ftpproto.server.event;

/**
 * Sự kiện theo dõi tiến trình truyền file (Upload / Download).
 */
public class TransferEvent {
    public enum Type {
        START,
        PROGRESS,
        COMPLETED,
        FAILED
    }

    private final Type type;
    private final String clientIp;
    private final String username;
    private final String direction; // UPLOAD hoặc DOWNLOAD
    private final String fileName;
    private final long bytesTransferred;
    private final long totalBytes;
    private final String errorMessage;

    public TransferEvent(Type type, String clientIp, String username, String direction,
                         String fileName, long bytesTransferred, long totalBytes, String errorMessage) {
        this.type = type;
        this.clientIp = clientIp;
        this.username = username;
        this.direction = direction;
        this.fileName = fileName;
        this.bytesTransferred = bytesTransferred;
        this.totalBytes = totalBytes;
        this.errorMessage = errorMessage;
    }

    public Type getType() {
        return type;
    }

    public String getClientIp() {
        return clientIp;
    }

    public String getUsername() {
        return username;
    }

    public String getDirection() {
        return direction;
    }

    public String getFileName() {
        return fileName;
    }

    public long getBytesTransferred() {
        return bytesTransferred;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
