package com.ftpproto.server.ui;

import com.ftpproto.server.core.FTPServer;
import com.ftpproto.server.db.ConnectionPool;
import com.ftpproto.server.event.ConnectionEvent;
import com.ftpproto.server.event.ServerEventBus;
import com.ftpproto.server.event.ServerEventListener;
import com.ftpproto.server.event.TransferEvent;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tab 1: Dashboard & Điều khiển máy chủ FTP.
 */
public class DashboardPanel extends JPanel implements ServerEventListener {
    private final FTPServer server;

    private JLabel lblStatusBadge;
    private JLabel lblServerIp;
    private JSpinner spinPort;
    private JTextField txtRootDir;
    private JButton btnBrowseRoot;
    private JButton btnStart;
    private JButton btnStop;

    private JLabel lblActiveConnectionsCount;
    private JLabel lblTotalUploads;
    private JLabel lblTotalDownloads;
    private JLabel lblDbStatus;

    private final AtomicInteger uploadCount = new AtomicInteger(0);
    private final AtomicInteger downloadCount = new AtomicInteger(0);

    public DashboardPanel(FTPServer server) {
        this.server = server;
        setLayout(new BorderLayout(15, 15));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        initUI();
        ServerEventBus.getInstance().register(this);
        updateState(server.isRunning());
    }

    private void initUI() {
        // TOP: Thẻ trạng thái & Thống kê nhanh
        JPanel statsPanel = new JPanel(new GridLayout(1, 4, 15, 0));

        statsPanel.add(createStatCard("Trạng thái Máy chủ", lblStatusBadge = new JLabel("STOPPED", SwingConstants.CENTER)));
        statsPanel.add(createStatCard("Kết nối Đang mở", lblActiveConnectionsCount = new JLabel("0", SwingConstants.CENTER)));
        statsPanel.add(createStatCard("Tổng Tải lên (STOR)", lblTotalUploads = new JLabel("0", SwingConstants.CENTER)));
        statsPanel.add(createStatCard("Tổng Tải về (RETR)", lblTotalDownloads = new JLabel("0", SwingConstants.CENTER)));

        add(statsPanel, BorderLayout.NORTH);

        // CENTER: Cấu hình và Bảng điều khiển
        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Cấu hình Vận hành Server",
                TitledBorder.LEFT, TitledBorder.TOP, new Font("SansSerif", Font.BOLD, 14)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 15, 10, 15);

