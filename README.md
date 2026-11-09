# FTPproto - Hệ Thống Ứng Dụng Mạng FTP Client - Server Toàn Diện

Hệ thống mô phỏng giao thức truyền file FTP (File Transfer Protocol - RFC 959) hoàn chỉnh được phát triển bằng ngôn ngữ **Java (JDK 17/21)**, giao diện đồ họa hiện đại bằng **Java Swing** kết hợp Look and Feel **FlatLaf**, kết nối và lưu trữ cơ sở dữ liệu qua **PostgreSQL** (hoặc MySQL) với **HikariCP Connection Pooling**.

---

## 1. Kiến Trúc & Cấu Trúc Dự Án (Clean Architecture / MVC)

Dự án được phân tách thành mô hình **Maven Multi-Module** với các trách nhiệm độc lập:

```
FTPproto/
├── pom.xml                               # Parent POM quản lý dependencies chung
├── docker-compose.yml                    # Khởi tạo nhanh PostgreSQL bằng Docker
├── db.properties                         # Cấu hình kết nối DB (PostgreSQL / MySQL)
├── scripts/
│   └── schema.sql                        # Script DDL PostgreSQL (users, activity_logs, sample data)
├── common/                               # Module thư viện dùng chung
│   ├── pom.xml
│   └── src/main/java/com/ftpproto/common/
│       ├── model/                        # User, ActivityLog, FTPFileItem
│       ├── protocol/                     # FTPStatusCode (RFC 959), FTPReply
│       └── util/                         # PasswordHasher (Salted SHA-256), FormatUtils
├── ftp-server/                           # Module FTP Server & Server Admin Dashboard
│   ├── pom.xml
│   └── src/main/java/com/ftpproto/server/
│       ├── ServerMain.java               # Điểm khởi chạy Server GUI
│       ├── config/                       # ServerConfig, DatabaseConfig
│       ├── db/                           # HikariCP ConnectionPool, DatabaseInitializer
│       ├── dao/                          # UserDAO, ActivityLogDAO
│       ├── core/                         # FTPServer, FTPClientHandler, PassiveDataServer, ChrootFileSystemHandler
│       ├── command/                      # CommandRegistry & Command Handlers (USER, PASS, PWD, CWD, PASV, LIST, RETR, STOR, DELE, MKD, RMD, SIZE, QUIT,...)
│       ├── event/                        # ServerEventBus, ServerEventListener, ConnectionEvent, LogEvent, TransferEvent
│       └── ui/                           # FlatLaf Server Dashboard (DashboardPanel, UserManagementPanel, ActiveConnectionsPanel, ServerLogsPanel)
└── ftp-client/                           # Module FTP Client (FileZilla Style)
    ├── pom.xml
    └── src/main/java/com/ftpproto/client/
        ├── ClientMain.java               # Điểm khởi chạy Client GUI
        ├── core/                         # FTPClientEngine (Control Socket + PASV Data Socket)
        ├── event/                        # TransferProgressListener, ClientLogListener
        └── ui/                           # Giao diện FileZilla (QuickConnectBar, StatusLogPanel, LocalFilePanel, RemoteFilePanel, TransferProgressPanel)
```

---

## 2. Tính Năng Nổi Bật

### 2.1 FTP Server & Server Admin Dashboard (FlatLaf)
- **Tách biệt Control Connection & Data Connection**: 
  - Control connection lắng nghe trên port 2121 (hoặc tùy chỉnh).
  - Data connection hỗ trợ chế độ bị động **Passive Mode (PASV)** cấp phát cổng động cho từng lệnh truyền dữ liệu (LIST, RETR, STOR).
- **Đa luồng đồng thời (Concurrency)**: Sử dụng `ExecutorService` (ThreadPool) phục vụ không giới hạn client song song mà không nghẽn.
- **Bảo mật Chroot Jail**: Ngăn chặn hoàn toàn các cuộc tấn công **Path Traversal (`../`)**. Thư mục làm việc của client bị khóa chặt trong `home_directory` được phân bổ.
- **Phân quyền truy cập hạt mịn (Permissions)**: Quản lý chi tiết quyền `Read` (Download, List), `Write` (Upload, Tạo thư mục), và `Delete` (Xóa file/thư mục).
- **Server Dashboard GUI**:
  - **Tab Dashboard / Control**: Bật/Tắt Server, hiển thị IP máy chủ, Port, huy hiệu RUNNING/STOPPED, số client đang kết nối, tổng lượt tải lên/tải xuống.
  - **Tab User Management**: Xem danh sách trong `JTable`, Thêm, Sửa, Khóa/Mở khóa tài khoản, Xóa tài khoản, tự động hash mật khẩu với Salted SHA-256.
  - **Tab Active Connections**: Theo dõi theo thời gian thực IP, Port, Username, Thời gian kết nối, Thư mục hiện tại, Hoạt động (Idle, Uploading, Downloading) kèm nút **Kick Client** cưỡng chế ngắt kết nối.
  - **Tab Server Logs**: Bảng nhật ký ghi lại toàn bộ raw command từ client (màu xanh dương) và phản hồi từ server (màu xanh lá/đỏ) theo thời gian thực kèm bộ lọc.

