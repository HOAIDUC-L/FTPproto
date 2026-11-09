package com.ftpproto.client;

import com.formdev.flatlaf.FlatDarkLaf;
import com.ftpproto.client.ui.ClientMainFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

/**
 * Điểm khởi chạy ứng dụng FTP Client giao diện đồ họa kiểu FileZilla (FlatLaf).
 */
public class ClientMain {
    private static final Logger logger = LoggerFactory.getLogger(ClientMain.class);

    public static void main(String[] args) {
        try {
            FlatDarkLaf.setup();
        } catch (Exception e) {
            logger.warn("Không thể thiết lập FlatDarkLaf cho Client, sử dụng giao diện mặc định", e);
        }

        SwingUtilities.invokeLater(() -> {
            ClientMainFrame frame = new ClientMainFrame();
            frame.setVisible(true);
            logger.info("FTP Client (FileZilla Style) đã khởi động!");
        });
    }
}
