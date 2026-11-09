package com.ftpproto.common.model;

import java.time.LocalDateTime;

/**
 * Model đại diện cho nhật ký hoạt động FTP trong cơ sở dữ liệu.
 */
public class ActivityLog {
    private Long id;
    private String username;
    private String action; // LOGIN, LOGOUT, UPLOAD, DOWNLOAD, DELETE, MKDIR, RMDIR, FAILED_LOGIN
    private String filePath;
    private long fileSize;
    private String clientIp;
    private String status; // SUCCESS, FAILED
    private String details;
    private LocalDateTime createdAt;

    public ActivityLog() {
        this.createdAt = LocalDateTime.now();
    }

    public ActivityLog(String username, String action, String filePath, long fileSize,
                       String clientIp, String status, String details) {
        this.username = username;
        this.action = action;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.clientIp = clientIp;
        this.status = status;
        this.details = details;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "ActivityLog{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", action='" + action + '\'' +
                ", filePath='" + filePath + '\'' +
                ", fileSize=" + fileSize +
                ", clientIp='" + clientIp + '\'' +
                ", status='" + status + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
