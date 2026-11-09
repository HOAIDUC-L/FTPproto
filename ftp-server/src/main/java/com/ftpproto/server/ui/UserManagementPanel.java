package com.ftpproto.server.ui;

import com.ftpproto.common.model.User;
import com.ftpproto.common.util.FormatUtils;
import com.ftpproto.server.dao.UserDAO;
import com.ftpproto.server.ui.dialog.AddEditUserDialog;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Tab 2: Quản lý tài khoản người dùng FTP từ Database.
 */
public class UserManagementPanel extends JPanel {
    private final UserDAO userDAO;
    private final Frame parentFrame;

    private JTable tableUsers;
    private DefaultTableModel tableModel;
    private List<User> userList;

    public UserManagementPanel(Frame parentFrame, UserDAO userDAO) {
        this.parentFrame = parentFrame;
        this.userDAO = userDAO;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        initUI();
        loadUsers();
    }

    private void initUI() {
        // Toolbar nút bấm phía trên
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        JButton btnAdd = new JButton("Thêm tài khoản");
        JButton btnEdit = new JButton("Chỉnh sửa");
        JButton btnToggleActive = new JButton("Khóa / Kích hoạt");
        JButton btnDelete = new JButton("Xóa tài khoản");
        JButton btnRefresh = new JButton("Nạp lại");

        btnAdd.addActionListener(e -> onAddUser());
        btnEdit.addActionListener(e -> onEditUser());
        btnToggleActive.addActionListener(e -> onToggleActive());
        btnDelete.addActionListener(e -> onDeleteUser());
        btnRefresh.addActionListener(e -> loadUsers());

        toolBar.add(btnAdd);
        toolBar.addSeparator();
        toolBar.add(btnEdit);
        toolBar.add(btnToggleActive);
        toolBar.add(btnDelete);
        toolBar.addSeparator();
        toolBar.add(btnRefresh);

        add(toolBar, BorderLayout.NORTH);

        // Bảng JTable hiển thị danh sách User
        String[] columns = {"ID", "Tên đăng nhập", "Thư mục gốc (Home)", "Quyền hạn", "Trạng thái", "Ngày tạo"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tableUsers = new JTable(tableModel);
        tableUsers.setRowHeight(26);
        tableUsers.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tableUsers.getTableHeader().setReorderingAllowed(false);

        // Căn giữa cột ID, Quyền hạn, Trạng thái
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        tableUsers.getColumnModel().getColumn(0).setPreferredWidth(50);
        tableUsers.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        tableUsers.getColumnModel().getColumn(1).setPreferredWidth(120);
        tableUsers.getColumnModel().getColumn(2).setPreferredWidth(220);
        tableUsers.getColumnModel().getColumn(3).setPreferredWidth(100);
        tableUsers.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);
        tableUsers.getColumnModel().getColumn(4).setPreferredWidth(100);
        tableUsers.getColumnModel().getColumn(4).setCellRenderer(centerRenderer);
        tableUsers.getColumnModel().getColumn(5).setPreferredWidth(150);

        tableUsers.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    onEditUser();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(tableUsers);
        add(scrollPane, BorderLayout.CENTER);
    }

    public void loadUsers() {
        tableModel.setRowCount(0);
        SwingWorker<List<User>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<User> doInBackground() {
                return userDAO.findAll();
            }

            @Override
            protected void done() {
                try {
                    userList = get();
                    for (User u : userList) {
                        StringBuilder perms = new StringBuilder();
                        if (u.isCanRead()) perms.append("R ");
                        if (u.isCanWrite()) perms.append("W ");
                        if (u.isCanDelete()) perms.append("D");

                        tableModel.addRow(new Object[]{
                                u.getId(),
                                u.getUsername(),
                                u.getHomeDirectory(),
                                perms.toString().trim(),
                                u.isActive() ? "Hoạt động" : "Bị khóa",
                                u.getCreatedAt() != null ? u.getCreatedAt().format(FormatUtils.DISPLAY_DATETIME_FORMATTER) : ""
                        });
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(UserManagementPanel.this,
                            "Lỗi khi tải danh sách người dùng từ DB: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void onAddUser() {
        AddEditUserDialog dialog = new AddEditUserDialog(parentFrame, null);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            User newUser = dialog.getUser();
            boolean ok = userDAO.create(newUser);
            if (ok) {
                loadUsers();
                JOptionPane.showMessageDialog(this, "Đã thêm tài khoản: " + newUser.getUsername(), "Thành công", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Không thể tạo tài khoản (có thể username đã tồn tại)!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void onEditUser() {
        int selectedRow = tableUsers.getSelectedRow();
        if (selectedRow == -1 || userList == null || selectedRow >= userList.size()) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một tài khoản để chỉnh sửa!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        User selectedUser = userList.get(selectedRow);
        AddEditUserDialog dialog = new AddEditUserDialog(parentFrame, selectedUser);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            boolean ok = userDAO.update(selectedUser);
            if (ok) {
                loadUsers();
                JOptionPane.showMessageDialog(this, "Đã cập nhật thông tin tài khoản!", "Thành công", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Lỗi khi cập nhật tài khoản vào cơ sở dữ liệu!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void onToggleActive() {
        int selectedRow = tableUsers.getSelectedRow();
        if (selectedRow == -1 || userList == null || selectedRow >= userList.size()) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một tài khoản!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        User selectedUser = userList.get(selectedRow);
        boolean newStatus = !selectedUser.isActive();
        boolean ok = userDAO.setActive(selectedUser.getId(), newStatus);
        if (ok) {
            loadUsers();
            String msg = newStatus ? "Đã kích hoạt tài khoản " + selectedUser.getUsername() : "Đã khóa tài khoản " + selectedUser.getUsername();
            JOptionPane.showMessageDialog(this, msg, "Thông báo", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void onDeleteUser() {
        int selectedRow = tableUsers.getSelectedRow();
        if (selectedRow == -1 || userList == null || selectedRow >= userList.size()) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một tài khoản để xóa!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        User selectedUser = userList.get(selectedRow);
        if ("admin".equalsIgnoreCase(selectedUser.getUsername())) {
            JOptionPane.showMessageDialog(this, "Không được xóa tài khoản quản trị viên tối cao (admin)!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int choice = JOptionPane.showConfirmDialog(this,
                "Bạn có chắc chắn muốn xóa tài khoản '" + selectedUser.getUsername() + "' khỏi hệ thống?",
                "Xác nhận xóa", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (choice == JOptionPane.YES_OPTION) {
            boolean ok = userDAO.delete(selectedUser.getId());
            if (ok) {
                loadUsers();
                JOptionPane.showMessageDialog(this, "Đã xóa tài khoản thành công!", "Thành công", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Không thể xóa tài khoản!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
