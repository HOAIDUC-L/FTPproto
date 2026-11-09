package com.ftpproto.server.core;

import com.ftpproto.server.command.CommandRegistry;
import com.ftpproto.server.config.ServerConfig;
import com.ftpproto.server.dao.ActivityLogDAO;
import com.ftpproto.server.dao.UserDAO;
import com.ftpproto.server.event.LogEvent;
import com.ftpproto.server.event.ServerEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lõi điều khiển toàn bộ tiến trình lắng nghe mạng của FTP Server.
 */
public class FTPServer {
    private static final Logger logger = LoggerFactory.getLogger(FTPServer.class);

    private final ServerConfig config;
    private final UserDAO userDAO;
    private final ActivityLogDAO activityLogDAO;
    private final CommandRegistry commandRegistry;

    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private final List<FTPClientHandler> activeClients = new CopyOnWriteArrayList<>();
    private volatile boolean running = false;
    private Thread acceptThread;

    public FTPServer(ServerConfig config, UserDAO userDAO, ActivityLogDAO activityLogDAO) {
        this.config = config;
        this.userDAO = userDAO;
        this.activityLogDAO = activityLogDAO;
        this.commandRegistry = new CommandRegistry(userDAO, activityLogDAO);
    }

    public synchronized void start() throws IOException {
        if (running) {
            logger.warn("FTP Server đang chạy, không thể gọi start() lại.");
            return;
        }

        // Tạo thư mục gốc nếu chưa có
        File rootDir = new File(config.getRootDirectory());
        if (!rootDir.exists()) {
            rootDir.mkdirs();
        }

        this.serverSocket = new ServerSocket(config.getPort());
        this.threadPool = Executors.newCachedThreadPool();
        this.running = true;

        logger.info("FTP Server đã khởi động thành công trên cổng {}", config.getPort());
        ServerEventBus.getInstance().postLogEvent(new LogEvent("SERVER", LogEvent.Direction.SYSTEM,
                "FTP Server khởi động trên cổng " + config.getPort() + ", Root: " + config.getRootDirectory()));
        ServerEventBus.getInstance().postServerState(true, config.getPort(), config.getRootDirectory());

        acceptThread = new Thread(this::acceptLoop, "FTPServer-AcceptThread");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    private void acceptLoop() {
        while (running && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                logger.info("Chấp nhận kết nối mới từ {}:{}", clientSocket.getInetAddress(), clientSocket.getPort());

                FTPClientHandler handler = new FTPClientHandler(clientSocket, this, commandRegistry);
                activeClients.add(handler);
                threadPool.submit(handler);

            } catch (IOException e) {
                if (running) {
                    logger.error("Lỗi khi accept kết nối client: {}", e.getMessage());
                }
            }
        }
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;

        logger.info("Đang dừng FTP Server...");
        ServerEventBus.getInstance().postLogEvent(new LogEvent("SERVER", LogEvent.Direction.SYSTEM, "Đang dừng FTP Server..."));

        // Ngắt toàn bộ client đang kết nối
        for (FTPClientHandler client : activeClients) {
            client.kick();
        }
        activeClients.clear();

        // Đóng ServerSocket
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {}
        }

        // Tắt ThreadPool
        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdownNow();
        }

        ServerEventBus.getInstance().postServerState(false, config.getPort(), config.getRootDirectory());
        logger.info("FTP Server đã dừng hoàn toàn.");
    }

    public void removeClient(FTPClientHandler client) {
        activeClients.remove(client);
    }

    public boolean kickClient(String sessionId) {
        for (FTPClientHandler client : activeClients) {
            if (client.getSessionId().equals(sessionId)) {
                logger.info("Admin cưỡng chế ngắt kết nối client {} ({})", client.getUsername(), client.getClientIp());
                client.kick();
                return true;
            }
        }
        return false;
    }

    public List<FTPClientHandler> getActiveClients() {
        return Collections.unmodifiableList(new ArrayList<>(activeClients));
    }

    public boolean isRunning() {
        return running;
    }

    public ServerConfig getConfig() {
        return config;
    }
}
