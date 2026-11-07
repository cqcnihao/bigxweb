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
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class BizProductServiceImpl extends AbstractRepository<BizProductMapper, BizProduct>
        implements BizProductService {

    private final BizProductMapper baseMapper;

    public BizProductServiceImpl(BizProductMapper baseMapper) {
        this.baseMapper = baseMapper;
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
    public IPage<BizProduct> page(long current, long size, String keyword) {
        Page<BizProduct> page = new Page<>(current, size);
        LambdaQueryWrapper<BizProduct> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                    .like(BizProduct::getProductName, keyword)
                    .or().like(BizProduct::getProductCode, keyword));
        }
        wrapper.orderByDesc(BizProduct::getCreateTime);
        return this.page(page, wrapper);
    }

    @Override
    public BizProduct create(ProductDTO dto) {
        BizProduct product = new BizProduct();
        BeanUtils.copyProperties(dto, product);
        product.setCreateBy(StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null);
        product.setCreateTime(LocalDateTime.now());
        product.setUpdateTime(LocalDateTime.now());
        product.setDeleted(0);
        this.save(product);
        return product;
    }

    @Override
    public BizProduct update(ProductDTO dto) {
        if (dto.getId() == null) {
            throw new BusinessException("400", "更新必须传入 id");
        }
        BizProduct exist = this.getById(dto.getId());
        if (exist == null) {
            throw new BusinessException("404", "商品不存在");
        }
        BeanUtils.copyProperties(dto, exist);
        exist.setUpdateTime(LocalDateTime.now());
        this.updateById(exist);
        return exist;
    }

    @Override
    public void delete(Long id) {
        if (!this.removeById(id)) {
            throw new BusinessException("404", "商品不存在");
        }
    }
}