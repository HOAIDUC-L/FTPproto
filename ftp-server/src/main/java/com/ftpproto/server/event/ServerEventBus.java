package com.ftpproto.server.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Event Bus trung tâm phân phối các sự kiện từ FTP Server Engine tới GUI và DAO.
 */
public class ServerEventBus {
    private static final Logger logger = LoggerFactory.getLogger(ServerEventBus.class);
    private static final ServerEventBus INSTANCE = new ServerEventBus();

    private final List<ServerEventListener> listeners = new CopyOnWriteArrayList<>();

    private ServerEventBus() {}

    public static ServerEventBus getInstance() {
        return INSTANCE;
    }

    public void register(ServerEventListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void unregister(ServerEventListener listener) {
        listeners.remove(listener);
    }

    public void postConnectionEvent(ConnectionEvent event) {
        for (ServerEventListener listener : listeners) {
            try {
                listener.onConnectionEvent(event);
            } catch (Exception e) {
                logger.error("Lỗi khi gửi ConnectionEvent tới listener", e);
            }
        }
    }

    public void postLogEvent(LogEvent event) {
        for (ServerEventListener listener : listeners) {
            try {
                listener.onLogEvent(event);
            } catch (Exception e) {
                logger.error("Lỗi khi gửi LogEvent tới listener", e);
            }
        }
    }

    public void postTransferEvent(TransferEvent event) {
        for (ServerEventListener listener : listeners) {
            try {
                listener.onTransferEvent(event);
            } catch (Exception e) {
                logger.error("Lỗi khi gửi TransferEvent tới listener", e);
            }
        }
    }

    public void postServerState(boolean running, int port, String rootDir) {
        for (ServerEventListener listener : listeners) {
            try {
                listener.onServerStateChanged(running, port, rootDir);
            } catch (Exception e) {
                logger.error("Lỗi khi gửi ServerStateChanged tới listener", e);
            }
        }
    }
}
