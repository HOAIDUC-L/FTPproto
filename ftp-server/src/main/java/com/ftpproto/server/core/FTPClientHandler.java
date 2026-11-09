package com.ftpproto.server.core;

import com.ftpproto.common.model.User;
import com.ftpproto.common.protocol.FTPReply;
import com.ftpproto.common.protocol.FTPStatusCode;
import com.ftpproto.server.command.CommandRegistry;
import com.ftpproto.server.command.FTPCommandHandler;
import com.ftpproto.server.event.ConnectionEvent;
import com.ftpproto.server.event.LogEvent;
import com.ftpproto.server.event.ServerEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Xử lý phiên làm việc của một Client kết nối tới Control Socket.
 */
public class FTPClientHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(FTPClientHandler.class);

    private final String sessionId;
    private final Socket socket;
    private final FTPServer server;
    private final CommandRegistry commandRegistry;

    private BufferedReader reader;
    private BufferedWriter writer;

    private final String clientIp;
    private final int clientPort;
    private final LocalDateTime connectTime;

    private User currentUser;
    private String pendingUsername;
    private ChrootFileSystemHandler fileSystem;
    private PassiveDataServer passiveDataServer;
    private String activityStatus = "Idle";
    private volatile boolean running = true;

    public FTPClientHandler(Socket socket, FTPServer server, CommandRegistry commandRegistry) {
        this.sessionId = UUID.randomUUID().toString().substring(0, 8);
        this.socket = socket;
        this.server = server;
        this.commandRegistry = commandRegistry;
        this.clientIp = socket.getInetAddress().getHostAddress();
        this.clientPort = socket.getPort();
        this.connectTime = LocalDateTime.now();
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            // Báo cáo sự kiện kết nối mới
            ServerEventBus.getInstance().postConnectionEvent(new ConnectionEvent(
                    ConnectionEvent.Type.CONNECTED, sessionId, clientIp, clientPort,
                    "anonymous", connectTime, "/", activityStatus
            ));

            // Gửi lời chào mở đầu chuẩn FTP 220
            sendReply(FTPStatusCode.SERVICE_READY, "FTPproto Server ready - Welcome!");

            String line;
            while (running && (line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // Ghi log command nhận được
                ServerEventBus.getInstance().postLogEvent(new LogEvent(clientIp, LogEvent.Direction.INCOMING, line));

                processCommand(line);
            }
        } catch (IOException e) {
            if (running) {
                logger.debug("Client {} ngắt kết nối: {}", clientIp, e.getMessage());
            }
        } finally {
            close();
        }
    }

    private void processCommand(String commandLine) throws IOException {
        String commandName;
        String argument = null;

        int spaceIdx = commandLine.indexOf(' ');
        if (spaceIdx != -1) {
            commandName = commandLine.substring(0, spaceIdx);
            argument = commandLine.substring(spaceIdx + 1).trim();
        } else {
            commandName = commandLine;
        }

        FTPCommandHandler handler = commandRegistry.getHandler(commandName);
        if (handler != null) {
            try {
                handler.execute(this, argument);
            } catch (Exception e) {
                logger.error("Lỗi khi thực thi lệnh '{}' từ {}: {}", commandName, clientIp, e.getMessage(), e);
                sendReply(FTPStatusCode.ACTION_ABORTED_LOCAL_ERROR, "Internal server error: " + e.getMessage());
            }
        } else {
            logger.warn("Lệnh không được hỗ trợ: '{}' từ {}", commandName, clientIp);
            sendReply(FTPStatusCode.COMMAND_NOT_IMPLEMENTED, "Command not implemented");
        }
    }

    public synchronized void sendReply(int code, String message) throws IOException {
        sendReply(new FTPReply(code, message));
    }

    public synchronized void sendReply(FTPReply reply) throws IOException {
        if (writer == null || socket.isClosed()) return;
        String raw = reply.toRawResponse();
        writer.write(raw);
        writer.flush();

        ServerEventBus.getInstance().postLogEvent(new LogEvent(clientIp, LogEvent.Direction.OUTGOING,
                reply.getCode() + " " + reply.getMessage(), reply.isPermanentNegative() || reply.isTransientNegative()));
    }

    public synchronized void sendRawLine(String rawLine) throws IOException {
        if (writer == null || socket.isClosed()) return;
        writer.write(rawLine);
        writer.flush();
        ServerEventBus.getInstance().postLogEvent(new LogEvent(clientIp, LogEvent.Direction.OUTGOING, rawLine.trim()));
    }

    public boolean requireAuth() throws IOException {
        if (currentUser == null) {
            sendReply(FTPStatusCode.NOT_LOGGED_IN, "Please login with USER and PASS first");
            return false;
        }
        return true;
    }

    public void authenticate(User user) {
        this.currentUser = user;
        this.fileSystem = new ChrootFileSystemHandler(user.getHomeDirectory());
        ServerEventBus.getInstance().postConnectionEvent(new ConnectionEvent(
                ConnectionEvent.Type.AUTHENTICATED, sessionId, clientIp, clientPort,
                user.getUsername(), connectTime, fileSystem.getVirtualPath(), activityStatus
        ));
    }

    public PassiveDataServer createPassiveDataServer() throws IOException {
        clearPassiveDataServer();
        this.passiveDataServer = new PassiveDataServer(socket.getLocalAddress());
        return this.passiveDataServer;
    }

    public PassiveDataServer getPassiveDataServer() {
        return passiveDataServer;
    }

    public void clearPassiveDataServer() {
        if (passiveDataServer != null) {
            passiveDataServer.closeServerSocket();
            passiveDataServer = null;
        }
    }

    public void updateCurrentDirectory(String newPath) {
        if (currentUser != null) {
            ServerEventBus.getInstance().postConnectionEvent(new ConnectionEvent(
                    ConnectionEvent.Type.AUTHENTICATED, sessionId, clientIp, clientPort,
                    currentUser.getUsername(), connectTime, newPath, activityStatus
            ));
        }
    }

    public void setActivityStatus(String status) {
        this.activityStatus = status;
        if (currentUser != null) {
            ServerEventBus.getInstance().postConnectionEvent(new ConnectionEvent(
                    ConnectionEvent.Type.AUTHENTICATED, sessionId, clientIp, clientPort,
                    currentUser.getUsername(), connectTime, fileSystem != null ? fileSystem.getVirtualPath() : "/", activityStatus
            ));
        }
    }

    public void kick() {
        try {
            sendReply(FTPStatusCode.SERVICE_CLOSING_CONTROL, "You have been disconnected by the server administrator");
        } catch (Exception ignored) {}
        close();
        ServerEventBus.getInstance().postConnectionEvent(new ConnectionEvent(
                ConnectionEvent.Type.KICKED, sessionId, clientIp, clientPort,
                getUsername(), connectTime, "/", "Kicked"
        ));
    }

    public void close() {
        running = false;
        clearPassiveDataServer();
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}

        server.removeClient(this);

        ServerEventBus.getInstance().postConnectionEvent(new ConnectionEvent(
                ConnectionEvent.Type.DISCONNECTED, sessionId, clientIp, clientPort,
                getUsername(), connectTime, "/", "Disconnected"
        ));
    }

    // Getters & Setters
    public String getSessionId() {
        return sessionId;
    }

    public String getClientIp() {
        return clientIp;
    }

    public int getClientPort() {
        return clientPort;
    }

    public LocalDateTime getConnectTime() {
        return connectTime;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public boolean isAuthenticated() {
        return currentUser != null;
    }

    public String getUsername() {
        return currentUser != null ? currentUser.getUsername() : (pendingUsername != null ? pendingUsername : "anonymous");
    }

    public String getPendingUsername() {
        return pendingUsername;
    }

    public void setPendingUsername(String pendingUsername) {
        this.pendingUsername = pendingUsername;
    }

    public ChrootFileSystemHandler getFileSystem() {
        return fileSystem;
    }

    public String getActivityStatus() {
        return activityStatus;
    }
}
