package com.ftpproto.server.command;

import com.ftpproto.common.model.ActivityLog;
import com.ftpproto.common.model.FTPFileItem;
import com.ftpproto.common.model.User;
import com.ftpproto.common.protocol.FTPStatusCode;
import com.ftpproto.common.util.FormatUtils;
import com.ftpproto.common.util.PasswordHasher;
import com.ftpproto.server.core.FTPClientHandler;
import com.ftpproto.server.core.PassiveDataServer;
import com.ftpproto.server.dao.ActivityLogDAO;
import com.ftpproto.server.dao.UserDAO;
import com.ftpproto.server.event.ServerEventBus;
import com.ftpproto.server.event.TransferEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Đăng ký và điều phối toàn bộ các lệnh FTP chuẩn RFC 959.
 */
public class CommandRegistry {
    private static final Logger logger = LoggerFactory.getLogger(CommandRegistry.class);

    private final Map<String, FTPCommandHandler> commands = new HashMap<>();
    private final UserDAO userDAO;
    private final ActivityLogDAO activityLogDAO;

    public CommandRegistry(UserDAO userDAO, ActivityLogDAO activityLogDAO) {
        this.userDAO = userDAO;
        this.activityLogDAO = activityLogDAO;
        registerDefaultCommands();
    }

    public FTPCommandHandler getHandler(String commandName) {
        if (commandName == null) return null;
        return commands.get(commandName.toUpperCase(Locale.ROOT));
    }

    private void registerDefaultCommands() {
        commands.put("USER", this::handleUser);
        commands.put("PASS", this::handlePass);
        commands.put("PWD", this::handlePwd);
        commands.put("XPWD", this::handlePwd);
        commands.put("CWD", this::handleCwd);
        commands.put("XCWD", this::handleCwd);
        commands.put("CDUP", this::handleCdup);
        commands.put("XCUP", this::handleCdup);
        commands.put("PASV", this::handlePasv);
        commands.put("EPSV", this::handleEpsv);
        commands.put("LIST", this::handleList);
        commands.put("NLST", this::handleList);
        commands.put("RETR", this::handleRetr);
        commands.put("STOR", this::handleStor);
        commands.put("DELE", this::handleDele);
        commands.put("MKD", this::handleMkd);
        commands.put("XMKD", this::handleMkd);
        commands.put("RMD", this::handleRmd);
        commands.put("XRMD", this::handleRmd);
        commands.put("SIZE", this::handleSize);
        commands.put("SYST", this::handleSyst);
        commands.put("TYPE", this::handleType);
        commands.put("NOOP", this::handleNoop);
        commands.put("FEAT", this::handleFeat);
        commands.put("QUIT", this::handleQuit);
    }

    // -------------------------------------------------------------
    // USER & PASS: Xác thực tài khoản
    // -------------------------------------------------------------
    private void handleUser(FTPClientHandler client, String arg) throws IOException {
        if (arg == null || arg.trim().isEmpty()) {
            client.sendReply(FTPStatusCode.SYNTAX_ERROR_IN_PARAMETERS, "Username required");
            return;
        }
        String username = arg.trim();
        client.setPendingUsername(username);
        client.sendReply(FTPStatusCode.USER_NAME_OK_NEED_PASSWORD, "Password required for " + username);
    }

    private void handlePass(FTPClientHandler client, String password) throws IOException {
        String username = client.getPendingUsername();
        if (username == null || username.isEmpty()) {
            client.sendReply(FTPStatusCode.BAD_SEQUENCE_OF_COMMANDS, "Login with USER first");
            return;
        }
        if (password == null) {
            password = "";
        }

        Optional<User> optUser = userDAO.findByUsername(username);
        if (optUser.isEmpty()) {
            logger.warn("Đăng nhập thất bại: Tài khoản '{}' không tồn tại (IP: {})", username, client.getClientIp());
            activityLogDAO.log(new ActivityLog(username, "FAILED_LOGIN", null, 0, client.getClientIp(), "FAILED", "Tài khoản không tồn tại"));
            client.sendReply(FTPStatusCode.NOT_LOGGED_IN, "Authentication failed");
            return;
        }

        User user = optUser.get();
        if (!user.isActive()) {
            logger.warn("Đăng nhập thất bại: Tài khoản '{}' đã bị khóa (IP: {})", username, client.getClientIp());
            activityLogDAO.log(new ActivityLog(username, "FAILED_LOGIN", null, 0, client.getClientIp(), "FAILED", "Tài khoản bị khóa"));
            client.sendReply(FTPStatusCode.NOT_LOGGED_IN, "Account is disabled");
            return;
        }

        boolean valid = PasswordHasher.verify(password, user.getSalt(), user.getPasswordHash());
        if (!valid) {
            logger.warn("Đăng nhập thất bại: Sai mật khẩu cho tài khoản '{}' (IP: {})", username, client.getClientIp());
            activityLogDAO.log(new ActivityLog(username, "FAILED_LOGIN", null, 0, client.getClientIp(), "FAILED", "Sai mật khẩu"));
            client.sendReply(FTPStatusCode.NOT_LOGGED_IN, "Authentication failed");
            return;
        }

        // Đăng nhập thành công!
        client.authenticate(user);
        logger.info("Tài khoản '{}' đăng nhập thành công từ IP: {}", username, client.getClientIp());
        activityLogDAO.log(new ActivityLog(username, "LOGIN", null, 0, client.getClientIp(), "SUCCESS", "Đăng nhập thành công"));
        client.sendReply(FTPStatusCode.USER_LOGGED_IN, "User logged in, proceed");
    }

