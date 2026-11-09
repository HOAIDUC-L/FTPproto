package com.ftpproto.common.util;

import com.ftpproto.common.model.FTPFileItem;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tiện ích định dạng dung lượng, thời gian và phân tích cú pháp FTP LIST.
 */
public final class FormatUtils {
    public static final DateTimeFormatter DISPLAY_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter FTP_LIST_FORMATTER = DateTimeFormatter.ofPattern("MMM dd HH:mm", Locale.ENGLISH);

    // Regex phân tích dòng trả về từ lệnh LIST:
    // ví dụ: "drwxr-xr-x 1 owner group 4096 Nov 09 08:00 myfolder"
    // hoặc: "-rw-r--r-- 1 owner group 1234 Nov 09 08:00 myfile.txt"
    private static final Pattern UNIX_LIST_PATTERN = Pattern.compile(
            "^([bcdlf-][rwxstST-]{9})\\s+(\\d+)\\s+(\\S+)\\s+(\\S+)\\s+(\\d+)\\s+([A-Za-z]{3}\\s+\\d+\\s+[^\\s]+)\\s+(.+)$"
    );

    private FormatUtils() {}

    /**
     * Định dạng dung lượng byte sang B, KB, MB, GB.
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format(Locale.US, "%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    /**
     * Định dạng tốc độ truyền (bytes/giây).
     */
    public static String formatSpeed(long bytesPerSec) {
        return formatFileSize(bytesPerSec) + "/s";
    }

    /**
     * Định dạng dòng hiển thị cho lệnh LIST chuẩn UNIX RFC 959.
     */
    public static String formatUnixListLine(FTPFileItem item) {
        String permissions = item.getPermissions();
        if (permissions == null || permissions.isEmpty()) {
            permissions = item.isDirectory() ? "drwxr-xr-x" : "-rw-r--r--";
        }
        String owner = item.getOwner() != null ? item.getOwner() : "ftp";
        String group = item.getGroup() != null ? item.getGroup() : "ftp";
        long size = item.isDirectory() ? 4096 : item.getSize();
        LocalDateTime mod = item.getLastModified() != null ? item.getLastModified() : LocalDateTime.now();
        String dateStr = mod.format(FTP_LIST_FORMATTER);

        return String.format(Locale.ENGLISH, "%s   1 %-8s %-8s %10d %s %s\r\n",
                permissions, owner, group, size, dateStr, item.getName());
    }

    /**
     * Phân tích một dòng kết quả từ lệnh LIST phía Server thành đối tượng FTPFileItem phía Client.
     */
    public static FTPFileItem parseUnixListLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }
        line = line.trim();
        Matcher matcher = UNIX_LIST_PATTERN.matcher(line);
        if (matcher.matches()) {
            String perms = matcher.group(1);
            boolean isDir = perms.startsWith("d");
            String owner = matcher.group(3);
            String group = matcher.group(4);
            long size = Long.parseLong(matcher.group(5));
            String name = matcher.group(7);

            return new FTPFileItem(name, size, isDir, LocalDateTime.now(), perms, owner, group);
        }

        // Fallback đơn giản nếu dòng trả về không theo chuẩn unix đầy đủ
        boolean isDir = line.startsWith("d");
        String[] parts = line.split("\\s+");
        String name = parts[parts.length - 1];
        long size = 0;
        if (parts.length >= 5) {
            try {
                size = Long.parseLong(parts[4]);
            } catch (NumberFormatException ignored) {}
        }
        return new FTPFileItem(name, size, isDir, LocalDateTime.now());
    }
}
