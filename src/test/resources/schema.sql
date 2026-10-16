CREATE SCHEMA IF NOT EXISTS dbo;

CREATE TABLE IF NOT EXISTS dbo.role (
    id BIGINT PRIMARY KEY,
    role_code VARCHAR(30) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS dbo.app_user (
    id BIGINT PRIMARY KEY,
    user_code VARCHAR(20) NOT NULL UNIQUE,
    full_name NVARCHAR(150) NOT NULL,
    email NVARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(255),
    account_status VARCHAR(20) NOT NULL,
    last_login_at TIMESTAMP(3)
);

CREATE TABLE IF NOT EXISTS dbo.user_role (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL
);
