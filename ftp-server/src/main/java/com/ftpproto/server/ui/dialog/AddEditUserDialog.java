package com.ftpproto.server.ui.dialog;

import com.ftpproto.common.model.User;
import com.ftpproto.common.util.PasswordHasher;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * Dialog thêm mới hoặc chỉnh sửa tài khoản người dùng FTP.
 */
public class AddEditUserDialog extends JDialog {
    private final User user;
    private boolean saved = false;

    private JTextField txtUsername;
    private JPasswordField txtPassword;
    private JTextField txtHomeDir;
    private JCheckBox chkRead;
    private JCheckBox chkWrite;
    private JCheckBox chkDelete;
    private JCheckBox chkActive;

    public AddEditUserDialog(Frame owner, User userToEdit) {
        super(owner, userToEdit == null ? "Thêm tài khoản FTP mới" : "Chỉnh sửa tài khoản: " + userToEdit.getUsername(), true);
        this.user = userToEdit != null ? userToEdit : new User();
        initUI(userToEdit != null);
        pack();
        setLocationRelativeTo(owner);
    }

    private void initUI(boolean isEdit) {
        setLayout(new BorderLayout(10, 10));
        setSize(480, 420);
        setResizable(false);

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 10, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 6, 6, 6);

        // Username
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        formPanel.add(new JLabel("Tên đăng nhập:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        txtUsername = new JTextField(user.getUsername() != null ? user.getUsername() : "", 20);
        if (isEdit) {
            txtUsername.setEditable(false);
        }
        formPanel.add(txtUsername, gbc);

        // Password
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        formPanel.add(new JLabel(isEdit ? "Mật khẩu mới (để trống nếu giữ nguyên):" : "Mật khẩu:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        
        JPanel passPanel = new JPanel(new BorderLayout(5, 0));
        txtPassword = new JPasswordField(16);
        txtPassword.setEchoChar('•');
        txtPassword.putClientProperty("JPasswordField.showRevealButton", true);
        
        JCheckBox chkShowPassword = new JCheckBox("Hiện");
        chkShowPassword.setToolTipText("Hiện hoặc ẩn ký tự mật khẩu");
        chkShowPassword.addActionListener(e -> {
            if (chkShowPassword.isSelected()) {
                txtPassword.setEchoChar((char) 0);
            } else {
                txtPassword.setEchoChar('•');
            }
        });
        passPanel.add(txtPassword, BorderLayout.CENTER);
        passPanel.add(chkShowPassword, BorderLayout.EAST);
        formPanel.add(passPanel, gbc);

        // Home Directory
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        formPanel.add(new JLabel("Thư mục gốc (Home Dir):"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        JPanel dirPanel = new JPanel(new BorderLayout(5, 0));
        String defaultDir = user.getHomeDirectory() != null ? user.getHomeDirectory() : "data/server_root/" + (isEdit ? user.getUsername() : "");
        txtHomeDir = new JTextField(defaultDir, 16);
        JButton btnBrowse = new JButton("Duyệt...");
        btnBrowse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                txtHomeDir.setText(chooser.getSelectedFile().getPath());
            }
        });
        dirPanel.add(txtHomeDir, BorderLayout.CENTER);
        dirPanel.add(btnBrowse, BorderLayout.EAST);
        formPanel.add(dirPanel, gbc);

        // Permissions
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        formPanel.add(new JLabel("Quyền hạn:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        JPanel permPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        chkRead = new JCheckBox("Đọc (Download/List)", user.isCanRead());
        chkWrite = new JCheckBox("Ghi (Upload/Tạo thư mục)", user.isCanWrite());
        chkDelete = new JCheckBox("Xóa (Xóa file/thư mục)", user.isCanDelete());
        permPanel.add(chkRead);
        permPanel.add(chkWrite);
        permPanel.add(chkDelete);
        formPanel.add(permPanel, gbc);

        // Active Status
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0;
        formPanel.add(new JLabel("Trạng thái:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        chkActive = new JCheckBox("Kích hoạt tài khoản", user.isActive());
        formPanel.add(chkActive, gbc);

        add(formPanel, BorderLayout.CENTER);

        // Bottom buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton btnCancel = new JButton("Hủy");
        btnCancel.addActionListener(e -> dispose());
        JButton btnSave = new JButton("Lưu thay đổi");
        btnSave.putClientProperty("JButton.buttonType", "roundRect");
        btnSave.addActionListener(e -> onSave(isEdit));

        buttonPanel.add(btnCancel);
        buttonPanel.add(btnSave);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void onSave(boolean isEdit) {
        String username = txtUsername.getText().trim();
        String password = new String(txtPassword.getPassword()).trim();
        String homeDir = txtHomeDir.getText().trim();

        if (username.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập tên đăng nhập!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (!isEdit && password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập mật khẩu cho tài khoản mới!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (homeDir.isEmpty()) {
            homeDir = "data" + File.separator + "server_root" + File.separator + username;
        }

        user.setUsername(username);
        user.setHomeDirectory(homeDir);
        user.setCanRead(chkRead.isSelected());
        user.setCanWrite(chkWrite.isSelected());
        user.setCanDelete(chkDelete.isSelected());
        user.setActive(chkActive.isSelected());

        if (!password.isEmpty()) {
            String salt = PasswordHasher.generateSalt();
            String hash = PasswordHasher.hash(password, salt);
            user.setSalt(salt);
            user.setPasswordHash(hash);
        }

        // Tạo thư mục trên đĩa nếu chưa có
        File dir = new File(homeDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        saved = true;
        dispose();
    }

    public boolean isSaved() {
        return saved;
    }

    public User getUser() {
        return user;
    }
}
