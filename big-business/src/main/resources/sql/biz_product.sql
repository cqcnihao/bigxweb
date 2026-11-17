CREATE TABLE biz_product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_name VARCHAR(128) NOT NULL,
    product_code VARCHAR(64)  NOT NULL,
    price DECIMAL(18,2) DEFAULT 0,
    stock INT DEFAULT 0,
    category VARCHAR(64),
    description VARCHAR(1024),
    status TINYINT NOT NULL DEFAULT 1 COMMENT '两步确认状态：0=新增待确认，1=已生效，2=修改待确认，3=删除待确认',
    pending_data TEXT NULL COMMENT '待确认变更快照（JSON），确认后应用并清空',
    create_by BIGINT,
    create_time DATETIME,
    update_time DATETIME,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_product_code (product_code),
    INDEX idx_biz_product_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';