package com.ftpproto.common.model;

import java.time.LocalDateTime;

/**
 * Đại diện cho thông tin file hoặc thư mục trên Server hoặc Local Client.
 */
public class FTPFileItem {
    private String name;
    private long size;
    private boolean directory;
    private LocalDateTime lastModified;
    private String permissions; // e.g. "drwxr-xr-x" or "-rw-r--r--"
    private String owner;
    private String group;

    public FTPFileItem() {
    }

    public FTPFileItem(String name, long size, boolean directory, LocalDateTime lastModified) {
        this.name = name;
        this.size = size;
        this.directory = directory;
        this.lastModified = lastModified;
        this.permissions = directory ? "drwxr-xr-x" : "-rw-r--r--";
        this.owner = "ftp";
        this.group = "ftp";
    }

    public FTPFileItem(String name, long size, boolean directory, LocalDateTime lastModified,
                       String permissions, String owner, String group) {
        this.name = name;
        this.size = size;
        this.directory = directory;
        this.lastModified = lastModified;
        this.permissions = permissions;
        this.owner = owner;
        this.group = group;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public boolean isDirectory() {
        return directory;
    }

    public void setDirectory(boolean directory) {
        this.directory = directory;
    }

    public LocalDateTime getLastModified() {
        return lastModified;
    }

    public void setLastModified(LocalDateTime lastModified) {
        this.lastModified = lastModified;
    }

    public String getPermissions() {
        return permissions;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    @Override
    public String toString() {
        return name + (directory ? "/" : "") + " (" + size + " bytes)";
    }
}
