package com.cqcnihao.bigx.business.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.repository.IRepository;
import com.cqcnihao.bigx.business.dto.ProductDTO;
import com.cqcnihao.bigx.business.entity.BizProduct;

public interface BizProductService extends IRepository<BizProduct> {

    IPage<BizProduct> page(long current, long size, String keyword);

    BizProduct create(ProductDTO dto);

    BizProduct update(ProductDTO dto);

    void delete(Long id);
}