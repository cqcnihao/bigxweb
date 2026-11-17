package com.cqcnihao.bigx.auth.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.anji.captcha.service.CaptchaService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cqcnihao.bigx.auth.dto.LoginDTO;
import com.cqcnihao.bigx.auth.dto.LoginVO;
import com.cqcnihao.bigx.auth.entity.SysUser;
import com.cqcnihao.bigx.auth.mapper.SysUserMapper;
import com.cqcnihao.bigx.core.exception.BusinessException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCrypt;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AuthServiceImpl 单元测试
 * <p>
 * 覆盖 token 签发链路（登录）与登录态读取（当前用户、登出）：
 * 验证码校验、账号锁定/禁用、密码校验失败计数、登录成功后调用 Sa-Token 签发 token
 * 并把 token/角色/权限组装进 LoginVO。
 * <p>
 * 说明：token 本身由 Sa-Token 生成并持久化在 Redis（JacksonSaTokenDao），
 * 过期/签名等底层校验属于 Sa-Token 与 Redis 的职责，纯单元测试中通过静态 mock StpUtil 隔离，
 * 不做真实 token 生命周期验证（需要 Redis 环境）。
 */
class AuthServiceImplTest {

    private static final String CAPTCHA_KEY = "auth:captcha:";
    private static final String LOGIN_FAIL_PREFIX = "auth:login:fail:";
    private static final String LOGIN_LOCK_PREFIX = "auth:login:lock:";
    private static final String CAPTCHA_IP_PREFIX = "auth:captcha:ip:";
    private static final String LOGIN_IP_FAIL_PREFIX = "auth:login:ip:";

