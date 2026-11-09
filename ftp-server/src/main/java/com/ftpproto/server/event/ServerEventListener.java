package com.ftpproto.server.event;

/**
 * Interface cho các thành phần lắng nghe sự kiện của Server (ví dụ: GUI Dashboard).
 */
public interface ServerEventListener {
    default void onConnectionEvent(ConnectionEvent event) {}
    default void onLogEvent(LogEvent event) {}
    default void onTransferEvent(TransferEvent event) {}
    default void onServerStateChanged(boolean running, int port, String rootDir) {}
}
