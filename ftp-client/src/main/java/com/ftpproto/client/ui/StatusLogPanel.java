package com.ftpproto.client.ui;

import com.ftpproto.client.event.ClientLogListener;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Khung hiển thị log giao tiếp FTP (Status / Message Log) với mã màu chuẩn FileZilla.
 */
public class StatusLogPanel extends JPanel implements ClientLogListener {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private JTextPane textPane;
    private StyledDocument doc;
    private SimpleAttributeSet styleStatus;
    private SimpleAttributeSet styleCommand;
    private SimpleAttributeSet styleResponse;
    private SimpleAttributeSet styleError;

    public StatusLogPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(200, 200, 200, 50)), "Status Log"),
                BorderFactory.createEmptyBorder(2, 4, 2, 4)
        ));
        setPreferredSize(new Dimension(800, 130));

        initUI();
    }

    private void initUI() {
        textPane = new JTextPane();
        textPane.setEditable(false);
        textPane.setFont(new Font("Monospaced", Font.PLAIN, 12));
        doc = textPane.getStyledDocument();

        // Khởi tạo các bảng màu
        styleStatus = new SimpleAttributeSet();
        StyleConstants.setForeground(styleStatus, new Color(170, 178, 189));

        styleCommand = new SimpleAttributeSet();
        StyleConstants.setForeground(styleCommand, new Color(52, 152, 219)); // Xanh dương
        StyleConstants.setBold(styleCommand, true);

        styleResponse = new SimpleAttributeSet();
        StyleConstants.setForeground(styleResponse, new Color(46, 204, 113)); // Xanh lá

        styleError = new SimpleAttributeSet();
        StyleConstants.setForeground(styleError, new Color(231, 76, 60)); // Đỏ
        StyleConstants.setBold(styleError, true);

        JScrollPane scrollPane = new JScrollPane(textPane);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        add(scrollPane, BorderLayout.CENTER);
    }

    @Override
    public void onLog(String type, String message) {
        SwingUtilities.invokeLater(() -> {
            try {
                String time = LocalTime.now().format(TIME_FORMATTER);
                String prefix = switch (type) {
                    case "COMMAND" -> "Command:   ";
                    case "RESPONSE" -> "Response:  ";
                    case "ERROR" -> "Error:     ";
                    default -> "Status:    ";
                };

                AttributeSet style = switch (type) {
                    case "COMMAND" -> styleCommand;
                    case "RESPONSE" -> styleResponse;
                    case "ERROR" -> styleError;
                    default -> styleStatus;
                };

                String line = String.format("[%s] %s%s\n", time, prefix, message);
                doc.insertString(doc.getLength(), line, style);

                // Giới hạn độ dài buffer để tránh chiếm bộ nhớ
                if (doc.getLength() > 50000) {
                    doc.remove(0, 10000);
                }

                // Tự động cuộn xuống cuối
                textPane.setCaretPosition(doc.getLength());
            } catch (BadLocationException ignored) {}
        });
    }

    public void clear() {
        textPane.setText("");
    }
}
