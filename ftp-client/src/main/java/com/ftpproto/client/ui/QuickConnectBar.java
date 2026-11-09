package com.ftpproto.client.ui;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Thanh kết nối nhanh (Quick Connect Bar) chuẩn phong cách FileZilla.
 */
public class QuickConnectBar extends JPanel {
    private JTextField txtHost;
    private JTextField txtUsername;
    private JPasswordField txtPassword;
    private JTextField txtPort;
    private JButton btnConnect;
    private JButton btnDisconnect;

    public interface ConnectAction {
        void onConnect(String host, int port, String username, String password);
    }

    public QuickConnectBar(ConnectAction connectAction, Runnable disconnectAction) {
        setLayout(new FlowLayout(FlowLayout.LEFT, 10, 8));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(200, 200, 200, 60)),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));

        add(new JLabel("Host:"));
        txtHost = new JTextField("127.0.0.1", 12);
        add(txtHost);

        add(new JLabel("Username:"));
        txtUsername = new JTextField("admin", 10);
        add(txtUsername);

        add(new JLabel("Password:"));
        JPanel passPanel = new JPanel(new BorderLayout(2, 0));
        txtPassword = new JPasswordField("admin123", 10);
        txtPassword.setEchoChar('•');
        txtPassword.putClientProperty("JPasswordField.showRevealButton", true);

        JToggleButton btnTogglePass = new JToggleButton("👁");
        btnTogglePass.setToolTipText("Hiện hoặc ẩn mật khẩu");
        btnTogglePass.setMargin(new Insets(2, 4, 2, 4));
        btnTogglePass.setFocusPainted(false);
        btnTogglePass.addActionListener(e -> {
            if (btnTogglePass.isSelected()) {
                txtPassword.setEchoChar((char) 0);
            } else {
                txtPassword.setEchoChar('•');
            }
        });
        passPanel.add(txtPassword, BorderLayout.CENTER);
        passPanel.add(btnTogglePass, BorderLayout.EAST);
        add(passPanel);

        add(new JLabel("Port:"));
        txtPort = new JTextField("2121", 5);
        add(txtPort);

        btnConnect = new JButton("Quickconnect");
        btnConnect.setBackground(new Color(0, 120, 215));
        btnConnect.setForeground(Color.WHITE);
        btnConnect.setFocusPainted(false);
        btnConnect.addActionListener(e -> {
            try {
                String host = txtHost.getText().trim();
                String username = txtUsername.getText().trim();
                String password = new String(txtPassword.getPassword()).trim();
                int port = Integer.parseInt(txtPort.getText().trim());

                if (host.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Vui lòng nhập Host IP!", "Lỗi", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                connectAction.onConnect(host, port, username, password);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Cổng (Port) phải là một số hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        });
        add(btnConnect);

        btnDisconnect = new JButton("Disconnect");
        btnDisconnect.setForeground(new Color(220, 53, 69));
        btnDisconnect.setEnabled(false);
        btnDisconnect.addActionListener(e -> disconnectAction.run());
        add(btnDisconnect);
    }

    public void setConnectedState(boolean connected) {
        SwingUtilities.invokeLater(() -> {
            btnConnect.setEnabled(!connected);
            btnDisconnect.setEnabled(connected);
            txtHost.setEnabled(!connected);
            txtUsername.setEnabled(!connected);
            txtPassword.setEnabled(!connected);
            txtPort.setEnabled(!connected);
        });
    }

    public String getHost() { return txtHost.getText().trim(); }
    public String getUsername() { return txtUsername.getText().trim(); }
}
