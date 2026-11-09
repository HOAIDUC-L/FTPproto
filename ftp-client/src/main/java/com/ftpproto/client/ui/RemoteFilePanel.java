package com.ftpproto.client.ui;

import com.ftpproto.client.core.FTPClientEngine;
import com.ftpproto.common.model.FTPFileItem;
import com.ftpproto.common.util.FormatUtils;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Cột phải: Quản lý tập tin và duyệt thư mục phía Server (Remote Site).
 */
public class RemoteFilePanel extends JPanel {
    private final FTPClientEngine clientEngine;
    private JTextField txtRemotePath;
    private JButton btnUp;
    private JTable tableFiles;
    private DefaultTableModel tableModel;
    private List<FTPFileItem> currentItems = Collections.emptyList();

    private Consumer<FTPFileItem> onDownloadRequested;

    public RemoteFilePanel(FTPClientEngine clientEngine, Consumer<FTPFileItem> onDownloadRequested) {
        this.clientEngine = clientEngine;
        this.onDownloadRequested = onDownloadRequested;

        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder("Remote Site (Máy chủ FTP)"));

        initUI();
    }

    private void initUI() {
        // TOP: Thanh địa chỉ Remote
        JPanel topPanel = new JPanel(new BorderLayout(5, 0));
        topPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        btnUp = new JButton(" ⬆ Lên thư mục cha ");
        btnUp.setToolTipText("Lên thư mục cha (CDUP)");
        btnUp.setEnabled(false);
        btnUp.addActionListener(e -> {
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    clientEngine.cdup();
                    return null;
                }
                @Override
                protected void done() {
                    refreshRemoteFiles();
                }
            }.execute();
        });

        txtRemotePath = new JTextField("/");
        txtRemotePath.setEnabled(false);
        txtRemotePath.addActionListener(e -> {
            String target = txtRemotePath.getText().trim();
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    clientEngine.cwd(target);
                    return null;
                }
                @Override
                protected void done() {
                    refreshRemoteFiles();
                }
            }.execute();
        });

        topPanel.add(btnUp, BorderLayout.WEST);
        topPanel.add(txtRemotePath, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        // CENTER: Bảng hiển thị tệp phía Server
        String[] columns = {"Tên tệp / Thư mục", "Kích thước", "Loại", "Quyền hạn", "Ngày sửa đổi"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tableFiles = new JTable(tableModel);
        tableFiles.setRowHeight(24);
        tableFiles.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tableFiles.getColumnModel().getColumn(0).setPreferredWidth(200);
        tableFiles.getColumnModel().getColumn(1).setPreferredWidth(90);
        tableFiles.getColumnModel().getColumn(2).setPreferredWidth(70);
        tableFiles.getColumnModel().getColumn(3).setPreferredWidth(90);
        tableFiles.getColumnModel().getColumn(4).setPreferredWidth(130);

        DefaultTableCellRenderer rightRenderer = new DefaultTableCellRenderer();
        rightRenderer.setHorizontalAlignment(JLabel.RIGHT);
        tableFiles.getColumnModel().getColumn(1).setCellRenderer(rightRenderer);

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

    public void setConnected(boolean connected) {
        btnUp.setEnabled(connected);
        txtRemotePath.setEnabled(connected);
        if (!connected) {
            tableModel.setRowCount(0);
            txtRemotePath.setText("/");
            currentItems = Collections.emptyList();
        }
    }

    public void refreshRemoteFiles() {
        if (!clientEngine.isConnected() || !clientEngine.isAuthenticated()) {
            return;
        }

        SwingWorker<List<FTPFileItem>, Void> worker = new SwingWorker<>() {
            private String path;

            @Override
            protected List<FTPFileItem> doInBackground() throws Exception {
                path = clientEngine.pwd();
                return clientEngine.listFiles("");
            }

            @Override
            protected void done() {
                try {
                    currentItems = get();
                    txtRemotePath.setText(path);
                    tableModel.setRowCount(0);

                    // Sắp xếp: Thư mục lên trước
                    currentItems.sort(Comparator.comparing((FTPFileItem i) -> !i.isDirectory())
                            .thenComparing(FTPFileItem::getName, String.CASE_INSENSITIVE_ORDER));

                    for (FTPFileItem item : currentItems) {
                        String name = (item.isDirectory() ? "📁 " : "📄 ") + item.getName();
                        String size = item.isDirectory() ? "" : FormatUtils.formatFileSize(item.getSize());
                        String type = item.isDirectory() ? "Thư mục" : "Tệp tin";
                        String perms = item.getPermissions() != null ? item.getPermissions() : "";
                        String date = item.getLastModified() != null ? item.getLastModified().format(FormatUtils.DISPLAY_DATETIME_FORMATTER) : "";

                        tableModel.addRow(new Object[]{name, size, type, perms, date});
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(RemoteFilePanel.this, "Lỗi khi lấy danh sách tệp từ Server: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void onItemDoubleClicked() {
        int row = tableFiles.getSelectedRow();
        if (row == -1 || row >= currentItems.size()) return;

        FTPFileItem item = currentItems.get(row);
        if (item.isDirectory()) {
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    clientEngine.cwd(item.getName());
                    return null;
                }

                @Override
                protected void done() {
                    refreshRemoteFiles();
                }
            }.execute();
        }
    }

    private FTPFileItem getSelectedItem() {
        int row = tableFiles.getSelectedRow();
        if (row == -1 || row >= currentItems.size()) return null;
        return currentItems.get(row);
    }

    private void showPopupMenu(MouseEvent e) {
        int row = tableFiles.rowAtPoint(e.getPoint());
        if (row >= 0 && row < tableFiles.getRowCount()) {
            tableFiles.setRowSelectionInterval(row, row);
        }

        FTPFileItem selected = getSelectedItem();
        JPopupMenu menu = new JPopupMenu();

        JMenuItem mnuDownload = new JMenuItem("⬇ Tải về máy (Download)");
        mnuDownload.setEnabled(selected != null && !selected.isDirectory());
        mnuDownload.addActionListener(ev -> {
            if (selected != null && onDownloadRequested != null) {
                onDownloadRequested.accept(selected);
            }
        });
        menu.add(mnuDownload);
        menu.addSeparator();

        JMenuItem mnuNewFolder = new JMenuItem("📁 Tạo thư mục mới trên Server...");
        mnuNewFolder.setEnabled(clientEngine.isAuthenticated());
        mnuNewFolder.addActionListener(ev -> {
            String name = JOptionPane.showInputDialog(this, "Nhập tên thư mục mới trên Server:", "Tạo thư mục", JOptionPane.PLAIN_MESSAGE);
            if (name != null && !name.trim().isEmpty()) {
                new SwingWorker<Boolean, Void>() {
                    @Override
                    protected Boolean doInBackground() throws Exception {
                        return clientEngine.makeDirectory(name.trim());
                    }

                    @Override
                    protected void done() {
                        try {
                            if (get()) {
                                refreshRemoteFiles();
                            } else {
                                JOptionPane.showMessageDialog(RemoteFilePanel.this, "Không thể tạo thư mục (Quyền bị từ chối hoặc đã tồn tại)!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                            }
                        } catch (Exception ex) {
                            JOptionPane.showMessageDialog(RemoteFilePanel.this, "Lỗi: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                }.execute();
            }
        });
        menu.add(mnuNewFolder);

        JMenuItem mnuDelete = new JMenuItem("🗑 Xóa trên Server");
        mnuDelete.setEnabled(selected != null);
        mnuDelete.addActionListener(ev -> {
            if (selected != null) {
                int ch = JOptionPane.showConfirmDialog(this, "Bạn có chắc muốn xóa '" + selected.getName() + "' trên Server?", "Xác nhận xóa", JOptionPane.YES_NO_OPTION);
                if (ch == JOptionPane.YES_OPTION) {
                    new SwingWorker<Boolean, Void>() {
                        @Override
                        protected Boolean doInBackground() throws Exception {
                            if (selected.isDirectory()) {
                                return clientEngine.removeDirectory(selected.getName());
                            } else {
                                return clientEngine.deleteFile(selected.getName());
                            }
                        }

                        @Override
                        protected void done() {
                            try {
                                if (get()) {
                                    refreshRemoteFiles();
                                } else {
                                    JOptionPane.showMessageDialog(RemoteFilePanel.this, "Xóa thất bại (Quyền bị từ chối hoặc thư mục không rỗng)!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                                }
                            } catch (Exception ex) {
                                JOptionPane.showMessageDialog(RemoteFilePanel.this, "Lỗi: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    }.execute();
                }
            }
        });
        menu.add(mnuDelete);

        JMenuItem mnuRefresh = new JMenuItem("🔄 Làm mới");
        mnuRefresh.setEnabled(clientEngine.isAuthenticated());
        mnuRefresh.addActionListener(ev -> refreshRemoteFiles());
        menu.add(mnuRefresh);

        menu.show(tableFiles, e.getX(), e.getY());
    }
}
