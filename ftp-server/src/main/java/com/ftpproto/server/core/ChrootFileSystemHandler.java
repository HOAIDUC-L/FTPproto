package com.ftpproto.server.core;

import com.ftpproto.common.model.FTPFileItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Quản lý hệ thống tập tin với cơ chế Chroot Jail.
 * Bảo vệ máy chủ chống tấn công Path Traversal (Directory Traversal).
 */
public class ChrootFileSystemHandler {
    private static final Logger logger = LoggerFactory.getLogger(ChrootFileSystemHandler.class);

    private final Path rootPath;
    private Path currentPath;

    public ChrootFileSystemHandler(String homeDirectory) {
        File home = new File(homeDirectory);
        if (!home.exists()) {
            home.mkdirs();
        }
        try {
            this.rootPath = home.toPath().toRealPath();
        } catch (IOException e) {
            throw new IllegalArgumentException("Không thể chuẩn hóa đường dẫn gốc: " + homeDirectory, e);
        }
        this.currentPath = this.rootPath;
    }

    /**
     * Lấy đường dẫn ảo hiện tại từ góc nhìn của Client (ví dụ: "/", "/docs", "/images/pic.png").
     */
    public synchronized String getVirtualPath() {
        if (currentPath.equals(rootPath)) {
            return "/";
        }
        Path relative = rootPath.relativize(currentPath);
        return "/" + relative.toString().replace(File.separatorChar, '/');
    }

    /**
     * Chuyển đổi đường dẫn ảo sang đường dẫn vật lý thực tế trên ổ đĩa có kiểm tra an toàn.
     */
    public synchronized File resolvePhysical(String pathArg) {
        if (pathArg == null || pathArg.trim().isEmpty()) {
            return currentPath.toFile();
        }
        String p = pathArg.trim();
        Path target;
        if (p.startsWith("/")) {
            // Đường dẫn tuyệt đối từ root ảo
            target = rootPath.resolve(p.substring(1)).normalize();
        } else {
            // Đường dẫn tương đối từ thư mục hiện tại
            target = currentPath.resolve(p).normalize();
        }

        // Kiểm tra an toàn: target phải bắt đầu bằng rootPath
        if (!target.startsWith(rootPath)) {
            logger.warn("Cảnh báo phát hiện tấn công Path Traversal! Root: {}, Mục tiêu: {}", rootPath, target);
            return null;
        }

        return target.toFile();
    }

    /**
     * Lệnh CWD: Thay đổi thư mục làm việc hiện tại.
     */
    public synchronized boolean changeDirectory(String dirArg) {
        File targetFile = resolvePhysical(dirArg);
        if (targetFile != null && targetFile.exists() && targetFile.isDirectory()) {
            try {
                Path realTarget = targetFile.toPath().toRealPath();
                if (realTarget.startsWith(rootPath)) {
                    this.currentPath = realTarget;
                    return true;
                }
            } catch (IOException e) {
                logger.warn("Lỗi khi chuẩn hóa đường dẫn đích: {}", targetFile, e);
            }
        }
        return false;
    }

    /**
     * Lệnh CDUP: Chuyển lên thư mục cha.
     */
    public synchronized boolean changeToParent() {
        if (currentPath.equals(rootPath)) {
            return true; // Đang ở root thì vẫn ở root
        }
        Path parent = currentPath.getParent();
        if (parent != null && parent.startsWith(rootPath)) {
            this.currentPath = parent;
            return true;
        }
        this.currentPath = rootPath;
        return true;
    }

    /**
     * Lấy danh sách file và thư mục con trong thư mục chỉ định (hoặc hiện tại).
     */
    public synchronized List<FTPFileItem> listFiles(String pathArg) {
        List<FTPFileItem> list = new ArrayList<>();
        File dir = (pathArg == null || pathArg.trim().isEmpty()) ? currentPath.toFile() : resolvePhysical(pathArg);

        if (dir != null && dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    LocalDateTime ldt = LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(f.lastModified()),
                            ZoneId.systemDefault()
                    );
                    FTPFileItem item = new FTPFileItem(f.getName(), f.length(), f.isDirectory(), ldt);
                    list.add(item);
                }
            }
        }
        return list;
    }

    /**
     * Lệnh MKD: Tạo thư mục mới.
     */
    public synchronized boolean makeDirectory(String dirName) {
        File target = resolvePhysical(dirName);
        if (target != null && !target.exists()) {
            return target.mkdirs();
        }
        return false;
    }

    /**
     * Lệnh RMD: Xóa thư mục rỗng.
     */
    public synchronized boolean removeDirectory(String dirName) {
        File target = resolvePhysical(dirName);
        if (target != null && target.exists() && target.isDirectory()) {
            return target.delete();
        }
        return false;
    }

    /**
     * Lệnh DELE: Xóa file.
     */
    public synchronized boolean deleteFile(String fileName) {
        File target = resolvePhysical(fileName);
        if (target != null && target.exists() && target.isFile()) {
            return target.delete();
        }
        return false;
    }

    /**
     * Lệnh SIZE: Lấy kích thước file tính theo bytes.
     */
    public synchronized long getFileSize(String fileName) {
        File target = resolvePhysical(fileName);
        if (target != null && target.exists() && target.isFile()) {
            return target.length();
        }
        return -1;
    }
}
