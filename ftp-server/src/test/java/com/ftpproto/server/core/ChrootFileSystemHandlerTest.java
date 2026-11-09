package com.ftpproto.server.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

public class ChrootFileSystemHandlerTest {
    private Path tempRootDir;
    private ChrootFileSystemHandler fs;

    @BeforeEach
    public void setUp() throws IOException {
        tempRootDir = Files.createTempDirectory("ftp_test_root_");
        fs = new ChrootFileSystemHandler(tempRootDir.toAbsolutePath().toString());
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (tempRootDir != null && Files.exists(tempRootDir)) {
            Files.walk(tempRootDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    @Test
    public void testInitialPathIsRoot() {
        assertEquals("/", fs.getVirtualPath());
    }

    @Test
    public void testMakeDirectoryAndChangeDirectory() {
        assertTrue(fs.makeDirectory("subfolder"));
        assertTrue(fs.changeDirectory("subfolder"));
        assertEquals("/subfolder", fs.getVirtualPath());

        assertTrue(fs.changeToParent());
        assertEquals("/", fs.getVirtualPath());
    }

    @Test
    public void testPathTraversalAttackPrevention() {
        // Cố gắng nhảy ra ngoài thư mục root bằng "../../etc"
        File escaped = fs.resolvePhysical("../../etc/passwd");
        assertNull(escaped, "Phải ngăn chặn truy cập file ngoài root (Path Traversal)!");

        assertFalse(fs.changeDirectory("../../etc"));
        assertEquals("/", fs.getVirtualPath());
    }
}
