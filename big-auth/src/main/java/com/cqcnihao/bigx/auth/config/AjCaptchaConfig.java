package com.cqcnihao.bigx.auth.config;

import com.anji.captcha.model.common.Const;
import com.anji.captcha.service.CaptchaService;
import com.anji.captcha.service.impl.CaptchaServiceFactory;
import com.anji.captcha.util.ImageUtils;
import com.cqcnihao.bigx.auth.captcha.CaptchaCacheServiceRedisImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.FileCopyUtils;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * AJ-Captcha 行为验证码装配（Boot 4 / jakarta 自写版，替代 starter 的 Boot2 自动装配）
 * <p>
 * starter（{@code com.anji-plus:captcha-spring-boot-starter}）基于 Boot 2.7 / javax，
 * 其 {@code spring.factories} 自动装配在 Boot 4 下天然不生效，因此：
 * <ul>
 *   <li>{@code CaptchaService} 由本类通过 {@link CaptchaServiceFactory#getInstance(Properties)} 装配；</li>
 *   <li>缓存用 Redis：{@link com.cqcnihao.bigx.auth.captcha.CaptchaCacheServiceRedisImpl}
 *       经 SPI 注册（META-INF/services），工厂按 {@code captcha.cacheType=redis} 选择；</li>
 *   <li>滑动拼图底图取自 starter jar 内置资源 {@code classpath:images/jigsaw}，启动期一次性加载内存缓存。</li>
 * </ul>
 * 配置键前缀 {@code aj.captcha}（见 big-auth application.yml），HTTP 端点见 AjCaptchaController。
 *
 * @author bigweb
 */
@Slf4j
@Configuration
public class AjCaptchaConfig {

    /** 右下角水印文字 */
    @Value("${aj.captcha.water-mark:bigxweb 研发团队}")
    private String waterMark;

    /** 水印字体（用系统字体名，避免依赖 jar 内置 ttf） */
    @Value("${aj.captcha.water-font:SimHei}")
    private String waterFont;

    /** 滑动允许误差（像素） */
    @Value("${aj.captcha.slip-offset:5}")
    private String slipOffset;

    /** 坐标 AES 加密开关：与官方前端组件约定一致，默认开启 */
    @Value("${aj.captcha.aes-status:true}")
    private Boolean aesStatus;

    /** 滑块干扰项（0/1/2） */
    @Value("${aj.captcha.interference-options:0}")
    private String interferenceOptions;

    /** 滑动拼图底图路径（AJ-Captcha core jar 内置资源，original + slidingBlock 齐全） */
    @Value("${aj.captcha.jigsaw:classpath:defaultImages/jigsaw}")
    private String jigsaw;

    /** 验证码接口内置分钟级限流开关 */
    @Value("${aj.captcha.req-frequency-limit-enable:true}")
    private boolean reqFrequencyLimitEnable;

    @Value("${aj.captcha.req-get-minute-limit:30}")
    private int reqGetMinuteLimit;

    @Value("${aj.captcha.req-check-minute-limit:60}")
    private int reqCheckMinuteLimit;

    @Value("${aj.captcha.req-verify-minute-limit:60}")
    private int reqVerifyMinuteLimit;

    @Value("${aj.captcha.req-get-lock-limit:5}")
    private int reqGetLockLimit;

    @Value("${aj.captcha.req-get-lock-seconds:300}")
    private int reqGetLockSeconds;

    @Bean
    public CaptchaService captchaService(StringRedisTemplate redisTemplate) {
        // 先注入 RedisTemplate，再走工厂（SPI 实例化 CaptchaCacheServiceRedisImpl 时为无参构造）
        CaptchaCacheServiceRedisImpl.init(redisTemplate);

        Properties config = new Properties();
        config.put(Const.CAPTCHA_CACHETYPE, "redis");
        config.put(Const.CAPTCHA_TYPE, "blockPuzzle");
        config.put(Const.CAPTCHA_WATER_MARK, waterMark);
        config.put(Const.CAPTCHA_WATER_FONT, waterFont);
        config.put(Const.CAPTCHA_FONT_TYPE, waterFont);
        config.put(Const.CAPTCHA_SLIP_OFFSET, slipOffset);
        config.put(Const.CAPTCHA_AES_STATUS, String.valueOf(aesStatus));
        config.put(Const.CAPTCHA_INTERFERENCE_OPTIONS, interferenceOptions);
        config.put(Const.ORIGINAL_PATH_JIGSAW, jigsaw);
        config.put(Const.REQ_FREQUENCY_LIMIT_ENABLE, reqFrequencyLimitEnable ? "1" : "0");
        config.put(Const.REQ_GET_MINUTE_LIMIT, String.valueOf(reqGetMinuteLimit));
        config.put(Const.REQ_CHECK_MINUTE_LIMIT, String.valueOf(reqCheckMinuteLimit));
        config.put(Const.REQ_VALIDATE_MINUTE_LIMIT, String.valueOf(reqVerifyMinuteLimit));
        config.put(Const.REQ_GET_LOCK_LIMIT, String.valueOf(reqGetLockLimit));
        config.put(Const.REQ_GET_LOCK_SECONDS, String.valueOf(reqGetLockSeconds));

        // 底图以 classpath 方式加载时，启动期一次性 Base64 载入内存缓存
        if (jigsaw.startsWith("classpath")) {
            config.put(Const.CAPTCHA_INIT_ORIGINAL, "true");
            Map<String, String> original = loadClasspathImages(jigsaw + "/original/*.png");
            Map<String, String> slidingBlock = loadClasspathImages(jigsaw + "/slidingBlock/*.png");
            if (original.isEmpty() || slidingBlock.isEmpty()) {
                throw new IllegalStateException("AJ-Captcha 滑动拼图底图加载失败：请确认 captcha-spring-boot-starter jar 内含 images/jigsaw 资源");
            }
            ImageUtils.cacheBootImage(original, slidingBlock, new HashMap<>());
        }

        CaptchaService service = CaptchaServiceFactory.getInstance(config);
        log.info("AJ-Captcha 行为验证码已装配：type=blockPuzzle, cacheType=redis, aesStatus={}, watermark={}",
                aesStatus, waterMark);
        return service;
    }

    /** 扫描 classpath 底图并 Base64 编码（复刻 starter 的 getResourcesImagesFile） */
    private Map<String, String> loadClasspathImages(String pattern) {
        Map<String, String> imgMap = new HashMap<>();
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources(pattern);
            for (Resource resource : resources) {
                byte[] bytes = FileCopyUtils.copyToByteArray(resource.getInputStream());
                imgMap.put(resource.getFilename(),
                        Base64.getEncoder().encodeToString(bytes));
            }
        } catch (Exception e) {
            log.warn("加载 AJ-Captcha 底图失败: {}", pattern, e);
        }
        return imgMap;
    }
}
