package com.cqcnihao.bigx.business.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cqcnihao.bigx.business.dto.ProductDTO;
import com.cqcnihao.bigx.business.entity.BizProduct;
import com.cqcnihao.bigx.business.mapper.BizProductMapper;
import com.cqcnihao.bigx.core.exception.BusinessException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * BizProductServiceImpl 单元测试
 * <p>
 * 重点覆盖从 MyBatis-Plus {@code IService/ServiceImpl} 迁移到
 * {@code IRepository/AbstractRepository} 后的行为一致性：
 * 保存、按 id 查询、更新、删除、分页都由基类默认实现转发到 BaseMapper，
 * 自定义的批量方法（saveBatch/saveOrUpdateBatch/updateBatchById）也必须落到对应 mapper 方法。
 * <p>
 * 测试不依赖真实数据库：用 Mockito 模拟 BaseMapper，只验证服务层的行为契约。
 */
class BizProductServiceImplTest {

    private BizProductMapper mapper;
    private BizProductServiceImpl service;

    /**
     * Lambda 条件构造器需要实体元数据（TableInfo），否则解析列名时会报错。
     * 这里手工初始化一次，让分页用例可以在纯单元测试环境跑通。
     */
    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), BizProduct.class);
    }

    @BeforeEach
    void setUp() {
        mapper = mock(BizProductMapper.class);
        service = new BizProductServiceImpl(mapper, new ObjectMapper());
    }

    // ------------------------------------------------------------------
    // 新增
    // ------------------------------------------------------------------

    @Test
    void create_shouldCopyDtoAndFillAuditFieldsWhenLoggedIn() {
        when(mapper.insert(any(BizProduct.class))).thenReturn(1);

        try (MockedStatic<StpUtil> stpUtil = Mockito.mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::isLogin).thenReturn(true);
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(42L);

            BizProduct created = service.create(dto("测试商品", "P-001", "10.00", 5));

            assertEquals("测试商品", created.getProductName());
            assertEquals("P-001", created.getProductCode());
            assertEquals(0, new BigDecimal("10.00").compareTo(created.getPrice()));
            assertEquals(5, created.getStock());
            assertEquals("电子", created.getCategory());
            assertEquals("描述", created.getDescription());
            assertEquals(42L, created.getCreateBy(), "登录用户应写入 createBy");
            assertEquals(0, created.getDeleted(), "逻辑删除标记应初始化为 0");
            assertEquals(BizProduct.STATUS_DRAFT_CREATE, created.getStatus(),
                    "两步确认：新增第一步应落草稿（status=0）");
            assertNotNull(created.getCreateTime());
            assertNotNull(created.getUpdateTime());

            verify(mapper, times(1)).insert(created);
        }
    }

    @Test
    void create_shouldLeaveCreateByNullWhenNotLoggedIn() {
        when(mapper.insert(any(BizProduct.class))).thenReturn(1);

        try (MockedStatic<StpUtil> stpUtil = Mockito.mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::isLogin).thenReturn(false);

            BizProduct created = service.create(dto("测试商品", "P-001", "10.00", 5));

            assertNull(created.getCreateBy(), "未登录时不应伪造 createBy");
            stpUtil.verify(StpUtil::getLoginIdAsLong, never());
            verify(mapper).insert(created);
        }
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    @Test
    void getById_shouldDelegateToBaseMapper() {
        BizProduct exist = product(1L, "旧商品");
        when(mapper.selectById(1L)).thenReturn(exist);

        BizProduct found = service.getById(1L);

        assertSame(exist, found);
        verify(mapper).selectById(1L);
    }

    // ------------------------------------------------------------------
    // 更新（两步确认：生效记录写快照；草稿直接改字段）
    // ------------------------------------------------------------------

    @Test
    void update_onActiveRecord_shouldWritePendingSnapshotWithoutTouchingOfficialFields() {
        BizProduct exist = product(1L, "旧商品");
        exist.setStatus(BizProduct.STATUS_ACTIVE);
        String oldName = exist.getProductName();
        BigDecimal oldPrice = exist.getPrice();
        when(mapper.selectById(1L)).thenReturn(exist);
        when(mapper.updateById(any(BizProduct.class))).thenReturn(1);

        ProductDTO dto = dto("新商品", "P-002", "20.00", 8);
        dto.setId(1L);

        BizProduct updated = service.update(dto);

        assertSame(exist, updated);
        assertEquals(BizProduct.STATUS_PENDING_UPDATE, updated.getStatus(),
                "生效记录提交修改后应进入修改待确认状态");
        assertEquals(oldName, updated.getProductName(), "正式字段不应被改动");
        assertEquals(0, oldPrice.compareTo(updated.getPrice()), "正式价格不应被改动");
        assertNotNull(updated.getPendingData(), "应写入变更快照");
        assertTrue(updated.getPendingData().contains("新商品"), "快照应包含新值");
        verify(mapper).updateById(exist);
    }

    @Test
    void update_onDraftRecord_shouldCopyFieldsDirectly() {
        BizProduct exist = product(1L, "草稿商品");
        exist.setStatus(BizProduct.STATUS_DRAFT_CREATE);
        when(mapper.selectById(1L)).thenReturn(exist);
        when(mapper.updateById(any(BizProduct.class))).thenReturn(1);

        ProductDTO dto = dto("新商品", "P-002", "20.00", 8);
        dto.setId(1L);

        BizProduct updated = service.update(dto);

        assertEquals(BizProduct.STATUS_DRAFT_CREATE, updated.getStatus(), "草稿应保持待确认状态");
        assertEquals("新商品", updated.getProductName(), "草稿可直接更新字段");
        assertNull(updated.getPendingData(), "草稿无需快照");
        verify(mapper).updateById(exist);
    }

    @Test
    void update_shouldRejectMissingIdWithoutTouchingMapper() {
        ProductDTO dto = dto("新商品", "P-002", "20.00", 8);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(dto));

        assertEquals("400", ex.getCode());
        assertEquals("更新必须传入 id", ex.getMessage());
        verifyNoInteractions(mapper);
    }

    @Test
    void update_shouldThrowNotFoundWhenEntityMissing() {
        when(mapper.selectById(99L)).thenReturn(null);
        ProductDTO dto = dto("新商品", "P-002", "20.00", 8);
        dto.setId(99L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(dto));

        assertEquals("404", ex.getCode());
        assertEquals("商品不存在", ex.getMessage());
        verify(mapper, never()).updateById(any(BizProduct.class));
    }

    // ------------------------------------------------------------------
    // 确认与取消（第二步）
    // ------------------------------------------------------------------

    @Test
    void confirm_shouldActivateDraftRecord() {
        BizProduct draft = product(1L, "草稿商品");
        draft.setStatus(BizProduct.STATUS_DRAFT_CREATE);
        when(mapper.selectById(1L)).thenReturn(draft);
        when(mapper.updateById(any(BizProduct.class))).thenReturn(1);

        BizProduct confirmed = service.confirm(1L);

        assertEquals(BizProduct.STATUS_ACTIVE, confirmed.getStatus(), "确认新增后应生效");
        verify(mapper).updateById(draft);
    }

    @Test
    void confirm_shouldApplyPendingSnapshotAndClearIt() {
        BizProduct pending = product(1L, "旧商品");
        pending.setStatus(BizProduct.STATUS_PENDING_UPDATE);
        ProductDTO change = dto("新商品", "P-002", "20.00", 8);
        pending.setPendingData(new ObjectMapper().writeValueAsString(change));
        when(mapper.selectById(1L)).thenReturn(pending);
        when(mapper.updateById(any(BizProduct.class))).thenReturn(1);

        BizProduct confirmed = service.confirm(1L);

        assertEquals(BizProduct.STATUS_ACTIVE, confirmed.getStatus());
        assertEquals("新商品", confirmed.getProductName(), "确认后应应用快照中的新值");
        assertEquals(0, new BigDecimal("20.00").compareTo(confirmed.getPrice()));
        assertNull(confirmed.getPendingData(), "确认后快照应清空");
        verify(mapper).updateById(pending);
    }

    @Test
    void confirm_shouldRejectAlreadyActiveRecord() {
        BizProduct active = product(1L, "已生效商品");
        active.setStatus(BizProduct.STATUS_ACTIVE);
        when(mapper.selectById(1L)).thenReturn(active);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.confirm(1L));

        assertEquals("400", ex.getCode());
        verify(mapper, never()).updateById(any(BizProduct.class));
    }

    @Test
    void confirm_shouldThrowNotFoundWhenMissing() {
        when(mapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.confirm(99L));

        assertEquals("404", ex.getCode());
    }

    @Test
    void cancel_shouldDeleteDraftRecord() {
        BizProduct draft = product(1L, "草稿商品");
        draft.setStatus(BizProduct.STATUS_DRAFT_CREATE);
        when(mapper.selectById(1L)).thenReturn(draft);
        when(mapper.deleteById(1L)).thenReturn(1);

        assertDoesNotThrow(() -> service.cancel(1L));

        verify(mapper).deleteById(1L);
    }

    @Test
    void cancel_shouldDiscardPendingSnapshotAndRestoreActive() {
        BizProduct pending = product(1L, "旧商品");
        pending.setStatus(BizProduct.STATUS_PENDING_UPDATE);
        pending.setPendingData("{\"productName\":\"新商品\"}");
        when(mapper.selectById(1L)).thenReturn(pending);
        when(mapper.updateById(any(BizProduct.class))).thenReturn(1);

        assertDoesNotThrow(() -> service.cancel(1L));

        assertEquals(BizProduct.STATUS_ACTIVE, pending.getStatus());
        assertEquals("旧商品", pending.getProductName(), "取消后正式数据不变");
        assertNull(pending.getPendingData());
        verify(mapper).updateById(pending);
    }

    @Test
    void cancel_shouldRejectActiveRecord() {
        BizProduct active = product(1L, "已生效商品");
        active.setStatus(BizProduct.STATUS_ACTIVE);
        when(mapper.selectById(1L)).thenReturn(active);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.cancel(1L));

        assertEquals("400", ex.getCode());
    }

    // ------------------------------------------------------------------
    // 删除
    // ------------------------------------------------------------------

    @Test
    void delete_shouldDelegateToBaseMapper() {
        // removeById 会先 selectById 校验存在，再执行 deleteById 逻辑删除
        when(mapper.selectById(1L)).thenReturn(product(1L, "商品A"));
        when(mapper.deleteById(1L)).thenReturn(1);

        assertDoesNotThrow(() -> service.delete(1L));

        verify(mapper).deleteById(1L);
    }

    @Test
    void delete_shouldThrowNotFoundWhenRemoveFails() {
        when(mapper.deleteById(99L)).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(99L));

        assertEquals("404", ex.getCode());
        assertEquals("商品不存在", ex.getMessage());
    }

    // ------------------------------------------------------------------
    // 分页
    // ------------------------------------------------------------------

    @Test
    void page_shouldPassPaginationAndKeywordConditionToMapper() {
        AtomicReference<Wrapper<BizProduct>> wrapperRef = new AtomicReference<>();
        when(mapper.selectPage(any(), any())).thenAnswer(invocation -> {
            IPage<BizProduct> page = invocation.getArgument(0);
            wrapperRef.set(invocation.getArgument(1));
            page.setRecords(List.of(product(1L, "商品A"), product(2L, "商品B")));
            page.setTotal(5);
            return page;
        });

        IPage<BizProduct> result = service.page(2, 3, "商品", null);

        assertEquals(2, result.getCurrent(), "分页页码应透传");
        assertEquals(3, result.getSize(), "分页大小应透传");
        assertEquals(5, result.getTotal());
        assertEquals(2, result.getRecords().size());

        LambdaQueryWrapper<BizProduct> wrapper = (LambdaQueryWrapper<BizProduct>) wrapperRef.get();
        String sql = wrapper.getSqlSegment();
        assertTrue(sql.contains("product_name"), "关键字应参与商品名称模糊匹配：" + sql);
        assertTrue(sql.contains("product_code"), "关键字应参与商品编码模糊匹配：" + sql);
        assertTrue(sql.contains("update_time"), "默认应按更新时间倒序：" + sql);
        assertEquals(2, wrapper.getParamNameValuePairs().size(), "两个 like 条件应各带一个参数");
    }

    @Test
    void page_shouldIgnoreBlankKeyword() {
        AtomicReference<Wrapper<BizProduct>> wrapperRef = new AtomicReference<>();
        when(mapper.selectPage(any(), any())).thenAnswer(invocation -> {
            IPage<BizProduct> page = invocation.getArgument(0);
            wrapperRef.set(invocation.getArgument(1));
            page.setRecords(List.of());
            page.setTotal(0);
            return page;
        });

        service.page(1, 10, "   ", null);

        LambdaQueryWrapper<BizProduct> wrapper = (LambdaQueryWrapper<BizProduct>) wrapperRef.get();
        String sql = wrapper.getSqlSegment();
        assertFalse(sql.contains("product_name"), "空白关键字不应生成 like 条件：" + sql);
        assertEquals(0, wrapper.getParamNameValuePairs().size());
    }

    // ------------------------------------------------------------------
    // 自定义批量方法（迁移后自行实现，最容易与旧 IService 行为不一致）
    // ------------------------------------------------------------------

    @Test
    void saveBatch_shouldInsertEachEntity() {
        when(mapper.insert(any(BizProduct.class))).thenReturn(1);
        List<BizProduct> list = List.of(product(null, "商品A"), product(null, "商品B"));

        assertTrue(service.saveBatch(list, 2));

        verify(mapper, times(2)).insert(any(BizProduct.class));
    }

    @Test
    void saveOrUpdateBatch_shouldDelegateEachEntityToInsertOrUpdate() {
        when(mapper.insertOrUpdate(any(BizProduct.class))).thenReturn(true);
        List<BizProduct> list = List.of(product(null, "商品A"), product(3L, "商品B"));

        assertTrue(service.saveOrUpdateBatch(list, 2));

        verify(mapper, times(2)).insertOrUpdate(any(BizProduct.class));
    }

    @Test
    void updateBatchById_shouldUpdateEachEntity() {
        when(mapper.updateById(any(BizProduct.class))).thenReturn(1);
        List<BizProduct> list = List.of(product(1L, "商品A"), product(2L, "商品B"));

        assertTrue(service.updateBatchById(list, 2));

        verify(mapper, times(2)).updateById(any(BizProduct.class));
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    private static ProductDTO dto(String name, String code, String price, Integer stock) {
        ProductDTO dto = new ProductDTO();
        dto.setProductName(name);
        dto.setProductCode(code);
        dto.setPrice(new BigDecimal(price));
        dto.setStock(stock);
        dto.setCategory("电子");
        dto.setDescription("描述");
        return dto;
    }

    private static BizProduct product(Long id, String name) {
        BizProduct product = new BizProduct();
        product.setId(id);
        product.setProductName(name);
        product.setProductCode("P-" + name);
        product.setPrice(new BigDecimal("10.00"));
        product.setStock(5);
        product.setCategory("电子");
        product.setDescription("描述");
        product.setStatus(BizProduct.STATUS_ACTIVE);
        product.setCreateTime(LocalDateTime.now().minusDays(1));
        product.setUpdateTime(LocalDateTime.now().minusDays(1));
        product.setDeleted(0);
        return product;
    }
}
