package com.cqcnihao.bigx.common.util;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分页工具类
 * 提供多种分页查询方式，优化深分页性能
 */
@Data
@NoArgsConstructor
public class PageUtil {
    
    /**
     * 默认分页大小
     */
    public static final int DEFAULT_PAGE_SIZE = 10;
    
    /**
     * 最大分页大小
     */
    public static final int MAX_PAGE_SIZE = 100;
    
    /**
     * 最大页数（防止深度分页）
     */
    public static final int MAX_PAGE_NUM = 1000;
    
    /**
     * 创建MyBatis-Plus分页对象
     * 
     * @param pageNum 页码（从1开始）
     * @param pageSize 每页大小
     * @param <T> 实体类型
     * @return 分页对象
     */
    public static <T> Page<T> createPage(Integer pageNum, Integer pageSize) {
        int currentPage = (pageNum == null || pageNum < 1) ? 1 : pageNum;
        int size = (pageSize == null || pageSize < 1) ? DEFAULT_PAGE_SIZE : pageSize;
        
        // 限制最大页码
        if (currentPage > MAX_PAGE_NUM) {
            currentPage = MAX_PAGE_NUM;
        }
        
        // 限制最大页大小
        if (size > MAX_PAGE_SIZE) {
            size = MAX_PAGE_SIZE;
        }
        
        return new Page<>(currentPage, size);
    }
    
    /**
     * 创建MyBatis-Plus分页对象（带排序）
     * 
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param orderField 排序字段
     * @param isAsc 是否升序
     * @param <T> 实体类型
     * @return 分页对象
     */
    public static <T> Page<T> createPage(Integer pageNum, Integer pageSize, 
                                         String orderField, boolean isAsc) {
        Page<T> page = createPage(pageNum, pageSize);
        
        if (StrUtil.isNotBlank(orderField)) {
            if (isAsc) {
                page.addOrder(OrderItem.asc(StrUtil.toCamelCase(orderField)
                ));
            } else {
                page.addOrder(OrderItem.desc(StrUtil.toCamelCase(orderField)
                ));
            }
        }
        
        return page;
    }
    
    /**
     * 转换分页结果
     * 
     * @param sourcePage 源分页
     * @param converter 转换器
     * @param <S> 源类型
     * @param <T> 目标类型
     * @return 转换后的分页
     */
    public static <S, T> PageResult<T> convert(Page<S> sourcePage, Function<S, T> converter) {
        List<T> records = sourcePage.getRecords().stream()
            .map(converter)
            .collect(Collectors.toList());
        
        PageResult<T> result = new PageResult<>();
        result.setList(records);
        result.setTotal(sourcePage.getTotal());
        result.setPageNum((int) sourcePage.getCurrent());
        result.setPageSize((int) sourcePage.getSize());
        result.setPages((int) sourcePage.getPages());
        
        return result;
    }
    
    /**
     * 构建分页结果
     * 
     * @param page MyBatis-Plus分页对象
     * @param <T> 实体类型
     * @return 分页结果
     */
    public static <T> PageResult<T> buildResult(Page<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setList(page.getRecords());
        result.setTotal(page.getTotal());
        result.setPageNum((int) page.getCurrent());
        result.setPageSize((int) page.getSize());
        result.setPages((int) page.getPages());
        
        return result;
    }
    
    /**
     * 构建空的分页结果
     * 
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param <T> 实体类型
     * @return 空分页结果
     */
    public static <T> PageResult<T> emptyResult(int pageNum, int pageSize) {
        PageResult<T> result = new PageResult<>();
        result.setList(Collections.emptyList());
        result.setTotal(0L);
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        result.setPages(0);
        
        return result;
    }
    
    /**
     * 计算偏移量
     * 
     * @param pageNum 页码（从1开始）
     * @param pageSize 每页大小
     * @return 偏移量
     */
    public static int calculateOffset(int pageNum, int pageSize) {
        return (pageNum - 1) * pageSize;
    }
    
    /**
     * 验证分页参数
     * 
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 是否合法
     */
    public static boolean isValid(int pageNum, int pageSize) {
        return pageNum > 0 && pageSize > 0 && pageNum <= MAX_PAGE_NUM && pageSize <= MAX_PAGE_SIZE;
    }
    
    /**
     * 分页结果封装
     * 
     * @param <T> 实体类型
     */
    @Data
    public static class PageResult<T> {
        /**
         * 数据列表
         */
        private List<T> list;
        
        /**
         * 总记录数
         */
        private Long total;
        
        /**
         * 当前页码
         */
        private Integer pageNum;
        
        /**
         * 每页大小
         */
        private Integer pageSize;
        
        /**
         * 总页数
         */
        private Integer pages;
        
        /**
         * 是否有下一页
         */
        public boolean hasNext() {
            return pageNum < pages;
        }
        
        /**
         * 是否有上一页
         */
        public boolean hasPrevious() {
            return pageNum > 1;
        }
        
        /**
         * 是否为空
         */
        public boolean isEmpty() {
            return list == null || list.isEmpty();
        }
    }
}