    // -------------------------------------------------------------
    // Điều hướng thư mục: PWD, CWD, CDUP
    // -------------------------------------------------------------
    private void handlePwd(FTPClientHandler client, String arg) throws IOException {
        if (!client.requireAuth()) return;
        String vPath = client.getFileSystem().getVirtualPath();
        client.sendReply(FTPStatusCode.PATHNAME_CREATED, "\"" + vPath + "\" is current directory");
    }

    private void handleCwd(FTPClientHandler client, String dir) throws IOException {
        if (!client.requireAuth()) return;
        if (dir == null || dir.trim().isEmpty()) {
            client.sendReply(FTPStatusCode.SYNTAX_ERROR_IN_PARAMETERS, "Directory required");
            return;
        }
        boolean ok = client.getFileSystem().changeDirectory(dir);
        if (ok) {
            client.updateCurrentDirectory(client.getFileSystem().getVirtualPath());
            client.sendReply(FTPStatusCode.FILE_ACTION_COMPLETED, "Directory successfully changed to \"" + client.getFileSystem().getVirtualPath() + "\"");
        } else {
            client.sendReply(FTPStatusCode.FILE_UNAVAILABLE_OR_NOT_FOUND, "Failed to change directory");
        }
    }

    private void handleCdup(FTPClientHandler client, String arg) throws IOException {
        if (!client.requireAuth()) return;
        boolean ok = client.getFileSystem().changeToParent();
        if (ok) {
            client.updateCurrentDirectory(client.getFileSystem().getVirtualPath());
            client.sendReply(FTPStatusCode.FILE_ACTION_COMPLETED, "Directory successfully changed to \"" + client.getFileSystem().getVirtualPath() + "\"");
        } else {
            client.sendReply(FTPStatusCode.FILE_UNAVAILABLE_OR_NOT_FOUND, "Failed to change directory");
        }
    }

    // -------------------------------------------------------------
    // Passive Data Connection: PASV, EPSV
    // -------------------------------------------------------------
    private void handlePasv(FTPClientHandler client, String arg) throws IOException {
        if (!client.requireAuth()) return;
        PassiveDataServer pds = client.createPassiveDataServer();
        client.sendReply(FTPStatusCode.ENTERING_PASSIVE_MODE, "Entering Passive Mode " + pds.getPasvResponseString());
    }

    private void handleEpsv(FTPClientHandler client, String arg) throws IOException {
        if (!client.requireAuth()) return;
        PassiveDataServer pds = client.createPassiveDataServer();
        client.sendReply(229, "Entering Extended Passive Mode (|||" + pds.getPort() + "|)");
    }

