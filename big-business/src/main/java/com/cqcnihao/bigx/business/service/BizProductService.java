package com.cqcnihao.bigx.business.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.repository.IRepository;
import com.cqcnihao.bigx.business.dto.ProductDTO;
import com.cqcnihao.bigx.business.entity.BizProduct;

public interface BizProductService extends IRepository<BizProduct> {

    IPage<BizProduct> page(long current, long size, String keyword, Integer status);

    /** 第一步：新增落草稿（status=0，未生效） */
    BizProduct create(ProductDTO dto);

    /** 第一步：提交修改（生效记录写 pendingData 快照，status=2；草稿直接更新字段） */
    BizProduct update(ProductDTO dto);

    /** 第二步：确认（新增待确认 → 生效；修改待确认 → 应用快照后生效） */
    BizProduct confirm(Long id);

    /** 取消待确认：草稿删除；修改待确认丢弃快照恢复生效 */
    void cancel(Long id);

    void delete(Long id);
}