package com.cqcnihao.bigx.web.common.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 统一错误控制器注册（@Bean 方式）
 * <p>
 * 先于 Boot 的 ErrorMvcAutoConfiguration 注册本 Bean，
 * 期望其 basicErrorController 因 @ConditionalOnMissingBean(ErrorController.class) 退让。
 * <p>
 * 实测：即便排序正确（或改为 imports 自注册），Boot 4.1 的 basicErrorController
 * 条件仍可能命中（SearchStrategy.CURRENT 评估时机看不到后注册的定义），
 * 两个 /error 映射并存导致启动报 Ambiguous mapping。
 * 因此下方用 BeanFactoryPostProcessor 在所有 Bean 定义加载完成后
 * 兜底移除 basicErrorController 定义，保证 /error 唯一映射。
 * errorAttributes、ErrorPageCustomizer（容器错误分发）等其余 Boot 错误机制不受影响。
 *
 * @author bigweb
 */
@Configuration
@AutoConfigureBefore(name = "org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfiguration")
public class UnifiedErrorControllerConfig {

    @Bean
    public UnifiedErrorController unifiedErrorController() {
        return new UnifiedErrorController();
    }

    /**
     * 兜底移除 Boot 的 basicErrorController Bean 定义。
     * BFPP 在 ConfigurationClassPostProcessor 注册完全部定义之后才执行，时序必然安全。
     */
    @Bean
    public static BeanFactoryPostProcessor basicErrorControllerRemoval() {
        return beanFactory -> {
            if (beanFactory instanceof BeanDefinitionRegistry registry
                    && registry.containsBeanDefinition("basicErrorController")) {
                registry.removeBeanDefinition("basicErrorController");
            }
        };
    }
}

