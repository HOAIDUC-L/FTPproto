package com.ftpproto.server.command;

import com.ftpproto.server.core.FTPClientHandler;

import java.io.IOException;

/**
 * Interface Command đại diện cho logic xử lý của từng lệnh FTP.
 */
@FunctionalInterface
public interface FTPCommandHandler {
    /**
     * Thực thi lệnh FTP từ client gửi lên.
     *
     * @param client   Phiên làm việc của Client
     * @param argument Tham số đi kèm lệnh (hoặc null/rỗng nếu không có)
     */
    void execute(FTPClientHandler client, String argument) throws IOException;
}
