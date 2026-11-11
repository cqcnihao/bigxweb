package com.cqcnihao.bigx.business.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.repository.IRepository;
import com.cqcnihao.bigx.business.dto.OrderDTO;
import com.cqcnihao.bigx.business.entity.BizOrder;

public interface BizOrderService extends IRepository<BizOrder> {

    IPage<BizOrder> page(long current, long size, String keyword, Integer status);

    BizOrder create(OrderDTO dto);

    BizOrder update(OrderDTO dto);

    BizOrder confirm(Long id);

    void cancel(Long id);

    void delete(Long id);
}