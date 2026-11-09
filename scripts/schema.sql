-- ====================================================================
-- FTPproto - Database Schema for PostgreSQL & MySQL
-- ====================================================================

-- 1. Create table `users`
CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    salt VARCHAR(64) NOT NULL,
    home_directory VARCHAR(255) NOT NULL,
    can_read BOOLEAN DEFAULT TRUE NOT NULL,
    can_write BOOLEAN DEFAULT TRUE NOT NULL,
    can_delete BOOLEAN DEFAULT TRUE NOT NULL,
    is_active BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- 2. Create table `activity_logs`
CREATE TABLE IF NOT EXISTS activity_logs (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50),
    action VARCHAR(30) NOT NULL,
    file_path VARCHAR(500),
    file_size BIGINT DEFAULT 0,
    client_ip VARCHAR(50),
    status VARCHAR(20) NOT NULL,
    details TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Index for searching activity logs
CREATE INDEX IF NOT EXISTS idx_activity_logs_username ON activity_logs(username);
CREATE INDEX IF NOT EXISTS idx_activity_logs_created_at ON activity_logs(created_at DESC);

-- ====================================================================
-- Sample Initial Accounts
-- Password for 'admin' is 'admin123' (Salt: 16-byte hex, SHA-256 with salt)
-- Password for 'user1' is 'user123'
-- Password hash calculation: SHA256(password + salt)
-- ====================================================================
INSERT INTO users (username, password_hash, salt, home_directory, can_read, can_write, can_delete, is_active)
VALUES 
    ('admin', '15f875f1d60da00c6e7ed4ef0338bf6ead42e5e0ccd50fd0f47fd9443adb5094', 'd9a3b6e8f1c4e2a5', 'data/server_root/admin', TRUE, TRUE, TRUE, TRUE),
    ('user1', 'bd06898bab9ca511671de75f675416e42789be4d31c7135a9f706847e1fff52a', 'a1b2c3d4e5f60718', 'data/server_root/user1', TRUE, TRUE, FALSE, TRUE)
ON CONFLICT (username) DO UPDATE 
SET password_hash = EXCLUDED.password_hash, salt = EXCLUDED.salt;
