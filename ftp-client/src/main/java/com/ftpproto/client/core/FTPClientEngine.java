package com.ftpproto.client.core;

import com.ftpproto.client.event.ClientLogListener;
import com.ftpproto.client.event.TransferProgressListener;
import com.ftpproto.common.model.FTPFileItem;
import com.ftpproto.common.protocol.FTPReply;
import com.ftpproto.common.protocol.FTPStatusCode;
import com.ftpproto.common.util.FormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Động cơ giao thức FTP phía Client thực hiện các lệnh Control và truyền dữ liệu qua Data Socket.
 */
public class FTPClientEngine {
    private static final Logger logger = LoggerFactory.getLogger(FTPClientEngine.class);
    private static final Pattern PASV_PATTERN = Pattern.compile("(\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)");

    private Socket controlSocket;
    private BufferedReader reader;
    private BufferedWriter writer;

    private String connectedHost;
    private int connectedPort;
    private boolean connected = false;
    private boolean authenticated = false;
    private String currentRemotePath = "/";

    private final List<ClientLogListener> logListeners = new CopyOnWriteArrayList<>();

    public void addLogListener(ClientLogListener listener) {
        if (listener != null && !logListeners.contains(listener)) {
            logListeners.add(listener);
        }
    }

    public void removeLogListener(ClientLogListener listener) {
        logListeners.remove(listener);
    }

