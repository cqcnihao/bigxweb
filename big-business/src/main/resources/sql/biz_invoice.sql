CREATE TABLE biz_invoice (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_no VARCHAR(64) NOT NULL,
    order_no VARCHAR(64),
    customer_name VARCHAR(128) NOT NULL,
    invoice_amount DECIMAL(18,2) DEFAULT 0,
    tax_rate DECIMAL(5,2) DEFAULT 0,
    tax_amount DECIMAL(18,2) DEFAULT 0,
    total_amount DECIMAL(18,2) DEFAULT 0,
    invoice_type VARCHAR(32),
    remark VARCHAR(1024),
    status TINYINT DEFAULT 0,
    pending_data TEXT,
    create_by BIGINT,
    create_time DATETIME,
    update_time DATETIME,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_invoice_no (invoice_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;