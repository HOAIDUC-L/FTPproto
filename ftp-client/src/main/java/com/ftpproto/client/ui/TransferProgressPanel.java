package com.ftpproto.client.ui;

import com.ftpproto.client.event.TransferProgressListener;
import com.ftpproto.common.util.FormatUtils;

import javax.swing.*;
import java.awt.*;

/**
 * Khung hiển thị thanh tiến trình truyền tải file dung lượng lớn (Progress Bar & Speed).
 */
public class TransferProgressPanel extends JPanel implements TransferProgressListener {
    private JProgressBar progressBar;
    private JLabel lblStatus;
    private JLabel lblDetails;

    public TransferProgressPanel() {
        setLayout(new BorderLayout(10, 5));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(200, 200, 200, 60)),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));

        initUI();
    }

    private void initUI() {
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        lblStatus = new JLabel("Hệ thống sẵn sàng.");
        lblStatus.setFont(new Font("SansSerif", Font.BOLD, 12));
        leftPanel.add(lblStatus);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        lblDetails = new JLabel("");
        lblDetails.setForeground(Color.GRAY);
        rightPanel.add(lblDetails);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(250, 18));
        rightPanel.add(progressBar);

        add(leftPanel, BorderLayout.WEST);
        add(rightPanel, BorderLayout.EAST);
    }

    @Override
    public void onStart(String action, String fileName, long totalBytes) {
        SwingUtilities.invokeLater(() -> {
            String actStr = "UPLOAD".equalsIgnoreCase(action) ? "Đang tải lên: " : "Đang tải về: ";
            lblStatus.setText(actStr + fileName);
            progressBar.setValue(0);
            progressBar.setString("0%");
            lblDetails.setText(totalBytes > 0 ? "0 / " + FormatUtils.formatFileSize(totalBytes) : "");
        });
    }

    @Override
    public void onProgress(String action, String fileName, long transferredBytes, long totalBytes, long bytesPerSec) {
        SwingUtilities.invokeLater(() -> {
            int percent = totalBytes > 0 ? (int) ((transferredBytes * 100) / totalBytes) : 0;
            progressBar.setValue(percent);
            progressBar.setString(percent + "%");

            String speedStr = FormatUtils.formatSpeed(bytesPerSec);
            String progressStr = FormatUtils.formatFileSize(transferredBytes) + (totalBytes > 0 ? " / " + FormatUtils.formatFileSize(totalBytes) : "");
            lblDetails.setText(progressStr + " (" + speedStr + ")");
        });
    }

    @Override
    public void onComplete(String action, String fileName, long totalBytes) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(100);
            progressBar.setString("100% Hoàn thành");
            String actStr = "UPLOAD".equalsIgnoreCase(action) ? "Đã tải lên xong: " : "Đã tải về xong: ";
            lblStatus.setText(actStr + fileName + " (" + FormatUtils.formatFileSize(totalBytes) + ")");
            lblDetails.setText("Thành công");
        });
    }

    @Override
    public void onError(String action, String fileName, String error) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(0);
            progressBar.setString("Thất bại");
            lblStatus.setText("Lỗi truyền tải: " + fileName);
            lblDetails.setText(error != null ? error : "Lỗi không xác định");
        });
    }
}