    private void log(String type, String message) {
        for (ClientLogListener l : logListeners) {
            try {
                l.onLog(type, message);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Kết nối tới FTP Server trên Control Port.
     */
    public synchronized boolean connect(String host, int port) throws IOException {
        disconnect();

        log("STATUS", "Đang kết nối tới " + host + ":" + port + "...");
        this.connectedHost = host;
        this.connectedPort = port;

        controlSocket = new Socket();
        controlSocket.connect(new InetSocketAddress(host, port), 10000);
        controlSocket.setSoTimeout(30000);

        reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream(), StandardCharsets.UTF_8));
        writer = new BufferedWriter(new OutputStreamWriter(controlSocket.getOutputStream(), StandardCharsets.UTF_8));

        FTPReply greeting = readReply();
        if (greeting == null || !greeting.isPositiveCompletion()) {
            disconnect();
            throw new IOException("Không nhận được lời chào hợp lệ từ máy chủ: " + greeting);
        }

        connected = true;
        log("STATUS", "Đã kết nối thành công tới máy chủ FTP!");

        // Gửi lệnh TYPE I (Binary) mặc định
        sendCommand("TYPE I");
        return true;
    }

    /**
     * Xác thực người dùng bằng lệnh USER và PASS.
     */
    public synchronized boolean login(String username, String password) throws IOException {
        if (!connected) {
            throw new IOException("Chưa kết nối tới máy chủ");
        }

        FTPReply userReply = sendCommand("USER " + username);
        if (userReply.getCode() == FTPStatusCode.USER_LOGGED_IN) {
            // Trường hợp không cần mật khẩu (anonymous)
            authenticated = true;
            pwd();
            return true;
        }

        if (userReply.getCode() == FTPStatusCode.USER_NAME_OK_NEED_PASSWORD) {
            FTPReply passReply = sendCommand("PASS " + password);
            if (passReply.getCode() == FTPStatusCode.USER_LOGGED_IN) {
                authenticated = true;
                log("STATUS", "Đăng nhập thành công với tài khoản: " + username);
                pwd();
                return true;
            } else {
                log("ERROR", "Đăng nhập thất bại: " + passReply.getMessage());
                return false;
            }
        }

        return false;
    }

    /**
     * Gửi lệnh thô và đọc phản hồi từ Server.
     */
    public synchronized FTPReply sendCommand(String command) throws IOException {
        if (writer == null || controlSocket == null || controlSocket.isClosed()) {
            throw new IOException("Control socket chưa sẵn sàng hoặc đã đóng");
        }

        // Không ghi mật khẩu vào log giao diện
        String displayCommand = command;
        if (command.startsWith("PASS ")) {
            displayCommand = "PASS ********";
        }
        log("COMMAND", displayCommand);

        writer.write(command + "\r\n");
        writer.flush();

        return readReply();
    }

    /**
     * Đọc phản hồi (hỗ trợ cả multiline replies).
     */
    private FTPReply readReply() throws IOException {
        String line = reader.readLine();
        if (line == null) {
            throw new IOException("Máy chủ đã đóng kết nối.");
        }

        // Multiline reply ví dụ: "220-Welcome\r\n ... \r\n220 Ready"
        if (line.length() >= 4 && line.charAt(3) == '-') {
            int code = Integer.parseInt(line.substring(0, 3));
            StringBuilder sb = new StringBuilder(line.substring(4));
            log("RESPONSE", line);
            while ((line = reader.readLine()) != null) {
                log("RESPONSE", line);
                if (line.length() >= 3 && line.startsWith(String.valueOf(code)) && (line.length() == 3 || line.charAt(3) == ' ')) {
                    if (line.length() > 4) {
                        sb.append("\n").append(line.substring(4));
                    }
                    break;
                } else {
                    sb.append("\n").append(line);
                }
            }
            return new FTPReply(code, sb.toString());
        }

        FTPReply reply = FTPReply.parse(line);
        if (reply != null) {
            if (reply.isPermanentNegative() || reply.isTransientNegative()) {
                log("ERROR", line);
            } else {
                log("RESPONSE", line);
            }
        }
        return reply;
    }

    /**
     * Mở kênh truyền dữ liệu theo chế độ bị động (Passive Mode - PASV).
     */
    public synchronized Socket openDataConnection() throws IOException {
        FTPReply pasvReply = sendCommand("PASV");
        if (pasvReply.getCode() != FTPStatusCode.ENTERING_PASSIVE_MODE) {
            throw new IOException("Lệnh PASV thất bại: " + pasvReply);
        }

        Matcher matcher = PASV_PATTERN.matcher(pasvReply.getMessage());
        if (!matcher.find()) {
            throw new IOException("Không thể phân tích cú pháp phản hồi PASV: " + pasvReply.getMessage());
        }

        String dataHost = matcher.group(1) + "." + matcher.group(2) + "." + matcher.group(3) + "." + matcher.group(4);
        int dataPort = (Integer.parseInt(matcher.group(5)) << 8) + Integer.parseInt(matcher.group(6));

        // Nếu IP phản hồi là 127.0.0.1 hoặc private trong khi client kết nối qua domain/ip khác, ưu tiên connectedHost
        if ("127.0.0.1".equals(dataHost) && !"127.0.0.1".equals(connectedHost)) {
            dataHost = connectedHost;
        }

        log("STATUS", "Mở kết nối dữ liệu Data Socket tới " + dataHost + ":" + dataPort);
        Socket dataSocket = new Socket();
        dataSocket.connect(new InetSocketAddress(dataHost, dataPort), 10000);
        dataSocket.setSoTimeout(30000);
        return dataSocket;
    }

    /**
     * Lấy đường dẫn thư mục hiện tại trên Server (PWD).
     */
    public synchronized String pwd() throws IOException {
        FTPReply reply = sendCommand("PWD");
        if (reply.getCode() == FTPStatusCode.PATHNAME_CREATED) {
            String msg = reply.getMessage();
            int firstQuote = msg.indexOf('"');
            int lastQuote = msg.lastIndexOf('"');
            if (firstQuote != -1 && lastQuote > firstQuote) {
                this.currentRemotePath = msg.substring(firstQuote + 1, lastQuote);
            } else {
                this.currentRemotePath = msg.split("\\s+")[0];
            }
        }
        return currentRemotePath;
    }

    /**
     * Thay đổi thư mục làm việc trên Server (CWD).
     */
    public synchronized boolean cwd(String path) throws IOException {
        FTPReply reply = sendCommand("CWD " + path);
        if (reply.getCode() == FTPStatusCode.FILE_ACTION_COMPLETED) {
            pwd();
            return true;
        }
        return false;
    }

    /**
     * Lên thư mục cha trên Server (CDUP).
     */
    public synchronized boolean cdup() throws IOException {
        FTPReply reply = sendCommand("CDUP");
        if (reply.getCode() == FTPStatusCode.FILE_ACTION_COMPLETED) {
            pwd();
            return true;
        }
        return false;
    }

    /**
     * Lấy danh sách tệp trên Server (LIST).
     */
    public synchronized List<FTPFileItem> listFiles(String path) throws IOException {
        List<FTPFileItem> items = new ArrayList<>();
        Socket dataSocket = openDataConnection();

        String cmd = (path == null || path.isEmpty()) ? "LIST" : "LIST " + path;
        FTPReply reply = sendCommand(cmd);

        if (reply.getCode() != FTPStatusCode.FILE_STATUS_OK_OPENING_DATA_CONNECTION &&
            reply.getCode() != FTPStatusCode.DATA_CONNECTION_ALREADY_OPEN) {
            dataSocket.close();
            throw new IOException("Không thể mở danh sách file: " + reply);
        }

        try (BufferedReader dReader = new BufferedReader(new InputStreamReader(dataSocket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = dReader.readLine()) != null) {
                FTPFileItem item = FormatUtils.parseUnixListLine(line);
                if (item != null && !".".equals(item.getName()) && !"..".equals(item.getName())) {
                    items.add(item);
                }
            }
        } finally {
            dataSocket.close();
        }

        // Đọc phản hồi hoàn thành 226
        FTPReply completeReply = readReply();
        if (completeReply != null && completeReply.getCode() != FTPStatusCode.CLOSING_DATA_CONNECTION && completeReply.getCode() != FTPStatusCode.COMMAND_OK) {
            logger.warn("Phản hồi sau LIST: {}", completeReply);
        }

        return items;
    }

    /**
     * Tải file từ Server về Local (RETR) kèm theo dõi tiến trình.
     */
    public void downloadFile(String remoteFileName, File localTargetFile, TransferProgressListener listener) throws IOException {
        long totalBytes = getFileSize(remoteFileName);
        if (listener != null) {
            listener.onStart("DOWNLOAD", remoteFileName, totalBytes);
        }

        Socket dataSocket = openDataConnection();
        FTPReply reply = sendCommand("RETR " + remoteFileName);

        if (reply.getCode() != FTPStatusCode.FILE_STATUS_OK_OPENING_DATA_CONNECTION &&
            reply.getCode() != FTPStatusCode.DATA_CONNECTION_ALREADY_OPEN) {
            dataSocket.close();
            if (listener != null) {
                listener.onError("DOWNLOAD", remoteFileName, reply.getMessage());
            }
            throw new IOException("Không thể bắt đầu tải file: " + reply.getMessage());
        }

        long transferred = 0;
        long startTime = System.currentTimeMillis();
        long lastReportTime = startTime;

        try (InputStream is = new BufferedInputStream(dataSocket.getInputStream());
             OutputStream os = new BufferedOutputStream(new FileOutputStream(localTargetFile))) {

            byte[] buffer = new byte[16384];
            int read;

            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
                transferred += read;

                long now = System.currentTimeMillis();
                if (now - lastReportTime >= 200 || transferred == totalBytes) {
                    long duration = Math.max(1, now - startTime);
                    long speed = (transferred * 1000) / duration;
                    if (listener != null) {
                        listener.onProgress("DOWNLOAD", remoteFileName, transferred, totalBytes, speed);
                    }
                    lastReportTime = now;
                }
            }
            os.flush();
        } catch (Exception e) {
            if (listener != null) {
                listener.onError("DOWNLOAD", remoteFileName, e.getMessage());
            }
            throw e;
        } finally {
            dataSocket.close();
        }

        FTPReply compReply = readReply();
        if (compReply != null && compReply.getCode() == FTPStatusCode.CLOSING_DATA_CONNECTION) {
            if (listener != null) {
                listener.onComplete("DOWNLOAD", remoteFileName, transferred);
            }
            log("STATUS", "Đã tải file thành công: " + remoteFileName + " (" + FormatUtils.formatFileSize(transferred) + ")");
        } else {
            if (listener != null) {
                listener.onError("DOWNLOAD", remoteFileName, compReply != null ? compReply.getMessage() : "Không rõ lỗi");
            }
        }
    }