        // Server IP
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.2;
        centerPanel.add(new JLabel("Địa chỉ IP Máy chủ:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.8;
        String localIp = "127.0.0.1";
        try {
            localIp = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ignored) {}
        lblServerIp = new JLabel(localIp);
        lblServerIp.setFont(new Font("Monospaced", Font.BOLD, 14));
        centerPanel.add(lblServerIp, gbc);

        // Listening Port
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.2;
        centerPanel.add(new JLabel("Cổng lắng nghe (Port):"), gbc);
        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 0.8;
        spinPort = new JSpinner(new SpinnerNumberModel(server.getConfig().getPort(), 1, 65535, 1));
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinPort, "#");
        spinPort.setEditor(editor);
        centerPanel.add(spinPort, gbc);

        // Root Directory
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.2;
        centerPanel.add(new JLabel("Thư mục gốc (Root Directory):"), gbc);
        gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 0.8;
        JPanel rootDirPanel = new JPanel(new BorderLayout(8, 0));
        txtRootDir = new JTextField(server.getConfig().getRootDirectory());
        btnBrowseRoot = new JButton("Duyệt...");
        btnBrowseRoot.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(txtRootDir.getText());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                txtRootDir.setText(chooser.getSelectedFile().getPath());
            }
        });
        rootDirPanel.add(txtRootDir, BorderLayout.CENTER);
        rootDirPanel.add(btnBrowseRoot, BorderLayout.EAST);
        centerPanel.add(rootDirPanel, gbc);

        // Database status
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.2;
        centerPanel.add(new JLabel("Cơ sở dữ liệu (PostgreSQL):"), gbc);
        gbc.gridx = 1; gbc.gridy = 3; gbc.weightx = 0.8;
        boolean dbOk = ConnectionPool.getInstance().isAvailable();
        lblDbStatus = new JLabel(dbOk ? "Đã kết nối (HikariCP Connected)" : "Chưa kết nối (Disconnected)");
        lblDbStatus.setForeground(dbOk ? new Color(46, 139, 87) : Color.RED);
        centerPanel.add(lblDbStatus, gbc);

        add(centerPanel, BorderLayout.CENTER);

        // SOUTH: Action Buttons (Start / Stop)
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 25, 15));

        btnStart = new JButton(" Khởi động Server ");
        btnStart.setFont(new Font("SansSerif", Font.BOLD, 14));
        btnStart.setBackground(new Color(40, 167, 69));
        btnStart.setForeground(Color.WHITE);
        btnStart.setFocusPainted(false);
        btnStart.addActionListener(e -> startServer());

        btnStop = new JButton(" Dừng Server ");
        btnStop.setFont(new Font("SansSerif", Font.BOLD, 14));
        btnStop.setBackground(new Color(220, 53, 69));
        btnStop.setForeground(Color.WHITE);
        btnStop.setFocusPainted(false);
        btnStop.setEnabled(false);
        btnStop.addActionListener(e -> stopServer());

        buttonPanel.add(btnStart);
        buttonPanel.add(btnStop);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private JPanel createStatCard(String title, JLabel valueLabel) {
        JPanel card = new JPanel(new BorderLayout(5, 5));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200, 60), 1),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)
        ));
        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        titleLabel.setForeground(Color.GRAY);

        valueLabel.setFont(new Font("SansSerif", Font.BOLD, 22));

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private void startServer() {
        int port = (int) spinPort.getValue();
        String rootDir = txtRootDir.getText().trim();
        server.getConfig().setPort(port);
        server.getConfig().setRootDirectory(rootDir);

        btnStart.setEnabled(false);
        SwingUtilities.invokeLater(() -> {
            try {
                server.start();
                updateState(true);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Không thể khởi động Server: " + ex.getMessage(),
                        "Lỗi Khởi Động", JOptionPane.ERROR_MESSAGE);
                btnStart.setEnabled(true);
            }
        });
    }

    private void stopServer() {
        btnStop.setEnabled(false);
        SwingUtilities.invokeLater(() -> {
            server.stop();
            updateState(false);
        });
    }

    private void updateState(boolean running) {
        SwingUtilities.invokeLater(() -> {
            if (running) {
                lblStatusBadge.setText("RUNNING");
                lblStatusBadge.setForeground(new Color(40, 167, 69));
                btnStart.setEnabled(false);
                btnStop.setEnabled(true);
                spinPort.setEnabled(false);
                txtRootDir.setEnabled(false);
                btnBrowseRoot.setEnabled(false);
            } else {
                lblStatusBadge.setText("STOPPED");
                lblStatusBadge.setForeground(new Color(220, 53, 69));
                btnStart.setEnabled(true);
                btnStop.setEnabled(false);
                spinPort.setEnabled(true);
                txtRootDir.setEnabled(true);
                btnBrowseRoot.setEnabled(true);
                lblActiveConnectionsCount.setText("0");
            }
        });
    }

    @Override
    public void onServerStateChanged(boolean running, int port, String rootDir) {
        updateState(running);
    }

    @Override
    public void onConnectionEvent(ConnectionEvent event) {
        SwingUtilities.invokeLater(() -> {
            lblActiveConnectionsCount.setText(String.valueOf(server.getActiveClients().size()));
        });
    }

    @Override
    public void onTransferEvent(TransferEvent event) {
        if (event.getType() == TransferEvent.Type.COMPLETED) {
            SwingUtilities.invokeLater(() -> {
                if ("UPLOAD".equalsIgnoreCase(event.getDirection())) {
                    lblTotalUploads.setText(String.valueOf(uploadCount.incrementAndGet()));
                } else if ("DOWNLOAD".equalsIgnoreCase(event.getDirection())) {
                    lblTotalDownloads.setText(String.valueOf(downloadCount.incrementAndGet()));
                }
            });
        }
    }
}
