-- =============================================================
-- biz_product 两步确认改造（新增待确认 / 修改待确认）
-- 执行目标库：big_business
-- 日期：2026-11-09
-- =============================================================

-- status: 0=新增待确认(草稿)，1=已生效，2=修改待确认
-- 存量数据默认视为已生效
ALTER TABLE biz_product
    ADD COLUMN status       TINYINT      NOT NULL DEFAULT 1 COMMENT '两步确认状态：0=新增待确认，1=已生效，2=修改待确认' AFTER description,
    ADD COLUMN pending_data TEXT         NULL             COMMENT '修改待确认变更快照（ProductDTO JSON），确认后应用并清空' AFTER status,
    ADD INDEX idx_biz_product_status (status);
