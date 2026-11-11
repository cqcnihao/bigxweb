CREATE TABLE biz_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64)  NOT NULL,
    customer_name VARCHAR(128) NOT NULL,
    total_amount DECIMAL(18,2) DEFAULT 0,
    item_count INT DEFAULT 0,
    remark VARCHAR(1024),
    status TINYINT DEFAULT 0,
    pending_data TEXT,
    create_by BIGINT,
    create_time DATETIME,
    update_time DATETIME,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;