    // -------------------------------------------------------------
    // Danh sách tệp: LIST
    // -------------------------------------------------------------
    private void handleList(FTPClientHandler client, String pathArg) throws IOException {
        if (!client.requireAuth()) return;
        if (!client.getCurrentUser().isCanRead()) {
            client.sendReply(FTPStatusCode.FILE_UNAVAILABLE_OR_NOT_FOUND, "Permission denied (Read permission required)");
            return;
        }

        PassiveDataServer pds = client.getPassiveDataServer();
        if (pds == null) {
            client.sendReply(FTPStatusCode.CANNOT_OPEN_DATA_CONNECTION, "Use PASV first");
            return;
        }

        // Bỏ cờ "-a", "-l", v.v... nếu client gửi tham số kèm theo
        if (pathArg != null && pathArg.startsWith("-")) {
            int spaceIdx = pathArg.indexOf(' ');
            pathArg = spaceIdx != -1 ? pathArg.substring(spaceIdx + 1).trim() : null;
        }

        client.sendReply(FTPStatusCode.FILE_STATUS_OK_OPENING_DATA_CONNECTION, "Here comes the directory listing");

        try (Socket dataSocket = pds.acceptDataSocket();
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(dataSocket.getOutputStream(), StandardCharsets.UTF_8))) {
            
            List<FTPFileItem> items = client.getFileSystem().listFiles(pathArg);
            for (FTPFileItem item : items) {
                writer.write(FormatUtils.formatUnixListLine(item));
            }
            writer.flush();
            client.sendReply(FTPStatusCode.CLOSING_DATA_CONNECTION, "Directory send OK");
        } catch (Exception e) {
            logger.error("Lỗi khi truyền dữ liệu LIST cho client: {}", client.getClientIp(), e);
            client.sendReply(FTPStatusCode.ACTION_ABORTED_LOCAL_ERROR, "Error reading directory listing");
        } finally {
            client.clearPassiveDataServer();
        }
    }

    // -------------------------------------------------------------
    // Tải về: RETR (Download)
    // -------------------------------------------------------------
    private void handleRetr(FTPClientHandler client, String fileName) throws IOException {
        if (!client.requireAuth()) return;
        if (!client.getCurrentUser().isCanRead()) {
            client.sendReply(FTPStatusCode.FILE_UNAVAILABLE_OR_NOT_FOUND, "Permission denied (Read permission required)");
            return;
        }
        if (fileName == null || fileName.trim().isEmpty()) {
            client.sendReply(FTPStatusCode.SYNTAX_ERROR_IN_PARAMETERS, "Filename required");
            return;
        }

        File targetFile = client.getFileSystem().resolvePhysical(fileName);
        if (targetFile == null || !targetFile.exists() || !targetFile.isFile()) {
            client.sendReply(FTPStatusCode.FILE_UNAVAILABLE_OR_NOT_FOUND, "File not found");
            return;
        }

        PassiveDataServer pds = client.getPassiveDataServer();
        if (pds == null) {
            client.sendReply(FTPStatusCode.CANNOT_OPEN_DATA_CONNECTION, "Use PASV first");
            return;
        }

        long fileSize = targetFile.length();
        client.setActivityStatus("Downloading: " + targetFile.getName());
        client.sendReply(FTPStatusCode.FILE_STATUS_OK_OPENING_DATA_CONNECTION,
                "Opening BINARY mode data connection for " + targetFile.getName() + " (" + fileSize + " bytes)");

        ServerEventBus.getInstance().postTransferEvent(new TransferEvent(
                TransferEvent.Type.START, client.getClientIp(), client.getUsername(),
                "DOWNLOAD", targetFile.getName(), 0, fileSize, null
        ));

        long bytesTransferred = 0;
        try (Socket dataSocket = pds.acceptDataSocket();
             InputStream fis = new BufferedInputStream(new FileInputStream(targetFile));
             OutputStream os = new BufferedOutputStream(dataSocket.getOutputStream())) {

            byte[] buffer = new byte[16384]; // 16KB buffer
            int read;
            long lastReportTime = System.currentTimeMillis();

            while ((read = fis.read(buffer)) != -1) {
                os.write(buffer, 0, read);
                bytesTransferred += read;

                long now = System.currentTimeMillis();
                if (now - lastReportTime >= 500 || bytesTransferred == fileSize) {
                    ServerEventBus.getInstance().postTransferEvent(new TransferEvent(
                            TransferEvent.Type.PROGRESS, client.getClientIp(), client.getUsername(),
                            "DOWNLOAD", targetFile.getName(), bytesTransferred, fileSize, null
                    ));
                    lastReportTime = now;
                }
            }
            os.flush();

            client.sendReply(FTPStatusCode.CLOSING_DATA_CONNECTION, "Transfer complete");
            activityLogDAO.log(new ActivityLog(client.getUsername(), "DOWNLOAD", targetFile.getAbsolutePath(),
                    fileSize, client.getClientIp(), "SUCCESS", "Đã tải về thành công"));

            ServerEventBus.getInstance().postTransferEvent(new TransferEvent(
                    TransferEvent.Type.COMPLETED, client.getClientIp(), client.getUsername(),
                    "DOWNLOAD", targetFile.getName(), bytesTransferred, fileSize, null
            ));
        } catch (Exception e) {
            logger.error("Lỗi khi tải file RETR: {}", fileName, e);
            client.sendReply(FTPStatusCode.ACTION_ABORTED_LOCAL_ERROR, "Transfer aborted");
            activityLogDAO.log(new ActivityLog(client.getUsername(), "DOWNLOAD", targetFile.getAbsolutePath(),
                    bytesTransferred, client.getClientIp(), "FAILED", e.getMessage()));
            ServerEventBus.getInstance().postTransferEvent(new TransferEvent(
                    TransferEvent.Type.FAILED, client.getClientIp(), client.getUsername(),
                    "DOWNLOAD", targetFile.getName(), bytesTransferred, fileSize, e.getMessage()
            ));
        } finally {
            client.clearPassiveDataServer();
            client.setActivityStatus("Idle");
        }
    }

    // -------------------------------------------------------------
    // Tải lên: STOR (Upload)
    // -------------------------------------------------------------
    private void handleStor(FTPClientHandler client, String fileName) throws IOException {
        if (!client.requireAuth()) return;
        if (!client.getCurrentUser().isCanWrite()) {
            client.sendReply(FTPStatusCode.FILE_UNAVAILABLE_OR_NOT_FOUND, "Permission denied (Write permission required)");
            return;
        }
        if (fileName == null || fileName.trim().isEmpty()) {
            client.sendReply(FTPStatusCode.SYNTAX_ERROR_IN_PARAMETERS, "Filename required");
            return;
        }

        File targetFile = client.getFileSystem().resolvePhysical(fileName);
        if (targetFile == null) {
            client.sendReply(FTPStatusCode.FILE_NAME_NOT_ALLOWED, "Invalid path or access denied");
            return;
        }

        PassiveDataServer pds = client.getPassiveDataServer();
        if (pds == null) {
            client.sendReply(FTPStatusCode.CANNOT_OPEN_DATA_CONNECTION, "Use PASV first");
            return;
        }

        client.setActivityStatus("Uploading: " + targetFile.getName());
        client.sendReply(FTPStatusCode.FILE_STATUS_OK_OPENING_DATA_CONNECTION, "Ok to send data");

        ServerEventBus.getInstance().postTransferEvent(new TransferEvent(
                TransferEvent.Type.START, client.getClientIp(), client.getUsername(),
                "UPLOAD", targetFile.getName(), 0, 0, null
        ));

        long bytesTransferred = 0;
        try (Socket dataSocket = pds.acceptDataSocket();
             InputStream is = new BufferedInputStream(dataSocket.getInputStream());
             OutputStream fos = new BufferedOutputStream(new FileOutputStream(targetFile))) {

            byte[] buffer = new byte[16384]; // 16KB buffer
            int read;
            long lastReportTime = System.currentTimeMillis();

            while ((read = is.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
                bytesTransferred += read;

                long now = System.currentTimeMillis();
                if (now - lastReportTime >= 500) {
                    ServerEventBus.getInstance().postTransferEvent(new TransferEvent(
                            TransferEvent.Type.PROGRESS, client.getClientIp(), client.getUsername(),
                            "UPLOAD", targetFile.getName(), bytesTransferred, 0, null
                    ));
                    lastReportTime = now;
                }
            }
            fos.flush();

            client.sendReply(FTPStatusCode.CLOSING_DATA_CONNECTION, "Transfer complete");
            activityLogDAO.log(new ActivityLog(client.getUsername(), "UPLOAD", targetFile.getAbsolutePath(),
                    bytesTransferred, client.getClientIp(), "SUCCESS", "Đã tải lên thành công"));

            ServerEventBus.getInstance().postTransferEvent(new TransferEvent(
                    TransferEvent.Type.COMPLETED, client.getClientIp(), client.getUsername(),
                    "UPLOAD", targetFile.getName(), bytesTransferred, bytesTransferred, null
            ));
        } catch (Exception e) {
            logger.error("Lỗi khi nhận file STOR: {}", fileName, e);
            client.sendReply(FTPStatusCode.ACTION_ABORTED_LOCAL_ERROR, "Transfer aborted");
            activityLogDAO.log(new ActivityLog(client.getUsername(), "UPLOAD", targetFile.getAbsolutePath(),
                    bytesTransferred, client.getClientIp(), "FAILED", e.getMessage()));
            ServerEventBus.getInstance().postTransferEvent(new TransferEvent(
                    TransferEvent.Type.FAILED, client.getClientIp(), client.getUsername(),
                    "UPLOAD", targetFile.getName(), bytesTransferred, 0, e.getMessage()
            ));
        } finally {
            client.clearPassiveDataServer();
            client.setActivityStatus("Idle");
        }
    }

    // -------------------------------------------------------------
    // Thao tác tệp và thư mục: DELE, MKD, RMD, SIZE
    // -------------------------------------------------------------
    private void handleDele(FTPClientHandler client, String fileName) throws IOException {
        if (!client.requireAuth()) return;
        if (!client.getCurrentUser().isCanDelete()) {
            client.sendReply(FTPStatusCode.FILE_UNAVAILABLE_OR_NOT_FOUND, "Permission denied (Delete permission required)");
            return;
        }
        boolean ok = client.getFileSystem().deleteFile(fileName);
        if (ok) {
            client.sendReply(FTPStatusCode.FILE_ACTION_COMPLETED, "Delete operation successful");
            activityLogDAO.log(new ActivityLog(client.getUsername(), "DELETE", fileName, 0, client.getClientIp(), "SUCCESS", "Đã xóa file"));
        } else {
            client.sendReply(FTPStatusCode.FILE_UNAVAILABLE_OR_NOT_FOUND, "Delete failed or file does not exist");
        }
    }

    private void handleMkd(FTPClientHandler client, String dirName) throws IOException {
        if (!client.requireAuth()) return;
        if (!client.getCurrentUser().isCanWrite()) {
            client.sendReply(FTPStatusCode.FILE_UNAVAILABLE_OR_NOT_FOUND, "Permission denied (Write permission required)");
            return;
        }
        boolean ok = client.getFileSystem().makeDirectory(dirName);
        if (ok) {
            client.sendReply(FTPStatusCode.PATHNAME_CREATED, "\"" + dirName + "\" created");
            activityLogDAO.log(new ActivityLog(client.getUsername(), "MKDIR", dirName, 0, client.getClientIp(), "SUCCESS", "Đã tạo thư mục"));
        } else {
            client.sendReply(FTPStatusCode.FILE_UNAVAILABLE_OR_NOT_FOUND, "Create directory failed");
        }
    }

    private void handleRmd(FTPClientHandler client, String dirName) throws IOException {
        if (!client.requireAuth()) return;
        if (!client.getCurrentUser().isCanDelete()) {
            client.sendReply(FTPStatusCode.FILE_UNAVAILABLE_OR_NOT_FOUND, "Permission denied (Delete permission required)");
            return;
        }
        boolean ok = client.getFileSystem().removeDirectory(dirName);
        if (ok) {
            client.sendReply(FTPStatusCode.FILE_ACTION_COMPLETED, "Remove directory operation successful");
            activityLogDAO.log(new ActivityLog(client.getUsername(), "RMDIR", dirName, 0, client.getClientIp(), "SUCCESS", "Đã xóa thư mục"));
        } else {
            client.sendReply(FTPStatusCode.FILE_UNAVAILABLE_OR_NOT_FOUND, "Remove directory failed (must be empty)");
        }
    }

    private void handleSize(FTPClientHandler client, String fileName) throws IOException {
        if (!client.requireAuth()) return;
        long size = client.getFileSystem().getFileSize(fileName);
        if (size >= 0) {
            client.sendReply(FTPStatusCode.FILE_STATUS, String.valueOf(size));
        } else {
            client.sendReply(FTPStatusCode.FILE_UNAVAILABLE_OR_NOT_FOUND, "Could not get file size");
        }
    }

    // -------------------------------------------------------------
    // Lệnh bổ trợ: SYST, TYPE, NOOP, FEAT, QUIT
    // -------------------------------------------------------------
    private void handleSyst(FTPClientHandler client, String arg) throws IOException {
        client.sendReply(FTPStatusCode.SYSTEM_NAME, "UNIX Type: L8");
    }

    private void handleType(FTPClientHandler client, String type) throws IOException {
        if (type != null && type.equalsIgnoreCase("A")) {
            client.sendReply(FTPStatusCode.COMMAND_OK, "Type set to A (ASCII)");
        } else {
            client.sendReply(FTPStatusCode.COMMAND_OK, "Type set to I (Binary)");
        }
    }

    private void handleNoop(FTPClientHandler client, String arg) throws IOException {
        client.sendReply(FTPStatusCode.COMMAND_OK, "NOOP ok");
    }

    private void handleFeat(FTPClientHandler client, String arg) throws IOException {
        client.sendRawLine("211-Features:\r\n PASV\r\n EPSV\r\n UTF8\r\n SIZE\r\n211 End\r\n");
    }

    private void handleQuit(FTPClientHandler client, String arg) throws IOException {
        client.sendReply(FTPStatusCode.SERVICE_CLOSING_CONTROL, "Goodbye");
        if (client.isAuthenticated()) {
            activityLogDAO.log(new ActivityLog(client.getUsername(), "LOGOUT", null, 0, client.getClientIp(), "SUCCESS", "Client ngắt kết nối"));
        }
        client.close();
    }
}
