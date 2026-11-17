CREATE TABLE sys_signature (
                               id          BIGINT AUTO_INCREMENT PRIMARY KEY,
                               biz_id      VARCHAR(64)  NOT NULL COMMENT '业务主键',
                               biz_type    VARCHAR(64)  NOT NULL COMMENT '业务类型（如 Product）',
                               sign_type   VARCHAR(32)  NOT NULL DEFAULT 'SHA256withRSA' COMMENT '签名算法',
                               payload     TEXT         NULL     COMMENT '签名原文（可选存储，用于审计）',
                               signature   TEXT         NOT NULL COMMENT 'RSA 签名 Base64',
                               create_time DATETIME     NULL,
                               update_time DATETIME     NULL,
                               UNIQUE KEY uk_biz (biz_id, biz_type),
                               INDEX idx_biz_type (biz_type)
) COMMENT '独立签名表';

-- 如果用 appendHistory=true（允许同一 biz_id+biz_type 多条历史），去掉 UNIQUE KEY