    /**
     * Tải file từ Local lên Server (STOR) kèm theo dõi tiến trình.
     */
    public void uploadFile(File localFile, String remoteFileName, TransferProgressListener listener) throws IOException {
        long totalBytes = localFile.length();
        if (listener != null) {
            listener.onStart("UPLOAD", remoteFileName, totalBytes);
        }

        Socket dataSocket = openDataConnection();
        FTPReply reply = sendCommand("STOR " + remoteFileName);

        if (reply.getCode() != FTPStatusCode.FILE_STATUS_OK_OPENING_DATA_CONNECTION &&
            reply.getCode() != FTPStatusCode.DATA_CONNECTION_ALREADY_OPEN) {
            dataSocket.close();
            if (listener != null) {
                listener.onError("UPLOAD", remoteFileName, reply.getMessage());
            }
            throw new IOException("Không thể bắt đầu tải file lên: " + reply.getMessage());
        }

        long transferred = 0;
        long startTime = System.currentTimeMillis();
        long lastReportTime = startTime;

        try (InputStream is = new BufferedInputStream(new FileInputStream(localFile));
             OutputStream os = new BufferedOutputStream(dataSocket.getOutputStream())) {

            byte[] buffer = new byte[16384];
            int read;

            while ((read = is.read(buffer)) != -1) {
                os.write(buffer, 0, read);
                transferred += read;

                long now = System.currentTimeMillis();
                if (now - lastReportTime >= 200 || transferred == totalBytes) {
                    long duration = Math.max(1, now - startTime);
                    long speed = (transferred * 1000) / duration;
                    if (listener != null) {
                        listener.onProgress("UPLOAD", remoteFileName, transferred, totalBytes, speed);
                    }
                    lastReportTime = now;
                }
            }
            os.flush();
        } catch (Exception e) {
            if (listener != null) {
                listener.onError("UPLOAD", remoteFileName, e.getMessage());
            }
            throw e;
        } finally {
            dataSocket.close();
        }

        FTPReply compReply = readReply();
        if (compReply != null && compReply.getCode() == FTPStatusCode.CLOSING_DATA_CONNECTION) {
            if (listener != null) {
                listener.onComplete("UPLOAD", remoteFileName, transferred);
            }
            log("STATUS", "Đã tải lên file thành công: " + remoteFileName + " (" + FormatUtils.formatFileSize(transferred) + ")");
        } else {
            if (listener != null) {
                listener.onError("UPLOAD", remoteFileName, compReply != null ? compReply.getMessage() : "Không rõ lỗi");
            }
        }
    }