### 2.2 FTP Client (Giao Diện FileZilla Style)
- **Quick Connect Bar**: Nhập nhanh Host, Username, Password, Port và các nút Quickconnect / Disconnect.
- **Message Log Panel**: Hiển thị chi tiết dòng log mã trạng thái FTP (220, 331, 230, 150, 226, 530,...) với mã màu trực quan.
- **Dual-Pane File Manager (2 cột song song)**:
  - Cột trái (**Local Site**): Duyệt cây thư mục và file trên máy cục bộ, nút lên thư mục cha, chuột phải Upload, Refresh, Delete, Tạo thư mục.
  - Cột phải (**Remote Site**): Duyệt thư mục và file trên FTP Server (lấy qua lệnh `LIST`), nút lên thư mục cha, chuột phải Download, Refresh, Delete, Tạo thư mục.
- **Transfer Progress Bar**: Hiển thị thanh tiến trình %, dung lượng đã truyền / tổng dung lượng, và tốc độ truyền tải thời gian thực (KB/s, MB/s).

---

## 3. Cấu Hình Cơ Sở Dữ Liệu (PostgreSQL & HikariCP)

Dự án sử dụng cơ chế **Connection Pooling** với `HikariCP` cho hiệu năng cao.

### 3.1 Khởi động Database qua Docker (Nhanh nhất)
```bash
docker-compose up -d
```

### 3.2 Hoặc sử dụng PostgreSQL có sẵn
Tạo database và áp dụng script `scripts/schema.sql`:
```bash
psql -U postgres -c "CREATE DATABASE ftpproto_db;"
psql -U postgres -d ftpproto_db -f scripts/schema.sql
```

### 3.3 File cấu hình `db.properties`
Chỉnh sửa thông tin kết nối trong file `db.properties` tại thư mục gốc:
```properties
db.url=jdbc:postgresql://localhost:5432/ftpproto_db
db.username=smartshop
db.password=smartshop_password
db.driverClassName=org.postgresql.Driver
db.maximumPoolSize=10
db.minimumIdle=2
```

> **Lưu ý**: Hệ thống tích hợp `DatabaseInitializer` tự động kiểm tra và tạo bảng nếu chưa có, đồng thời khởi tạo tài khoản quản trị mặc định:
> - **Username**: `admin`
> - **Password**: `admin123`
> - **Home Directory**: `data/server_root/admin`
> - **Quyền**: Toàn quyền (Read, Write, Delete)

---

## 4. Hướng Dẫn Biên Dịch & Chạy Ứng Dụng

### Yêu cầu môi trường
- **Java**: JDK 17 trở lên (đã kiểm thử tương thích hoàn hảo trên OpenJDK 21).
- **Maven**: 3.8+.

### Bước 1: Biên dịch và cài đặt các module vào Local Maven Repository
```bash
mvn clean install -DskipTests
```
> **Giải thích**: Trong dự án multi-module, lệnh `mvn clean install` sẽ biên dịch và cài đặt module `common` vào kho thư viện cục bộ (`~/.m2`), đồng thời đóng gói ra cả **JAR tiêu chuẩn** và **Fat JAR (jar-with-dependencies)** chứa sẵn toàn bộ thư viện cần thiết.

### Bước 2: Khởi chạy Server Admin Dashboard
**Cách 1 (Khuyên dùng)**: Chạy trực tiếp bằng Maven:
```bash
mvn exec:java -pl ftp-server
```
**Cách 2**: Chạy bằng Fat JAR độc lập:
```bash
java -jar ftp-server/target/ftp-server-1.0.0-jar-with-dependencies.jar
```

### Bước 3: Khởi chạy FTP Client (FileZilla Style)
Mở một cửa sổ dòng lệnh (terminal) khác và chạy:

**Cách 1 (Khuyên dùng)**:
```bash
mvn exec:java -pl ftp-client
```
**Cách 2**: Chạy bằng Fat JAR độc lập:
```bash
java -jar ftp-client/target/ftp-client-1.0.0-jar-with-dependencies.jar
```

---

## 5. Kịch Bản Kiểm Thử & Trải Nghiệm Hệ Thống

1. **Khởi động Server**: Trên giao diện Server Dashboard, nhấn nút **"Khởi động Server"**. Trạng thái chuyển sang huy hiệu màu xanh **RUNNING** trên cổng `2121`.
2. **Kết nối từ Client**: Mở giao diện FTP Client, kiểm tra các thông tin mặc định:
   - Host: `127.0.0.1`
   - Username: `admin`
   - Password: `admin123`
   - Port: `2121`
   - Nhấn **Quickconnect**. Khung Status Log sẽ hiển thị các mã phản hồi `220`, `331`, `230`. Cột Remote Site sẽ tự động hiển thị danh sách file trên máy chủ.
3. **Tải lên File (Upload)**: Tại cột trái (Local Site), nhấp chuột phải vào bất kỳ file nào và chọn **"Tải lên Server (Upload)"**. Thanh tiến trình phía dưới sẽ hiển thị % và tốc độ truyền; file sẽ xuất hiện ngay lập tức tại cột Remote Site.
4. **Tải về File (Download)**: Tại cột phải (Remote Site), nhấp chuột phải vào file (ví dụ `welcome.txt`) và chọn **"Tải về máy (Download)"**. File sẽ được truyền về thư mục Local hiện tại.
5. **Kiểm tra Giám sát kết nối**: Chuyển sang Server Dashboard, mở tab **"Giám sát kết nối"** để thấy client đang kết nối với trạng thái thời gian thực. Bấm nút **"Kick Client"** để kiểm tra tính năng ngắt kết nối cưỡng chế.
6. **Kiểm tra Nhật ký kiểm toán**: Mở tab **"Nhật ký hệ thống"** trên Server hoặc truy vấn bảng `activity_logs` trong PostgreSQL để xem lại toàn bộ lịch sử đăng nhập và upload/download.
