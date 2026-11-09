package com.ftpproto.server.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Quản lý cổng kết nối dữ liệu ở chế độ bị động (Passive Mode - PASV).
 */
public class PassiveDataServer {
    private static final Logger logger = LoggerFactory.getLogger(PassiveDataServer.class);

    private ServerSocket passiveServerSocket;
    private final InetAddress serverAddress;
    private final int port;

    public PassiveDataServer(InetAddress serverAddress) throws IOException {
        this.serverAddress = serverAddress;
        // Mở ServerSocket trên cổng ngẫu nhiên của hệ điều hành
        this.passiveServerSocket = new ServerSocket(0, 1, serverAddress);
        this.passiveServerSocket.setSoTimeout(30000); // Timeout 30 giây chờ client kết nối dữ liệu
        this.port = this.passiveServerSocket.getLocalPort();
        logger.debug("Khởi tạo Passive Data Server tại {}:{}", serverAddress.getHostAddress(), port);
    }

    public int getPort() {
        return port;
    }

    /**
     * Tạo chuỗi định dạng phản hồi cho lệnh PASV: (h1,h2,h3,h4,p1,p2)
     */
    public String getPasvResponseString() {
        byte[] addressBytes = serverAddress.getAddress();
        int h1 = addressBytes[0] & 0xFF;
        int h2 = addressBytes[1] & 0xFF;
        int h3 = addressBytes[2] & 0xFF;
        int h4 = addressBytes[3] & 0xFF;
        int p1 = (port >> 8) & 0xFF;
        int p2 = port & 0xFF;

        return String.format("(%d,%d,%d,%d,%d,%d)", h1, h2, h3, h4, p1, p2);
    }

    /**
     * Chờ và chấp nhận kết nối Data Socket từ Client.
     */
    public Socket acceptDataSocket() throws IOException {
        if (passiveServerSocket == null || passiveServerSocket.isClosed()) {
            throw new IOException("Passive ServerSocket đã bị đóng");
        }
        try {
            Socket socket = passiveServerSocket.accept();
            logger.debug("Đã chấp nhận kết nối Data Socket từ {}:{}", socket.getInetAddress(), socket.getPort());
            return socket;
        } finally {
            closeServerSocket();
        }
    }

    public void closeServerSocket() {
        if (passiveServerSocket != null && !passiveServerSocket.isClosed()) {
            try {
                passiveServerSocket.close();
            } catch (IOException ignored) {}
        }
    }
}
