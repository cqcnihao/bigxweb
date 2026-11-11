package com.cqcnihao.bigx.business.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.repository.IRepository;
import com.cqcnihao.bigx.business.dto.InvoiceDTO;
import com.cqcnihao.bigx.business.entity.BizInvoice;

public interface BizInvoiceService extends IRepository<BizInvoice> {

    IPage<BizInvoice> page(long current, long size, String keyword, Integer status);

    BizInvoice create(InvoiceDTO dto);

    BizInvoice update(InvoiceDTO dto);

    BizInvoice confirm(Long id);

    void cancel(Long id);

    void delete(Long id);
}