package com.ftpproto.client;

import com.ftpproto.client.core.FTPClientEngine;
import com.ftpproto.client.event.TransferProgressListener;
import com.ftpproto.common.model.FTPFileItem;
import com.ftpproto.common.model.User;
import com.ftpproto.common.util.PasswordHasher;
import com.ftpproto.server.config.ServerConfig;
import com.ftpproto.server.core.FTPServer;
import com.ftpproto.server.dao.ActivityLogDAO;
import com.ftpproto.server.dao.UserDAO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class FTPIntegrationTest {
    private Path tempRootDir;
    private FTPServer server;
    private FTPClientEngine client;
    private int testPort = 21219;

    @BeforeEach
    public void setUp() throws Exception {
        tempRootDir = Files.createTempDirectory("ftp_integ_root_");

        UserDAO mockUserDAO = new UserDAO(null) {
            @Override
            public Optional<User> findByUsername(String username) {
                if ("admin".equals(username)) {
                    String salt = "test_salt_123456";
                    String hash = PasswordHasher.hash("admin123", salt);
                    User user = new User("admin", hash, salt, tempRootDir.toAbsolutePath().toString(), true, true, true, true);
                    return Optional.of(user);
                }
                return Optional.empty();
            }
        };

        ActivityLogDAO mockLogDAO = new ActivityLogDAO(null) {
            @Override
            public void log(com.ftpproto.common.model.ActivityLog log) {
                // No-op for test
            }
        };

        ServerConfig config = new ServerConfig(testPort, tempRootDir.toAbsolutePath().toString());
        server = new FTPServer(config, mockUserDAO, mockLogDAO);
        server.start();

        client = new FTPClientEngine();
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (client != null) {
            client.disconnect();
        }
        if (server != null) {
            server.stop();
        }
        if (tempRootDir != null && Files.exists(tempRootDir)) {
            Files.walk(tempRootDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    @Test
    public void testFullFTPFlowUploadDownloadListDelete() throws Exception {
        // 1. Connect
        assertTrue(client.connect("127.0.0.1", testPort));
        assertTrue(client.isConnected());

        // 2. Login
        assertTrue(client.login("admin", "admin123"));
        assertTrue(client.isAuthenticated());

        // 3. PWD
        assertEquals("/", client.pwd());

        // 4. MKD & CWD
        assertTrue(client.makeDirectory("testdir"));
        assertTrue(client.cwd("testdir"));
        assertEquals("/testdir", client.pwd());

        // 5. STOR (Upload)
        File localUploadFile = File.createTempFile("ftp_upload_", ".txt");
        String content = "Hello FTP Senior Engineer! Test data transfer.\nLine 2.\n";
        Files.writeString(localUploadFile.toPath(), content);

        AtomicBoolean uploadCompleted = new AtomicBoolean(false);
        client.uploadFile(localUploadFile, "uploaded_file.txt", new TransferProgressListener() {
            @Override public void onStart(String action, String fileName, long totalBytes) {}
            @Override public void onProgress(String action, String fileName, long transferredBytes, long totalBytes, long bytesPerSec) {}
            @Override public void onComplete(String action, String fileName, long totalBytes) {
                uploadCompleted.set(true);
            }
            @Override public void onError(String action, String fileName, String error) {}
        });

        assertTrue(uploadCompleted.get(), "Upload phải hoàn thành");

        // 6. LIST
        List<FTPFileItem> items = client.listFiles("");
        assertNotNull(items);
        assertTrue(items.stream().anyMatch(i -> "uploaded_file.txt".equals(i.getName())));

        // 7. RETR (Download)
        File localDownloadFile = File.createTempFile("ftp_download_", ".txt");
        AtomicBoolean downloadCompleted = new AtomicBoolean(false);
        client.downloadFile("uploaded_file.txt", localDownloadFile, new TransferProgressListener() {
            @Override public void onStart(String action, String fileName, long totalBytes) {}
            @Override public void onProgress(String action, String fileName, long transferredBytes, long totalBytes, long bytesPerSec) {}
            @Override public void onComplete(String action, String fileName, long totalBytes) {
                downloadCompleted.set(true);
            }
            @Override public void onError(String action, String fileName, String error) {}
        });

        assertTrue(downloadCompleted.get(), "Download phải hoàn thành");
        String downloadedContent = Files.readString(localDownloadFile.toPath());
        assertEquals(content, downloadedContent, "Dữ liệu tải về phải khớp tuyệt đối với dữ liệu đã tải lên!");

        // 8. DELE (Delete)
        assertTrue(client.deleteFile("uploaded_file.txt"));

        // 9. CDUP
        assertTrue(client.cdup());
        assertEquals("/", client.pwd());

        // Cleanup temp local files
        localUploadFile.delete();
        localDownloadFile.delete();
    }
}
