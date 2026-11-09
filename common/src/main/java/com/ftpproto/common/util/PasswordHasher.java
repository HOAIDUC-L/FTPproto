package com.ftpproto.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Tiện ích mã hóa mật khẩu an toàn sử dụng Salted SHA-256.
 */
public final class PasswordHasher {
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {}

    /**
     * Sinh chuỗi salt ngẫu nhiên 16 bytes dưới dạng Hex string.
     */
    public static String generateSalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return bytesToHex(salt);
    }

    /**
     * Băm mật khẩu kết hợp với salt bằng giải thuật SHA-256.
     *
     * @param password Mật khẩu thô
     * @param salt     Chuỗi salt dạng hex
     * @return Chuỗi băm dạng Hex 64 ký tự
     */
    public static String hash(String password, String salt) {
        if (password == null) {
            password = "";
        }
        if (salt == null) {
            salt = "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hashed = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Thuật toán SHA-256 không khả dụng trong môi trường runtime", e);
        }
    }

    /**
     * Kiểm tra tính hợp lệ của mật khẩu người dùng nhập vào so với mã băm trong DB.
     */
    public static boolean verify(String password, String salt, String expectedHash) {
        if (expectedHash == null || password == null) {
            return false;
        }
        String calculated = hash(password, salt);
        return MessageDigest.isEqual(
                calculated.getBytes(StandardCharsets.UTF_8),
                expectedHash.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
