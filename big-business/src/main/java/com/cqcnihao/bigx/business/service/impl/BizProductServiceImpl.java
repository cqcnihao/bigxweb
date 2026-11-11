package com.cqcnihao.bigx.business.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.repository.AbstractRepository;
import com.cqcnihao.bigx.business.dto.ProductDTO;
import com.cqcnihao.bigx.business.entity.BizProduct;
import com.cqcnihao.bigx.business.mapper.BizProductMapper;
import com.cqcnihao.bigx.business.service.BizProductService;
import com.cqcnihao.bigx.core.exception.BusinessException;
import com.cqcnihao.bigx.license.LicenseSignService;
import com.cqcnihao.bigx.license.annotation.BizType;
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
public class BizProductServiceImpl extends AbstractRepository<BizProductMapper, BizProduct>
        implements BizProductService {

    private final BizProductMapper baseMapper;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private LicenseSignService signService;

    public BizProductServiceImpl(BizProductMapper baseMapper, ObjectMapper objectMapper) {
        this.baseMapper = baseMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public BizProductMapper getBaseMapper() {
        return baseMapper;
    }

    @Override
    public boolean saveBatch(java.util.Collection<BizProduct> entityList, int batchSize) {
        entityList.forEach(this::save);
        return true;
    }

    @Override
    public boolean saveOrUpdateBatch(java.util.Collection<BizProduct> entityList, int batchSize) {
        entityList.forEach(this::saveOrUpdate);
        return true;
    }

    @Override
    public boolean updateBatchById(java.util.Collection<BizProduct> entityList, int batchSize) {
        entityList.forEach(this::updateById);
        return true;
    }

    @Override
    public IPage<BizProduct> page(long current, long size, String keyword, Integer status) {
        Page<BizProduct> page = new Page<>(current, size);
        LambdaQueryWrapper<BizProduct> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                    .like(BizProduct::getProductName, keyword)
                    .or().like(BizProduct::getProductCode, keyword));
        }
        if (status != null) {
            wrapper.eq(BizProduct::getStatus, status);
        }
        wrapper.orderByDesc(BizProduct::getUpdateTime);
        return this.page(page, wrapper);
    }

    @Override
    @Transactional
    @Sign(storePayload = true)
    public BizProduct create(ProductDTO dto) {
        BizProduct product = new BizProduct();
        BeanUtils.copyProperties(dto, product);
        product.setStatus(BizProduct.STATUS_DRAFT_CREATE);
        product.setCreateBy(StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null);
        product.setCreateTime(LocalDateTime.now());
        product.setUpdateTime(LocalDateTime.now());
        product.setDeleted(0);
        this.save(product);
        return product;
    }

    @Override
    @Transactional
    @Sign(storePayload = true)
    public BizProduct update(ProductDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("400", "update requires id");
        }
        BizProduct exist = this.getById(dto.getId());
        if (exist == null) {
            throw new BusinessException("404", "product not found");
        }
        exist.setUpdateTime(LocalDateTime.now());
        if (Objects.equals(exist.getStatus(), BizProduct.STATUS_DRAFT_CREATE)) {
            BeanUtils.copyProperties(dto, exist);
            this.updateById(exist);
            return exist;
        }
        exist.setPendingData(writePending(dto));
        exist.setStatus(BizProduct.STATUS_PENDING_UPDATE);
        this.updateById(exist);
        return exist;
    }

    @Override
    @Transactional
    @Verify
    public BizProduct confirm(Long id) {
        BizProduct product = this.getById(id);
        if (product == null) {
            throw new BusinessException("404", "product not found");
        }
        int status = product.getStatus() == null ? BizProduct.STATUS_ACTIVE : product.getStatus();

        switch (status) {
            case BizProduct.STATUS_DRAFT_CREATE -> {
                product.setStatus(BizProduct.STATUS_ACTIVE);
                product.setUpdateTime(LocalDateTime.now());
                this.updateById(product);
            }
            case BizProduct.STATUS_PENDING_UPDATE -> {
                ProductDTO pending = readPending(product.getPendingData());
                if (pending == null) {
                    throw new BusinessException("500", "pending snapshot missing, resubmit update please");
                }
                BeanUtils.copyProperties(pending, product);
                product.setPendingData(null);
                product.setStatus(BizProduct.STATUS_ACTIVE);
                product.setUpdateTime(LocalDateTime.now());
                this.updateById(product);
            }
            case BizProduct.STATUS_PENDING_DELETE -> {
                if (!this.removeById(id)) {
                    throw new BusinessException("404", "product not found");
                }
                return product;
            }
            case BizProduct.STATUS_ACTIVE ->
                    throw new BusinessException("400", "already active, no confirm needed");
            default -> throw new BusinessException("400", "unknown status: " + status);
        }
        return product;
    }

    @Override
    @Transactional
    public void cancel(Long id) {
        BizProduct product = this.getById(id);
        if (product == null) {
            throw new BusinessException("404", "product not found");
        }
        int status = product.getStatus() == null ? BizProduct.STATUS_ACTIVE : product.getStatus();
        switch (status) {
            case BizProduct.STATUS_DRAFT_CREATE -> this.removeById(id);
            case BizProduct.STATUS_PENDING_UPDATE -> {
                product.setPendingData(null);
                product.setStatus(BizProduct.STATUS_ACTIVE);
                product.setUpdateTime(LocalDateTime.now());
                this.updateById(product);
            }
            case BizProduct.STATUS_PENDING_DELETE -> {
                product.setStatus(BizProduct.STATUS_ACTIVE);
                product.setUpdateTime(LocalDateTime.now());
                this.updateById(product);
            }
            default -> throw new BusinessException("400", "no cancel needed at current status");
        }
    }

    @Override
    @Transactional
    @Sign(storePayload = true)
    public void delete(Long id) {
        BizProduct product = this.getById(id);
        if (product == null) {
            throw new BusinessException("404", "product not found");
        }
        int status = product.getStatus() == null ? BizProduct.STATUS_ACTIVE : product.getStatus();
        switch (status) {
            case BizProduct.STATUS_DRAFT_CREATE -> this.removeById(id);
            case BizProduct.STATUS_ACTIVE, BizProduct.STATUS_PENDING_UPDATE -> {
                product.setStatus(BizProduct.STATUS_PENDING_DELETE);
                product.setUpdateTime(LocalDateTime.now());
                this.updateById(product);
            }
            case BizProduct.STATUS_PENDING_DELETE ->
                    throw new BusinessException("400", "already pending delete");
            default -> throw new BusinessException("400", "unknown status: " + status);
        }
    }

    private String writePending(ProductDTO dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (Exception e) {
            throw new BusinessException("500", "snapshot serialize failed");
        }
    }

    private ProductDTO readPending(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ProductDTO.class);
        } catch (Exception e) {
            return null;
        }
    }
}