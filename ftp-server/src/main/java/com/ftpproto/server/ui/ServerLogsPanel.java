package com.ftpproto.server.ui;

import com.ftpproto.common.util.FormatUtils;
import com.ftpproto.server.event.LogEvent;
import com.ftpproto.server.event.ServerEventBus;
import com.ftpproto.server.event.ServerEventListener;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;

/**
 * Tab 4: Nhật ký hệ thống ghi lại raw command và response theo thời gian thực.
 */
public class ServerLogsPanel extends JPanel implements ServerEventListener {
    private JTable tableLogs;
    private DefaultTableModel tableModel;
    private TableRowSorter<DefaultTableModel> sorter;
    private JCheckBox chkAutoScroll;
    private JTextField txtSearch;
    private JComboBox<String> cmbDirectionFilter;

    public ServerLogsPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        initUI();
        ServerEventBus.getInstance().register(this);
    }

    private void initUI() {
        // Toolbar bộ lọc và nút xóa
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

        filterPanel.add(new JLabel("Lọc theo loại:"));
        cmbDirectionFilter = new JComboBox<>(new String[]{"Tất cả", "Commands (IN)", "Responses (OUT)", "Hệ thống (SYS)", "Chỉ lỗi"});
        cmbDirectionFilter.addActionListener(e -> applyFilters());
        filterPanel.add(cmbDirectionFilter);

        filterPanel.add(new JLabel("Tìm kiếm:"));
        txtSearch = new JTextField(15);
        txtSearch.addCaretListener(e -> applyFilters());
        filterPanel.add(txtSearch);

        chkAutoScroll = new JCheckBox("Tự động cuộn", true);
        filterPanel.add(chkAutoScroll);

        JButton btnClear = new JButton("Xóa nhật ký");
        btnClear.addActionListener(e -> tableModel.setRowCount(0));
        filterPanel.add(btnClear);

        add(filterPanel, BorderLayout.NORTH);

        // Bảng Logs
        String[] columns = {"Thời gian", "Client IP", "Chiều", "Nội dung giao thức (Raw Command / Response)"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        sorter = new TableRowSorter<>(tableModel);
        tableLogs = new JTable(tableModel);
        tableLogs.setRowSorter(sorter);
        tableLogs.setRowHeight(24);
        tableLogs.setFont(new Font("Monospaced", Font.PLAIN, 12));

        tableLogs.getColumnModel().getColumn(0).setPreferredWidth(140);
        tableLogs.getColumnModel().getColumn(1).setPreferredWidth(120);
        tableLogs.getColumnModel().getColumn(2).setPreferredWidth(80);
        tableLogs.getColumnModel().getColumn(3).setPreferredWidth(500);

        // Renderer màu sắc cho từng dòng log
        tableLogs.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    int modelRow = table.convertRowIndexToModel(row);
                    String dir = (String) tableModel.getValueAt(modelRow, 2);
                    String content = (String) tableModel.getValueAt(modelRow, 3);

                    if ("IN".equals(dir)) {
                        c.setForeground(new Color(0, 120, 215)); // Xanh dương cho lệnh client gửi
                    } else if ("OUT".equals(dir)) {
                        if (content != null && (content.startsWith("5") || content.startsWith("4"))) {
                            c.setForeground(new Color(220, 53, 69)); // Đỏ cho phản hồi lỗi 4xx, 5xx
                        } else {
                            c.setForeground(new Color(40, 167, 69)); // Xanh lá cho phản hồi thành công
                        }
                    } else {
                        c.setForeground(Color.GRAY);
                    }
                }
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(tableLogs);
        add(scrollPane, BorderLayout.CENTER);
    }

    private void applyFilters() {
        String query = txtSearch.getText().trim();
        String typeFilter = (String) cmbDirectionFilter.getSelectedItem();

        RowFilter<DefaultTableModel, Object> rf = new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends DefaultTableModel, ?> entry) {
                if ("Commands (IN)".equals(typeFilter) && !"IN".equals(entry.getStringValue(2))) return false;
                if ("Responses (OUT)".equals(typeFilter) && !"OUT".equals(entry.getStringValue(2))) return false;
                if ("Hệ thống (SYS)".equals(typeFilter) && !"SYS".equals(entry.getStringValue(2))) return false;
                if ("Chỉ lỗi".equals(typeFilter)) {
                    String content = entry.getStringValue(3);
                    if (!content.startsWith("5") && !content.startsWith("4")) return false;
                }

                if (!query.isEmpty()) {
                    boolean matched = false;
                    for (int i = 0; i < entry.getValueCount(); i++) {
                        if (entry.getStringValue(i).toLowerCase().contains(query.toLowerCase())) {
                            matched = true;
                            break;
                        }
                    }
                    if (!matched) return false;
                }
                return true;
            }
        };
        sorter.setRowFilter(rf);
    }

    @Override
    public void onLogEvent(LogEvent event) {
        SwingUtilities.invokeLater(() -> {
            String dirStr = switch (event.getDirection()) {
                case INCOMING -> "IN";
                case OUTGOING -> "OUT";
                case SYSTEM -> "SYS";
            };

            tableModel.addRow(new Object[]{
                    event.getTimestamp().format(FormatUtils.DISPLAY_DATETIME_FORMATTER),
                    event.getClientIp(),
                    dirStr,
                    event.getContent()
            });

            // Giới hạn 2000 dòng log để tránh tràn RAM
            if (tableModel.getRowCount() > 2000) {
                tableModel.removeRow(0);
            }

            if (chkAutoScroll.isSelected()) {
                int lastRow = tableLogs.getRowCount() - 1;
                if (lastRow >= 0) {
                    tableLogs.scrollRectToVisible(tableLogs.getCellRect(lastRow, 0, true));
                }
            }
        });
    }
}
