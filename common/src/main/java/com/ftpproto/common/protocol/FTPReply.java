package com.ftpproto.common.protocol;

/**
 * Đóng gói bản tin phản hồi từ FTP Server tới FTP Client.
 */
public class FTPReply {
    private final int code;
    private final String message;

    public FTPReply(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public boolean isPositivePreliminary() {
        return code >= 100 && code < 200;
    }

    public boolean isPositiveCompletion() {
        return code >= 200 && code < 300;
    }

    public boolean isPositiveIntermediate() {
        return code >= 300 && code < 400;
    }

    public boolean isTransientNegative() {
        return code >= 400 && code < 500;
    }

    public boolean isPermanentNegative() {
        return code >= 500 && code < 600;
    }

    public boolean isSuccess() {
        return isPositivePreliminary() || isPositiveCompletion() || isPositiveIntermediate();
    }

    /**
     * Định dạng theo chuẩn giao thức FTP: "<code-number> <message>\r\n"
     */
    public String toRawResponse() {
        return code + " " + message + "\r\n";
    }

    public static FTPReply parse(String rawLine) {
        if (rawLine == null || rawLine.trim().isEmpty()) {
            return null;
        }
        String line = rawLine.trim();
        if (line.length() < 3) {
            return new FTPReply(500, line);
        }
        try {
            int code = Integer.parseInt(line.substring(0, 3));
            String message = line.length() > 3 ? line.substring(3).trim() : "";
            // Handle "-" in multiline replies like "220-"
            if (message.startsWith("-")) {
                message = message.substring(1).trim();
            }
            return new FTPReply(code, message);
        } catch (NumberFormatException e) {
            return new FTPReply(500, line);
        }
    }

    @Override
    public String toString() {
        return code + " " + message;
    }
}
