package com.cqcnihao.bigx.common.util;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * 游标分页工具类
 * 基于游标的高效分页查询
 */
@Data
@NoArgsConstructor
public class CursorPageUtil {
    
    /**
     * 游标值
     */
    private Object cursor;
    
    /**
     * 是否还有更多数据
     */
    private boolean hasMore;
    
    /**
     * 当前游标
     */
    private Object currentCursor;
    
    /**
     * 下一页游标
     */
    private Object nextCursor;
    
    /**
     * 获取当前页数据
     */
    private List<?> records;
    
    /**
     * 创建游标分页结果
     * 
     * @param records 当前页数据
     * @return 游标分页结果
     */
    public static <T> CursorPageResult<T> build(List<T> records, long total) {
        CursorPageResult<T> result = new CursorPageResult<>();
        result.setRecords(records);
        result.setTotal(total);
        result.setPages((int) Math.ceil((double) total / records.size()));
        return result;
    }
    
    /**
     * 创建空结果
     * 
     * @return 空的游标分页结果
     */
    public static <T> CursorPageResult<T> empty() {
        CursorPageResult<T> result = new CursorPageResult<>();
        result.setRecords(Collections.emptyList());
        result.setTotal(0L);
        result.setPages(0);
        return result;
    }
    
    /**
     * 游标分页结果
     */
    @Data
    public static class CursorPageResult<T> {
        /**
         * 数据列表
         */
        private List<T> records;
        
        /**
         * 总记录数
         */
        private Long total;
        
        /**
         * 总页数
         */
        private Integer pages;
        
        /**
         * 当前游标
         */
        private Object cursor;
        
        /**
         * 是否有更多数据
         */
        private Boolean hasMore;
    }
}
