package com.cqcnihao.bigx.business.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.repository.AbstractRepository;
import com.cqcnihao.bigx.business.dto.InvoiceDTO;
import com.cqcnihao.bigx.business.entity.BizInvoice;
import com.cqcnihao.bigx.business.mapper.BizInvoiceMapper;
import com.cqcnihao.bigx.business.service.BizInvoiceService;
import com.cqcnihao.bigx.core.exception.BusinessException;
import com.cqcnihao.bigx.license.LicenseSignService;
import com.cqcnihao.bigx.license.annotation.Sign;
import com.cqcnihao.bigx.license.annotation.Verify;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Objects;

@Slf4j
@Service
public class BizInvoiceServiceImpl extends AbstractRepository<BizInvoiceMapper, BizInvoice>
        implements BizInvoiceService {

    private final BizInvoiceMapper baseMapper;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private LicenseSignService signService;

    public BizInvoiceServiceImpl(BizInvoiceMapper baseMapper, ObjectMapper objectMapper) {
        this.baseMapper = baseMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public BizInvoiceMapper getBaseMapper() {
        return baseMapper;
    }

    @Override
    public boolean saveBatch(java.util.Collection<BizInvoice> entityList, int batchSize) {
        entityList.forEach(this::save);
        return true;
    }

    @Override
    public boolean saveOrUpdateBatch(java.util.Collection<BizInvoice> entityList, int batchSize) {
        entityList.forEach(this::saveOrUpdate);
        return true;
    }

    @Override
    public boolean updateBatchById(java.util.Collection<BizInvoice> entityList, int batchSize) {
        entityList.forEach(this::updateById);
        return true;
    }

    @Override
    public IPage<BizInvoice> page(long current, long size, String keyword, Integer status) {
        Page<BizInvoice> page = new Page<>(current, size);
        LambdaQueryWrapper<BizInvoice> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                    .like(BizInvoice::getInvoiceNo, keyword)
                    .or().like(BizInvoice::getOrderNo, keyword)
                    .or().like(BizInvoice::getCustomerName, keyword));
        }
        if (status != null) {
            wrapper.eq(BizInvoice::getStatus, status);
        }
        wrapper.orderByDesc(BizInvoice::getUpdateTime);
        return this.page(page, wrapper);
    }

    @Override
    @Transactional
    @Sign(storePayload = true)
    public BizInvoice create(InvoiceDTO dto) {
        BizInvoice invoice = new BizInvoice();
        BeanUtils.copyProperties(dto, invoice);
        invoice.setStatus(BizInvoice.STATUS_DRAFT_CREATE);
        invoice.setCreateBy(StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null);
        invoice.setCreateTime(LocalDateTime.now());
        invoice.setUpdateTime(LocalDateTime.now());
        invoice.setDeleted(0);
        this.save(invoice);
        return invoice;
    }

    @Override
    @Transactional
    @Sign(storePayload = true)
    public BizInvoice update(InvoiceDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("400", "update requires id");
        }
        BizInvoice exist = this.getById(dto.getId());
        if (exist == null) {
            throw new BusinessException("404", "invoice not found");
        }
        exist.setUpdateTime(LocalDateTime.now());
        if (Objects.equals(exist.getStatus(), BizInvoice.STATUS_DRAFT_CREATE)) {
            BeanUtils.copyProperties(dto, exist);
            this.updateById(exist);
            return exist;
        }
        exist.setPendingData(writePending(dto));
        exist.setStatus(BizInvoice.STATUS_PENDING_UPDATE);
        this.updateById(exist);
        return exist;
    }

    @Override
    @Transactional
    @Verify
    public BizInvoice confirm(Long id) {
        BizInvoice invoice = this.getById(id);
        if (invoice == null) {
            throw new BusinessException("404", "invoice not found");
        }
        int status = invoice.getStatus() == null ? BizInvoice.STATUS_ACTIVE : invoice.getStatus();

        switch (status) {
            case BizInvoice.STATUS_DRAFT_CREATE -> {
                invoice.setStatus(BizInvoice.STATUS_ACTIVE);
                invoice.setUpdateTime(LocalDateTime.now());
                this.updateById(invoice);
            }
            case BizInvoice.STATUS_PENDING_UPDATE -> {
                InvoiceDTO pending = readPending(invoice.getPendingData());
                if (pending == null) {
                    throw new BusinessException("500", "pending snapshot missing, resubmit update please");
                }
                BeanUtils.copyProperties(pending, invoice);
                invoice.setPendingData(null);
                invoice.setStatus(BizInvoice.STATUS_ACTIVE);
                invoice.setUpdateTime(LocalDateTime.now());
                this.updateById(invoice);
            }
            case BizInvoice.STATUS_PENDING_DELETE -> {
                if (!this.removeById(id)) {
                    throw new BusinessException("404", "invoice not found");
                }
                return invoice;
            }
            case BizInvoice.STATUS_ACTIVE ->
                    throw new BusinessException("400", "already active, no confirm needed");
            default -> throw new BusinessException("400", "unknown status: " + status);
        }
        return invoice;
    }

    @Override
    @Transactional
    public void cancel(Long id) {
        BizInvoice invoice = this.getById(id);
        if (invoice == null) {
            throw new BusinessException("404", "invoice not found");
        }
        int status = invoice.getStatus() == null ? BizInvoice.STATUS_ACTIVE : invoice.getStatus();
        switch (status) {
            case BizInvoice.STATUS_DRAFT_CREATE -> this.removeById(id);
            case BizInvoice.STATUS_PENDING_UPDATE -> {
                invoice.setPendingData(null);
                invoice.setStatus(BizInvoice.STATUS_ACTIVE);
                invoice.setUpdateTime(LocalDateTime.now());
                this.updateById(invoice);
            }
            case BizInvoice.STATUS_PENDING_DELETE -> {
                invoice.setStatus(BizInvoice.STATUS_ACTIVE);
                invoice.setUpdateTime(LocalDateTime.now());
                this.updateById(invoice);
            }
            default -> throw new BusinessException("400", "no cancel needed at current status");
        }
    }

    @Override
    @Transactional
    @Sign(storePayload = true)
    public void delete(Long id) {
        BizInvoice invoice = this.getById(id);
        if (invoice == null) {
            throw new BusinessException("404", "invoice not found");
        }
        int status = invoice.getStatus() == null ? BizInvoice.STATUS_ACTIVE : invoice.getStatus();
        switch (status) {
            case BizInvoice.STATUS_DRAFT_CREATE -> this.removeById(id);
            case BizInvoice.STATUS_ACTIVE, BizInvoice.STATUS_PENDING_UPDATE -> {
                invoice.setStatus(BizInvoice.STATUS_PENDING_DELETE);
                invoice.setUpdateTime(LocalDateTime.now());
                this.updateById(invoice);
            }
            case BizInvoice.STATUS_PENDING_DELETE ->
                    throw new BusinessException("400", "already pending delete");
            default -> throw new BusinessException("400", "unknown status: " + status);
        }
    }

    private String writePending(InvoiceDTO dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (Exception e) {
            throw new BusinessException("500", "snapshot serialize failed");
        }
    }

    private InvoiceDTO readPending(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, InvoiceDTO.class);
        } catch (Exception e) {
            return null;
        }
    }
}