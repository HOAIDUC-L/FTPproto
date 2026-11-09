package com.ftpproto.client.event;

/**
 * Lắng nghe các bản tin trao đổi giữa Client và FTP Server để hiển thị lên Status Log UI.
 */
@FunctionalInterface
public interface ClientLogListener {
    /**
     * @param type    "COMMAND", "RESPONSE", "ERROR", "STATUS"
     * @param message Nội dung bản tin
     */
    void onLog(String type, String message);
}
