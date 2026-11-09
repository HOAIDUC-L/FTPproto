package com.ftpproto.client.ui;

import com.ftpproto.client.core.FTPClientEngine;
import com.ftpproto.common.model.FTPFileItem;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

/**
 * Cửa sổ chính của ứng dụng FTP Client giao diện chuẩn FileZilla.
 */
public class ClientMainFrame extends JFrame {
    private final FTPClientEngine clientEngine;

    private QuickConnectBar quickConnectBar;
    private StatusLogPanel statusLogPanel;
    private LocalFilePanel localFilePanel;
    private RemoteFilePanel remoteFilePanel;
    private TransferProgressPanel transferProgressPanel;

    public ClientMainFrame() {
        this.clientEngine = new FTPClientEngine();

        setTitle("FTPproto Client - Trình truyền tệp FTP (FileZilla Style)");
        setSize(1100, 750);
        setMinimumSize(new Dimension(850, 600));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        initUI();

        // Đăng ký nhận log giao tiếp từ clientEngine
        clientEngine.addLogListener(statusLogPanel);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                clientEngine.disconnect();
            }
        });
    }

    private void initUI() {
        setLayout(new BorderLayout());

        // 1. Thanh kết nối nhanh ở trên cùng
        quickConnectBar = new QuickConnectBar(this::performConnect, this::performDisconnect);
        add(quickConnectBar, BorderLayout.NORTH);

        // 2. Khung log trạng thái
        statusLogPanel = new StatusLogPanel();

        // 3. Khung quản lý file 2 cột song song (Dual-Pane)
        localFilePanel = new LocalFilePanel(this::performUpload);
        remoteFilePanel = new RemoteFilePanel(clientEngine, this::performDownload);

        JSplitPane filesSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, localFilePanel, remoteFilePanel);
        filesSplitPane.setResizeWeight(0.5);
        filesSplitPane.setContinuousLayout(true);

        // Split dọc: Trên là Log, Dưới là 2 cột File
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, statusLogPanel, filesSplitPane);
        mainSplitPane.setDividerLocation(130);
        mainSplitPane.setContinuousLayout(true);

        add(mainSplitPane, BorderLayout.CENTER);

        // 4. Thanh tiến trình truyền tải ở dưới cùng
        transferProgressPanel = new TransferProgressPanel();
        add(transferProgressPanel, BorderLayout.SOUTH);
    }

    private void performConnect(String host, int port, String username, String password) {
        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                boolean connected = clientEngine.connect(host, port);
                if (connected) {
                    return clientEngine.login(username, password);
                }
                return false;
            }

            @Override
            protected void done() {
                try {
                    boolean success = get();
                    if (success) {
                        quickConnectBar.setConnectedState(true);
                        remoteFilePanel.setConnected(true);
                        remoteFilePanel.refreshRemoteFiles();
                    } else {
                        quickConnectBar.setConnectedState(false);
                        remoteFilePanel.setConnected(false);
                        JOptionPane.showMessageDialog(ClientMainFrame.this,
                                "Đăng nhập thất bại! Vui lòng kiểm tra lại Username/Password.", "Lỗi Xác Thực", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception e) {
                    quickConnectBar.setConnectedState(false);
                    remoteFilePanel.setConnected(false);
                    JOptionPane.showMessageDialog(ClientMainFrame.this,
                            "Không thể kết nối tới FTP Server: " + e.getMessage(), "Lỗi Kết Nối", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void performDisconnect() {
        clientEngine.disconnect();
        quickConnectBar.setConnectedState(false);
        remoteFilePanel.setConnected(false);
    }

    private void performUpload(File localFile) {
        if (!clientEngine.isConnected() || !clientEngine.isAuthenticated()) {
            JOptionPane.showMessageDialog(this, "Chưa kết nối tới FTP Server!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                clientEngine.uploadFile(localFile, localFile.getName(), transferProgressPanel);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    remoteFilePanel.refreshRemoteFiles();
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(ClientMainFrame.this,
                            "Lỗi khi tải file lên: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void performDownload(FTPFileItem remoteItem) {
        if (!clientEngine.isConnected() || !clientEngine.isAuthenticated()) {
            JOptionPane.showMessageDialog(this, "Chưa kết nối tới FTP Server!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        File targetFile = new File(localFilePanel.getCurrentDirectory(), remoteItem.getName());
        if (targetFile.exists()) {
            int ch = JOptionPane.showConfirmDialog(this,
                    "File '" + remoteItem.getName() + "' đã tồn tại ở thư mục cục bộ. Bạn có muốn ghi đè?",
                    "Xác nhận ghi đè", JOptionPane.YES_NO_OPTION);
            if (ch != JOptionPane.YES_OPTION) return;
        }

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                clientEngine.downloadFile(remoteItem.getName(), targetFile, transferProgressPanel);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    localFilePanel.loadFiles(localFilePanel.getCurrentDirectory());
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(ClientMainFrame.this,
                            "Lỗi khi tải file về: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }
}
