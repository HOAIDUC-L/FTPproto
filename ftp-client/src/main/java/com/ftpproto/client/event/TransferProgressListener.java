package com.ftpproto.client.event;

/**
 * Lắng nghe tiến trình truyền nhận file giữa Client và FTP Server.
 */
public interface TransferProgressListener {
    void onStart(String action, String fileName, long totalBytes);
    void onProgress(String action, String fileName, long transferredBytes, long totalBytes, long bytesPerSec);
    void onComplete(String action, String fileName, long totalBytes);
    void onError(String action, String fileName, String error);
}