    private SysUserMapper userMapper;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private CaptchaService captchaService;
    private AuthServiceImpl authService;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysUser.class);
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userMapper = mock(SysUserMapper.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // AJ-Captcha 行为验证服务（登录传 captchaVerification 时才会用到，本测试走旧字符图验证码）
        captchaService = mock(CaptchaService.class);
        authService = new AuthServiceImpl(userMapper, redisTemplate, captchaService);
    }

    // ------------------------------------------------------------------
    // 登录成功：token 签发主链路
    // ------------------------------------------------------------------

    @Test
    void login_shouldIssueTokenAndReturnProfileWhenCredentialsValid() {
        SysUser user = user(1L, 1, BCrypt.hashpw("secret123", BCrypt.gensalt()));
        when(valueOps.get(CAPTCHA_KEY + "k1")).thenReturn("abcd");
        when(redisTemplate.hasKey(LOGIN_LOCK_PREFIX + "alice")).thenReturn(false);
        when(userMapper.selectByUsernameWithRoles("alice")).thenReturn(user);
        when(userMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of("admin"));
        when(userMapper.selectPermissionCodesByUserId(1L)).thenReturn(List.of("product:read"));
        when(userMapper.update(isNull(), any())).thenReturn(1);

        try (MockedStatic<StpUtil> stpUtil = Mockito.mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getTokenValue).thenReturn("token-abc");
            stpUtil.when(StpUtil::getTokenTimeout).thenReturn(2592000L);

            LoginVO vo = authService.login(loginDTO("secret123"));

            assertEquals("token-abc", vo.getToken(), "登录成功必须把 Sa-Token 生成的 token 返回");
            assertEquals(1L, vo.getUserId());
            assertEquals("alice", vo.getUsername());
            assertEquals("小A", vo.getNickname());
            assertEquals(List.of("admin"), vo.getRoles());
            assertEquals(List.of("product:read"), vo.getPermissions());
            assertEquals(2592000L, vo.getTokenTimeout());

            stpUtil.verify(() -> StpUtil.login(1L), times(1));
            stpUtil.verify(() -> StpUtil.getTokenValue(), times(1));
        }

        // 验证码一次性作废、失败计数清理、最近登录时间刷新
        verify(redisTemplate).delete(CAPTCHA_KEY + "k1");
        verify(redisTemplate).delete(LOGIN_FAIL_PREFIX + "alice");
        verify(redisTemplate).delete(LOGIN_LOCK_PREFIX + "alice");
        verify(userMapper).update(isNull(), any());
    }

    @Test
    void login_shouldFallbackToEmptyListsWhenMapperReturnsNull() {
        SysUser user = user(1L, 1, BCrypt.hashpw("secret123", BCrypt.gensalt()));
        when(valueOps.get(CAPTCHA_KEY + "k1")).thenReturn("abcd");
        when(userMapper.selectByUsernameWithRoles("alice")).thenReturn(user);
        when(userMapper.selectRoleCodesByUserId(1L)).thenReturn(null);
        when(userMapper.selectPermissionCodesByUserId(1L)).thenReturn(null);

        try (MockedStatic<StpUtil> stpUtil = Mockito.mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getTokenValue).thenReturn("token-abc");
            stpUtil.when(StpUtil::getTokenTimeout).thenReturn(100L);

            LoginVO vo = authService.login(loginDTO("secret123"));

            assertTrue(vo.getRoles().isEmpty(), "角色为空时不能返回 null");
            assertTrue(vo.getPermissions().isEmpty(), "权限为空时不能返回 null");
        }
    }

    // ------------------------------------------------------------------
    // 登录失败：验证码、锁定、禁用、密码错误
    // ------------------------------------------------------------------

    @Test
    void login_shouldRejectWhenCaptchaMissing() {
        LoginDTO dto = loginDTO("secret123");
        dto.setCaptchaKey(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(dto));

        assertEquals("400", ex.getCode());
        assertEquals("验证码不能为空", ex.getMessage());
        verifyNoInteractions(userMapper);
    }

    @Test
    void login_shouldRejectWhenCaptchaMismatchAndInvalidateIt() {
        when(valueOps.get(CAPTCHA_KEY + "k1")).thenReturn("abcd");
        LoginDTO dto = loginDTO("secret123");
        dto.setCaptchaCode("zzzz");

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.login(dto));

        assertEquals("400", ex.getCode());
        assertEquals("验证码错误或已过期", ex.getMessage());
        verify(redisTemplate).delete(CAPTCHA_KEY + "k1");
        verifyNoInteractions(userMapper);
    }

    @Test
    void login_shouldRejectLockedAccount() {
        when(valueOps.get(CAPTCHA_KEY + "k1")).thenReturn("abcd");
        when(redisTemplate.hasKey(LOGIN_LOCK_PREFIX + "alice")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(loginDTO("secret123")));

        assertEquals("429", ex.getCode());
        assertEquals("登录失败次数过多，账号已锁定，请稍后再试", ex.getMessage());
        verifyNoInteractions(userMapper);
    }

    @Test
    void login_shouldRejectBlockedIpBeforeVerifyingCaptcha() {
        when(valueOps.get(LOGIN_IP_FAIL_PREFIX + "unknown")).thenReturn("20");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(loginDTO("secret123")));

        assertEquals("429", ex.getCode());
        assertEquals("登录尝试过于频繁，请稍后再试", ex.getMessage());
        verify(valueOps, never()).get(CAPTCHA_KEY + "k1");
        verifyNoInteractions(userMapper);
    }

    @Test
    void login_shouldRejectDisabledAccount() {
        SysUser user = user(1L, 0, BCrypt.hashpw("secret123", BCrypt.gensalt()));
        when(valueOps.get(CAPTCHA_KEY + "k1")).thenReturn("abcd");
        when(userMapper.selectByUsernameWithRoles("alice")).thenReturn(user);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(loginDTO("secret123")));

        assertEquals("403", ex.getCode());
        assertEquals("账号已被禁用", ex.getMessage());
    }

    @Test
    void login_shouldCountFailureWhenPasswordWrong() {
        SysUser user = user(1L, 1, BCrypt.hashpw("secret123", BCrypt.gensalt()));
        when(valueOps.get(CAPTCHA_KEY + "k1")).thenReturn("abcd");
        when(userMapper.selectByUsernameWithRoles("alice")).thenReturn(user);
        when(valueOps.increment(LOGIN_FAIL_PREFIX + "alice")).thenReturn(1L);
        when(valueOps.increment(LOGIN_IP_FAIL_PREFIX + "unknown")).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(loginDTO("wrong-password")));

        assertEquals("401", ex.getCode());
        assertEquals("用户名或密码错误", ex.getMessage());
        verify(valueOps).increment(LOGIN_FAIL_PREFIX + "alice");
        verify(redisTemplate).expire(LOGIN_FAIL_PREFIX + "alice", 10, TimeUnit.MINUTES);
        verify(valueOps).increment(LOGIN_IP_FAIL_PREFIX + "unknown");
    }

    @Test
    void login_shouldLockAccountAfterFiveFailures() {
        SysUser user = user(1L, 1, BCrypt.hashpw("secret123", BCrypt.gensalt()));
        when(valueOps.get(CAPTCHA_KEY + "k1")).thenReturn("abcd");
        when(userMapper.selectByUsernameWithRoles("alice")).thenReturn(user);
        when(valueOps.increment(LOGIN_FAIL_PREFIX + "alice")).thenReturn(5L);
        when(valueOps.increment(LOGIN_IP_FAIL_PREFIX + "unknown")).thenReturn(5L);

        assertThrows(BusinessException.class, () -> authService.login(loginDTO("wrong-password")));

        verify(valueOps).set(eq(LOGIN_LOCK_PREFIX + "alice"), eq("1"), eq(10L), eq(TimeUnit.MINUTES));
        verify(redisTemplate).delete(LOGIN_FAIL_PREFIX + "alice");
    }

    @Test
    void login_shouldRejectUnknownUserWithoutLeakingExistence() {
        when(valueOps.get(CAPTCHA_KEY + "k1")).thenReturn("abcd");
        when(userMapper.selectByUsernameWithRoles("alice")).thenReturn(null);
        when(valueOps.increment(LOGIN_FAIL_PREFIX + "alice")).thenReturn(1L);
        when(valueOps.increment(LOGIN_IP_FAIL_PREFIX + "unknown")).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(loginDTO("secret123")));

        assertEquals("401", ex.getCode());
        assertEquals("用户名或密码错误", ex.getMessage(), "不能暴露账号是否存在");
    }

    // ------------------------------------------------------------------
    // 登录态读取：当前用户 / 登出
    // ------------------------------------------------------------------

    @Test
    void getCurrentUser_shouldReadLoginIdAndLoadProfile() {
        SysUser user = user(1L, 1, null);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of("admin"));
        when(userMapper.selectPermissionCodesByUserId(1L)).thenReturn(List.of("product:read"));

        try (MockedStatic<StpUtil> stpUtil = Mockito.mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            stpUtil.when(StpUtil::getTokenTimeout).thenReturn(3600L);

            LoginVO vo = authService.getCurrentUser();

            assertEquals(1L, vo.getUserId());
            assertEquals("alice", vo.getUsername());
            assertEquals(List.of("admin"), vo.getRoles());
            assertEquals(List.of("product:read"), vo.getPermissions());
            assertEquals(3600L, vo.getTokenTimeout());
            stpUtil.verify(() -> StpUtil.getLoginIdAsLong(), times(1));
        }
    }

    @Test
    void getCurrentUser_shouldThrowWhenUserRemoved() {
        when(userMapper.selectById(1L)).thenReturn(null);

        try (MockedStatic<StpUtil> stpUtil = Mockito.mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

            BusinessException ex = assertThrows(BusinessException.class, () -> authService.getCurrentUser());

            assertEquals("401", ex.getCode());
            assertEquals("用户不存在", ex.getMessage());
        }
    }

    @Test
    void logout_shouldLogoutCurrentToken() {
        try (MockedStatic<StpUtil> stpUtil = Mockito.mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

            authService.logout();

            stpUtil.verify(() -> StpUtil.getLoginIdAsLong(), times(1));
            stpUtil.verify(StpUtil::logout, times(1));
        }
    }

    // ------------------------------------------------------------------
    // 验证码
    // ------------------------------------------------------------------

    @Test
    void captcha_shouldCacheLowercaseTextAndReturnKeyWithImage() {
        when(valueOps.increment(CAPTCHA_IP_PREFIX + "unknown")).thenReturn(1L);

        Map<String, String> data = authService.captcha();

        assertNotNull(data.get("captchaKey"));
        assertNotNull(data.get("captchaBase64"));
        assertFalse(data.get("captchaBase64").isBlank());
        verify(valueOps).set(startsWith(CAPTCHA_KEY), anyString(), eq(5L), eq(TimeUnit.MINUTES));
        verify(redisTemplate).expire(CAPTCHA_IP_PREFIX + "unknown", 1, TimeUnit.MINUTES);
    }

    @Test
    void captcha_shouldRateLimitByIp() {
        when(valueOps.increment(CAPTCHA_IP_PREFIX + "unknown")).thenReturn(31L);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.captcha());

        assertEquals("429", ex.getCode());
        assertEquals("验证码请求过于频繁，请稍后再试", ex.getMessage());
        verify(valueOps, never()).set(anyString(), anyString(), anyLong(), any());
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    private static LoginDTO loginDTO(String password) {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("alice");
        dto.setPassword(password);
        dto.setCaptchaKey("k1");
        dto.setCaptchaCode("abcd");
        return dto;
    }

    private static SysUser user(Long id, Integer status, String password) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername("alice");
        user.setNickname("小A");
        user.setPassword(password);
        user.setStatus(status);
        user.setLastLoginTime(LocalDateTime.now().minusDays(1));
        return user;
    }
}
