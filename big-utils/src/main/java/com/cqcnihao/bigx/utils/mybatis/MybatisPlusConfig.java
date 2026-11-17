package com.cqcnihao.bigx.utils.mybatis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;

@Configuration
public class MybatisPlusConfig {

    /**
     * 配置 MyBatis-Plus 的核心拦截器链
     * <p>
     * 注意拦截器的添加顺序会影响执行顺序
     *
     * @return 配置好的 MyBatis-Plus 拦截器实例
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        // 创建拦截器实例
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 添加乐观锁拦截器（实现版本号控制的乐观锁机制）
        interceptor.addInnerInterceptor(optimisticLockerInnerInterceptor());

        // 添加攻击阻断拦截器（防止全表更新/删除等危险操作）
        interceptor.addInnerInterceptor(blockAttackInnerInterceptor());
        
        // 添加分页拦截器（处理分页查询逻辑）
        interceptor.addInnerInterceptor(paginationInnerInterceptor());

        return interceptor;
    }
    
    /**
     * 分页插件
     */
    @Bean
    public PaginationInnerInterceptor paginationInnerInterceptor() {
    	
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);

        // 设置数据库类型
        paginationInterceptor.setDbType(DbType.MYSQL);
        
        // 设置最大单页限制数量，默认 500 条，-1 不受限制
        paginationInterceptor.setMaxLimit(1000L);
        
        // 开启 count 的 join 优化,只针对 left join
        paginationInterceptor.setOptimizeJoin(true);
        
        // 设置请求的页面大于最大页后操作
        // true: 调回到首页
        // false: 继续请求，默认 false
        paginationInterceptor.setOverflow(false);
        
        // 设置单页条数限制，默认无限制
        // paginationInterceptor.setMaxLimit(500L);
        
        return paginationInterceptor;
    }
    
    /**
     * 乐观锁插件
     */
    @Bean
    public OptimisticLockerInnerInterceptor optimisticLockerInnerInterceptor() {
        return new OptimisticLockerInnerInterceptor();
    }
    
    /**
     * 防止全表更新与删除插件
     * <p>
     * 创建并配置 BlockAttackInnerInterceptor 拦截器实例，用于防止全表更新和删除等危险操作。
     * 该拦截器会检查 UPDATE 和 DELETE 语句，如果没有 WHERE 条件则会抛出异常，有效防止误操作导致的数据丢失。
     *
     * @return 配置好的攻击阻断拦截器实例
     */
    @Bean
    public BlockAttackInnerInterceptor blockAttackInnerInterceptor() {
        // 创建并返回 BlockAttackInnerInterceptor 实例
        // 该拦截器无需额外配置，默认会拦截无 WHERE 条件的更新/删除操作
        return new BlockAttackInnerInterceptor();
    }
}