    /**
     * Xóa file trên Server (DELE).
     */
    public synchronized boolean deleteFile(String remoteFileName) throws IOException {
        FTPReply reply = sendCommand("DELE " + remoteFileName);
        return reply.getCode() == FTPStatusCode.FILE_ACTION_COMPLETED;
    }

    /**
     * Tạo thư mục mới trên Server (MKD).
     */
    public synchronized boolean makeDirectory(String remoteDirName) throws IOException {
        FTPReply reply = sendCommand("MKD " + remoteDirName);
        return reply.getCode() == FTPStatusCode.PATHNAME_CREATED;
    }

    /**
     * Xóa thư mục trên Server (RMD).
     */
    public synchronized boolean removeDirectory(String remoteDirName) throws IOException {
        FTPReply reply = sendCommand("RMD " + remoteDirName);
        return reply.getCode() == FTPStatusCode.FILE_ACTION_COMPLETED;
    }

    /**
     * Lấy kích thước file trên Server (SIZE).
     */
    public synchronized long getFileSize(String remoteFileName) {
        try {
            FTPReply reply = sendCommand("SIZE " + remoteFileName);
            if (reply.getCode() == FTPStatusCode.FILE_STATUS) {
                return Long.parseLong(reply.getMessage().trim());
            }
        } catch (Exception ignored) {}
        return 0;
    }

    /**
     * Ngắt kết nối khỏi máy chủ FTP (QUIT).
     */
    public synchronized void disconnect() {
        if (connected) {
            try {
                sendCommand("QUIT");
            } catch (Exception ignored) {}
        }
        connected = false;
        authenticated = false;
        currentRemotePath = "/";

        try {
            if (controlSocket != null && !controlSocket.isClosed()) {
                controlSocket.close();
            }
        } catch (IOException ignored) {}

        log("STATUS", "Đã ngắt kết nối khỏi máy chủ.");
    }

    public boolean isConnected() {
        return connected && controlSocket != null && !controlSocket.isClosed();
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public String getCurrentRemotePath() {
        return currentRemotePath;
    }
}
