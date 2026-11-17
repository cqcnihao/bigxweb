CREATE TABLE sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password VARCHAR(128) NOT NULL COMMENT 'BCrypt 加密后的密码',
    nickname VARCHAR(64),
    email VARCHAR(128),
    phone VARCHAR(32),
    avatar VARCHAR(256),
    status TINYINT DEFAULT 1 COMMENT '1=启用，0=禁用',
    last_login_time DATETIME,
    last_login_ip VARCHAR(64),
    create_time DATETIME,
    update_time DATETIME,
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

CREATE TABLE sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(64) NOT NULL,
    description VARCHAR(256),
    status TINYINT DEFAULT 1,
    create_time DATETIME,
    update_time DATETIME,
    UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

CREATE TABLE sys_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    permission_code VARCHAR(128) NOT NULL,
    permission_name VARCHAR(128) NOT NULL,
    description VARCHAR(256),
    status TINYINT DEFAULT 1,
    create_time DATETIME,
    update_time DATETIME,
    UNIQUE KEY uk_permission_code (permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

CREATE TABLE sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    create_time DATETIME,
    UNIQUE KEY uk_user_role (user_id, role_code),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色关联表';