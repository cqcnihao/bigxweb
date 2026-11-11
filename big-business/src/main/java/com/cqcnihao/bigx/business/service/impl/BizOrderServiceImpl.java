package com.cqcnihao.bigx.business.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.repository.AbstractRepository;
import com.cqcnihao.bigx.business.dto.OrderDTO;
import com.cqcnihao.bigx.business.entity.BizOrder;
import com.cqcnihao.bigx.business.mapper.BizOrderMapper;
import com.cqcnihao.bigx.business.service.BizOrderService;
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
public class BizOrderServiceImpl extends AbstractRepository<BizOrderMapper, BizOrder>
        implements BizOrderService {

    private final BizOrderMapper baseMapper;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private LicenseSignService signService;

    public BizOrderServiceImpl(BizOrderMapper baseMapper, ObjectMapper objectMapper) {
        this.baseMapper = baseMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public BizOrderMapper getBaseMapper() {
        return baseMapper;
    }

    @Override
    public boolean saveBatch(java.util.Collection<BizOrder> entityList, int batchSize) {
        entityList.forEach(this::save);
        return true;
    }

    @Override
    public boolean saveOrUpdateBatch(java.util.Collection<BizOrder> entityList, int batchSize) {
        entityList.forEach(this::saveOrUpdate);
        return true;
    }

    @Override
    public boolean updateBatchById(java.util.Collection<BizOrder> entityList, int batchSize) {
        entityList.forEach(this::updateById);
        return true;
    }

    @Override
    public IPage<BizOrder> page(long current, long size, String keyword, Integer status) {
        Page<BizOrder> page = new Page<>(current, size);
        LambdaQueryWrapper<BizOrder> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                    .like(BizOrder::getOrderNo, keyword)
                    .or().like(BizOrder::getCustomerName, keyword));
        }
        if (status != null) {
            wrapper.eq(BizOrder::getStatus, status);
        }
        wrapper.orderByDesc(BizOrder::getUpdateTime);
        return this.page(page, wrapper);
    }

    @Override
    @Transactional
    @Sign(storePayload = true)
    public BizOrder create(OrderDTO dto) {
        BizOrder order = new BizOrder();
        BeanUtils.copyProperties(dto, order);
        order.setStatus(BizOrder.STATUS_DRAFT_CREATE);
        order.setCreateBy(StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null);
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        order.setDeleted(0);
        this.save(order);
        return order;
    }

    @Override
    @Transactional
    @Sign(storePayload = true)
    public BizOrder update(OrderDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("400", "update requires id");
        }
        BizOrder exist = this.getById(dto.getId());
        if (exist == null) {
            throw new BusinessException("404", "order not found");
        }
        exist.setUpdateTime(LocalDateTime.now());
        if (Objects.equals(exist.getStatus(), BizOrder.STATUS_DRAFT_CREATE)) {
            BeanUtils.copyProperties(dto, exist);
            this.updateById(exist);
            return exist;
        }
        exist.setPendingData(writePending(dto));
        exist.setStatus(BizOrder.STATUS_PENDING_UPDATE);
        this.updateById(exist);
        return exist;
    }

    @Override
    @Transactional
    @Verify
    public BizOrder confirm(Long id) {
        BizOrder order = this.getById(id);
        if (order == null) {
            throw new BusinessException("404", "order not found");
        }
        int status = order.getStatus() == null ? BizOrder.STATUS_ACTIVE : order.getStatus();

        switch (status) {
            case BizOrder.STATUS_DRAFT_CREATE -> {
                order.setStatus(BizOrder.STATUS_ACTIVE);
                order.setUpdateTime(LocalDateTime.now());
                this.updateById(order);
            }
            case BizOrder.STATUS_PENDING_UPDATE -> {
                OrderDTO pending = readPending(order.getPendingData());
                if (pending == null) {
                    throw new BusinessException("500", "pending snapshot missing, resubmit update please");
                }
                BeanUtils.copyProperties(pending, order);
                order.setPendingData(null);
                order.setStatus(BizOrder.STATUS_ACTIVE);
                order.setUpdateTime(LocalDateTime.now());
                this.updateById(order);
            }
            case BizOrder.STATUS_PENDING_DELETE -> {
                if (!this.removeById(id)) {
                    throw new BusinessException("404", "order not found");
                }
                return order;
            }
            case BizOrder.STATUS_ACTIVE ->
                    throw new BusinessException("400", "already active, no confirm needed");
            default -> throw new BusinessException("400", "unknown status: " + status);
        }
        return order;
    }

    @Override
    @Transactional
    public void cancel(Long id) {
        BizOrder order = this.getById(id);
        if (order == null) {
            throw new BusinessException("404", "order not found");
        }
        int status = order.getStatus() == null ? BizOrder.STATUS_ACTIVE : order.getStatus();
        switch (status) {
            case BizOrder.STATUS_DRAFT_CREATE -> this.removeById(id);
            case BizOrder.STATUS_PENDING_UPDATE -> {
                order.setPendingData(null);
                order.setStatus(BizOrder.STATUS_ACTIVE);
                order.setUpdateTime(LocalDateTime.now());
                this.updateById(order);
            }
            case BizOrder.STATUS_PENDING_DELETE -> {
                order.setStatus(BizOrder.STATUS_ACTIVE);
                order.setUpdateTime(LocalDateTime.now());
                this.updateById(order);
            }
            default -> throw new BusinessException("400", "no cancel needed at current status");
        }
    }

    @Override
    @Transactional
    @Sign(storePayload = true)
    public void delete(Long id) {
        BizOrder order = this.getById(id);
        if (order == null) {
            throw new BusinessException("404", "order not found");
        }
        int status = order.getStatus() == null ? BizOrder.STATUS_ACTIVE : order.getStatus();
        switch (status) {
            case BizOrder.STATUS_DRAFT_CREATE -> this.removeById(id);
            case BizOrder.STATUS_ACTIVE, BizOrder.STATUS_PENDING_UPDATE -> {
                order.setStatus(BizOrder.STATUS_PENDING_DELETE);
                order.setUpdateTime(LocalDateTime.now());
                this.updateById(order);
            }
            case BizOrder.STATUS_PENDING_DELETE ->
                    throw new BusinessException("400", "already pending delete");
            default -> throw new BusinessException("400", "unknown status: " + status);
        }
    }

    private String writePending(OrderDTO dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (Exception e) {
            throw new BusinessException("500", "snapshot serialize failed");
        }
    }

    private OrderDTO readPending(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, OrderDTO.class);
        } catch (Exception e) {
            return null;
        }
    }
}