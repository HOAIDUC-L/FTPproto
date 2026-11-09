package com.ftpproto.common.util;

import com.ftpproto.common.model.FTPFileItem;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class CommonUtilsTest {

    @Test
    public void testPasswordHasher() {
        String salt = PasswordHasher.generateSalt();
        assertNotNull(salt);
        assertEquals(32, salt.length()); // 16 bytes = 32 hex chars

        String password = "mySecretPassword123!";
        String hash = PasswordHasher.hash(password, salt);
        assertNotNull(hash);
        assertEquals(64, hash.length()); // SHA-256 = 64 hex chars

        assertTrue(PasswordHasher.verify(password, salt, hash));
        assertFalse(PasswordHasher.verify("wrongPassword", salt, hash));
    }

    @Test
    public void testFormatUtilsFileSize() {
        assertEquals("500 B", FormatUtils.formatFileSize(500));
        assertEquals("1.0 KB", FormatUtils.formatFileSize(1024));
        assertEquals("1.5 MB", FormatUtils.formatFileSize((long) (1.5 * 1024 * 1024)));
    }

    @Test
    public void testParseUnixListLine() {
        FTPFileItem item = new FTPFileItem("test.txt", 1024, false, LocalDateTime.of(2026, 11, 9, 8, 30));
        String formatted = FormatUtils.formatUnixListLine(item);
        assertNotNull(formatted);
        assertTrue(formatted.contains("test.txt"));

        FTPFileItem parsed = FormatUtils.parseUnixListLine(formatted.trim());
        assertNotNull(parsed);
        assertEquals("test.txt", parsed.getName());
        assertEquals(1024, parsed.getSize());
        assertFalse(parsed.isDirectory());
    }
}
