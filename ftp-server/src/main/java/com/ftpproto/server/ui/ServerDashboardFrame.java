package com.ftpproto.server.ui;

import com.ftpproto.server.core.FTPServer;
import com.ftpproto.server.dao.ActivityLogDAO;
import com.ftpproto.server.dao.UserDAO;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Giao diện cửa sổ chính Server Admin Dashboard.
 */
public class ServerDashboardFrame extends JFrame {
    private final FTPServer server;
    private final UserDAO userDAO;
    private final ActivityLogDAO activityLogDAO;

    private JTabbedPane tabbedPane;
    private DashboardPanel dashboardPanel;
    private UserManagementPanel userManagementPanel;
    private ActiveConnectionsPanel activeConnectionsPanel;
    private ServerLogsPanel serverLogsPanel;

    public ServerDashboardFrame(FTPServer server, UserDAO userDAO, ActivityLogDAO activityLogDAO) {
        this.server = server;
        this.userDAO = userDAO;
        this.activityLogDAO = activityLogDAO;

        setTitle("FTPproto Server - Admin Dashboard (FlatLaf)");
        setSize(950, 680);
        setMinimumSize(new Dimension(800, 550));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        initUI();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onExit();
            }
        });
    }

    private void initUI() {
        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.putClientProperty("JTabbedPane.showTabSeparators", true);
        tabbedPane.putClientProperty("JTabbedPane.tabHeight", 34);

        dashboardPanel = new DashboardPanel(server);
        userManagementPanel = new UserManagementPanel(this, userDAO);
        activeConnectionsPanel = new ActiveConnectionsPanel(server);
        serverLogsPanel = new ServerLogsPanel();

        tabbedPane.addTab(" Dashboard / Điều khiển ", dashboardPanel);
        tabbedPane.addTab(" Quản lý tài khoản (Users) ", userManagementPanel);
        tabbedPane.addTab(" Giám sát kết nối (Connections) ", activeConnectionsPanel);
        tabbedPane.addTab(" Nhật ký hệ thống (Logs) ", serverLogsPanel);

        // Nạp lại danh sách user khi chuyển sang Tab Quản lý tài khoản
        tabbedPane.addChangeListener(e -> {
            if (tabbedPane.getSelectedComponent() == userManagementPanel) {
                userManagementPanel.loadUsers();
            } else if (tabbedPane.getSelectedComponent() == activeConnectionsPanel) {
                activeConnectionsPanel.refreshTable();
            }
        });

        add(tabbedPane, BorderLayout.CENTER);
    }

    private void onExit() {
        if (server.isRunning()) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Máy chủ FTP vẫn đang chạy. Bạn có muốn dừng máy chủ và thoát?",
                    "Xác nhận thoát", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                server.stop();
                dispose();
                System.exit(0);
            }
        } else {
            dispose();
            System.exit(0);
        }
    }
}
