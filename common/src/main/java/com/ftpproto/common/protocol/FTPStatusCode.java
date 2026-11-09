package com.ftpproto.common.protocol;

/**
 * Định nghĩa các mã trạng thái phản hồi chuẩn theo RFC 959 của giao thức FTP.
 */
public final class FTPStatusCode {
    private FTPStatusCode() {}

    // 1xx: Positive Preliminary reply
    public static final int RESTART_MARKER_REPLY = 110;
    public static final int SERVICE_READY_MINUTES = 120;
    public static final int DATA_CONNECTION_ALREADY_OPEN = 125;
    public static final int FILE_STATUS_OK_OPENING_DATA_CONNECTION = 150;

    // 2xx: Positive Completion reply
    public static final int COMMAND_OK = 200;
    public static final int COMMAND_SUPERFLUOUS = 202;
    public static final int SYSTEM_STATUS = 211;
    public static final int DIRECTORY_STATUS = 212;
    public static final int FILE_STATUS = 213;
    public static final int HELP_MESSAGE = 214;
    public static final int SYSTEM_NAME = 215;
    public static final int SERVICE_READY = 220;
    public static final int SERVICE_CLOSING_CONTROL = 221;
    public static final int DATA_CONNECTION_OPEN = 225;
    public static final int CLOSING_DATA_CONNECTION = 226;
    public static final int ENTERING_PASSIVE_MODE = 227;
    public static final int USER_LOGGED_IN = 230;
    public static final int FILE_ACTION_COMPLETED = 250;
    public static final int PATHNAME_CREATED = 257;

    // 3xx: Positive Intermediate reply
    public static final int USER_NAME_OK_NEED_PASSWORD = 331;
    public static final int NEED_ACCOUNT = 332;
    public static final int FILE_ACTION_PENDING = 350;

    // 4xx: Transient Negative Completion reply
    public static final int SERVICE_NOT_AVAILABLE = 421;
    public static final int CANNOT_OPEN_DATA_CONNECTION = 425;
    public static final int CONNECTION_CLOSED_TRANSFER_ABORTED = 426;
    public static final int FILE_ACTION_NOT_TAKEN = 450;
    public static final int ACTION_ABORTED_LOCAL_ERROR = 451;
    public static final int ACTION_NOT_TAKEN_INSUFFICIENT_STORAGE = 452;

    // 5xx: Permanent Negative Completion reply
    public static final int SYNTAX_ERROR_COMMAND_UNRECOGNIZED = 500;
    public static final int SYNTAX_ERROR_IN_PARAMETERS = 501;
    public static final int COMMAND_NOT_IMPLEMENTED = 502;
    public static final int BAD_SEQUENCE_OF_COMMANDS = 503;
    public static final int COMMAND_NOT_IMPLEMENTED_FOR_PARAMETER = 504;
    public static final int NOT_LOGGED_IN = 530;
    public static final int NEED_ACCOUNT_FOR_STORING_FILES = 532;
    public static final int FILE_UNAVAILABLE_OR_NOT_FOUND = 550;
    public static final int PAGE_TYPE_UNKNOWN = 551;
    public static final int EXCEEDED_STORAGE_ALLOCATION = 552;
    public static final int FILE_NAME_NOT_ALLOWED = 553;
}
