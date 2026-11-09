package com.ftpproto.server.ui;

import com.ftpproto.common.util.FormatUtils;
import com.ftpproto.server.core.FTPClientHandler;
import com.ftpproto.server.core.FTPServer;
import com.ftpproto.server.event.ConnectionEvent;
import com.ftpproto.server.event.ServerEventBus;
import com.ftpproto.server.event.ServerEventListener;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Tab 3: Giám sát các phiên kết nối Client đang hoạt động theo thời gian thực.
 */
public class ActiveConnectionsPanel extends JPanel implements ServerEventListener {
    private final FTPServer server;

    private JTable tableConnections;
    private DefaultTableModel tableModel;
    private JButton btnKick;

    public ActiveConnectionsPanel(FTPServer server) {
        this.server = server;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        initUI();
        ServerEventBus.getInstance().register(this);
        refreshTable();
    }

    private void initUI() {
        // Toolbar
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        btnKick = new JButton("Ngắt kết nối (Kick Client)");
        btnKick.setForeground(new Color(220, 53, 69));
        btnKick.setFont(new Font("SansSerif", Font.BOLD, 12));
        JButton btnRefresh = new JButton("Làm mới danh sách");

        btnKick.addActionListener(e -> onKickClient());
        btnRefresh.addActionListener(e -> refreshTable());

        toolBar.add(btnKick);
        toolBar.addSeparator();
        toolBar.add(btnRefresh);

        add(toolBar, BorderLayout.NORTH);

        // Bảng kết nối
        String[] columns = {"Session ID", "Địa chỉ IP", "Cổng (Port)", "Tên tài khoản", "Thời gian kết nối", "Thư mục làm việc", "Trạng thái truyền tải"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tableConnections = new JTable(tableModel);
        tableConnections.setRowHeight(26);
        tableConnections.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        tableConnections.getColumnModel().getColumn(0).setPreferredWidth(80);
        tableConnections.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        tableConnections.getColumnModel().getColumn(1).setPreferredWidth(120);
        tableConnections.getColumnModel().getColumn(2).setPreferredWidth(80);
        tableConnections.getColumnModel().getColumn(2).setCellRenderer(centerRenderer);
        tableConnections.getColumnModel().getColumn(3).setPreferredWidth(110);
        tableConnections.getColumnModel().getColumn(4).setPreferredWidth(150);
        tableConnections.getColumnModel().getColumn(5).setPreferredWidth(150);
        tableConnections.getColumnModel().getColumn(6).setPreferredWidth(150);

        JScrollPane scrollPane = new JScrollPane(tableConnections);
        add(scrollPane, BorderLayout.CENTER);
    }

    public void refreshTable() {
        SwingUtilities.invokeLater(() -> {
            tableModel.setRowCount(0);
            List<FTPClientHandler> clients = server.getActiveClients();
            for (FTPClientHandler client : clients) {
                String dir = client.getFileSystem() != null ? client.getFileSystem().getVirtualPath() : "/";
                tableModel.addRow(new Object[]{
                        client.getSessionId(),
                        client.getClientIp(),
                        client.getClientPort(),
                        client.getUsername(),
                        client.getConnectTime().format(FormatUtils.DISPLAY_DATETIME_FORMATTER),
                        dir,
                        client.getActivityStatus()
                });
            }
        });
    }

    private void onKickClient() {
        int selectedRow = tableConnections.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một client đang kết nối để ngắt!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String sessionId = (String) tableModel.getValueAt(selectedRow, 0);
        String username = (String) tableModel.getValueAt(selectedRow, 3);
        String ip = (String) tableModel.getValueAt(selectedRow, 1);

        int choice = JOptionPane.showConfirmDialog(this,
                "Bạn có chắc muốn cưỡng chế ngắt kết nối client '" + username + "' (" + ip + ")?",
                "Xác nhận Kick Client", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (choice == JOptionPane.YES_OPTION) {
            boolean ok = server.kickClient(sessionId);
            if (ok) {
                refreshTable();
                JOptionPane.showMessageDialog(this, "Đã ngắt kết nối client thành công!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Client có thể đã tự ngắt kết nối trước đó.", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    @Override
    public void onConnectionEvent(ConnectionEvent event) {
        refreshTable();
    }
}
