package com.ftpproto.client.ui;

import com.ftpproto.common.util.FormatUtils;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Consumer;

/**
 * Cột trái: Quản lý tập tin và duyệt thư mục phía Local (Local Site).
 */
public class LocalFilePanel extends JPanel {
    private File currentDirectory;
    private JTextField txtPath;
    private JButton btnUp;
    private JTable tableFiles;
    private DefaultTableModel tableModel;

    private Consumer<File> onUploadRequested;

    public LocalFilePanel(Consumer<File> onUploadRequested) {
        this.onUploadRequested = onUploadRequested;
        this.currentDirectory = new File(System.getProperty("user.home"));

        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder("Local Site (Máy cục bộ)"));

        initUI();
        loadFiles(currentDirectory);
    }

    private void initUI() {
        // TOP: Thanh địa chỉ Local
        JPanel topPanel = new JPanel(new BorderLayout(5, 0));
        topPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        btnUp = new JButton(" ⬆ Lên thư mục cha ");
        btnUp.setToolTipText("Lên thư mục cha (..)");
        btnUp.addActionListener(e -> {
            File parent = currentDirectory.getParentFile();
            if (parent != null && parent.exists()) {
                loadFiles(parent);
            }
        });

        txtPath = new JTextField(currentDirectory.getAbsolutePath());
        txtPath.addActionListener(e -> {
            File target = new File(txtPath.getText().trim());
            if (target.exists() && target.isDirectory()) {
                loadFiles(target);
            } else {
                txtPath.setText(currentDirectory.getAbsolutePath());
            }
        });

        topPanel.add(btnUp, BorderLayout.WEST);
        topPanel.add(txtPath, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        // CENTER: Bảng hiển thị tệp
        String[] columns = {"Tên tệp / Thư mục", "Kích thước", "Loại", "Ngày sửa đổi"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tableFiles = new JTable(tableModel);
        tableFiles.setRowHeight(24);
        tableFiles.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tableFiles.getColumnModel().getColumn(0).setPreferredWidth(220);
        tableFiles.getColumnModel().getColumn(1).setPreferredWidth(90);
        tableFiles.getColumnModel().getColumn(2).setPreferredWidth(70);
        tableFiles.getColumnModel().getColumn(3).setPreferredWidth(140);

        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(JLabel.RIGHT);
        tableFiles.getColumnModel().getColumn(1).setCellRenderer(rightRenderer);

        // Xử lý Double Click
        tableFiles.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    onItemDoubleClicked();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) showPopupMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showPopupMenu(e);
            }
        });

        JScrollPane scrollPane = new JScrollPane(tableFiles);
        add(scrollPane, BorderLayout.CENTER);
    }

    public void loadFiles(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;
        this.currentDirectory = dir;
        txtPath.setText(dir.getAbsolutePath());
        tableModel.setRowCount(0);

        File[] files = dir.listFiles();
        if (files != null) {
            // Sắp xếp thư mục lên trước, rồi tới file
            Arrays.sort(files, Comparator.comparing((File f) -> !f.isDirectory())
                    .thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER));

            for (File f : files) {
                String name = (f.isDirectory() ? "📁 " : "📄 ") + f.getName();
                String size = f.isDirectory() ? "" : FormatUtils.formatFileSize(f.length());
                String type = f.isDirectory() ? "Thư mục" : "Tệp tin";
                LocalDateTime mod = LocalDateTime.ofInstant(Instant.ofEpochMilli(f.lastModified()), ZoneId.systemDefault());
                String date = mod.format(FormatUtils.DISPLAY_DATETIME_FORMATTER);

                tableModel.addRow(new Object[]{name, size, type, date});
            }
        }
    }

    private void onItemDoubleClicked() {
        int row = tableFiles.getSelectedRow();
        if (row == -1) return;

        File selected = getSelectedRawFile();
        if (selected != null && selected.isDirectory()) {
            loadFiles(selected);
        }
    }

    private File getSelectedRawFile() {
        int row = tableFiles.getSelectedRow();
        if (row == -1) return null;
        String rawName = (String) tableModel.getValueAt(row, 0);
        String fileName = rawName.substring(rawName.indexOf(' ') + 1);
        return new File(currentDirectory, fileName);
    }

    private void showPopupMenu(MouseEvent e) {
        int row = tableFiles.rowAtPoint(e.getPoint());
        if (row >= 0 && row < tableFiles.getRowCount()) {
            tableFiles.setRowSelectionInterval(row, row);
        }

        File selected = getSelectedRawFile();
        JPopupMenu menu = new JPopupMenu();

        JMenuItem mnuUpload = new JMenuItem("⬆ Tải lên Server (Upload)");
        mnuUpload.setEnabled(selected != null && selected.isFile());
        mnuUpload.addActionListener(ev -> {
            if (selected != null && onUploadRequested != null) {
                onUploadRequested.accept(selected);
            }
        });
        menu.add(mnuUpload);
        menu.addSeparator();

        JMenuItem mnuNewFolder = new JMenuItem("📁 Tạo thư mục mới...");
        mnuNewFolder.addActionListener(ev -> {
            String name = JOptionPane.showInputDialog(this, "Nhập tên thư mục mới:", "Tạo thư mục cục bộ", JOptionPane.PLAIN_MESSAGE);
            if (name != null && !name.trim().isEmpty()) {
                File newDir = new File(currentDirectory, name.trim());
                if (newDir.mkdir()) {
                    loadFiles(currentDirectory);
                } else {
                    JOptionPane.showMessageDialog(this, "Không thể tạo thư mục!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        menu.add(mnuNewFolder);

        JMenuItem mnuDelete = new JMenuItem("🗑 Xóa");
        mnuDelete.setEnabled(selected != null);
        mnuDelete.addActionListener(ev -> {
            if (selected != null) {
                int ch = JOptionPane.showConfirmDialog(this, "Bạn có chắc muốn xóa '" + selected.getName() + "'?", "Xác nhận xóa", JOptionPane.YES_NO_OPTION);
                if (ch == JOptionPane.YES_OPTION) {
                    if (selected.delete()) {
                        loadFiles(currentDirectory);
                    } else {
                        JOptionPane.showMessageDialog(this, "Không thể xóa (có thể thư mục không rỗng)!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });
        menu.add(mnuDelete);

        JMenuItem mnuRefresh = new JMenuItem("🔄 Làm mới");
        mnuRefresh.addActionListener(ev -> loadFiles(currentDirectory));
        menu.add(mnuRefresh);

        menu.show(tableFiles, e.getX(), e.getY());
    }

    public File getCurrentDirectory() {
        return currentDirectory;
    }
}
