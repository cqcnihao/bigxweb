package com.cqcnihao.bigx.auth.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cqcnihao.bigx.auth.dto.LoginDTO;
import com.cqcnihao.bigx.auth.dto.LoginVO;
import com.cqcnihao.bigx.auth.entity.SysUser;
import com.cqcnihao.bigx.auth.mapper.SysUserMapper;
import com.cqcnihao.bigx.auth.service.AuthService;
import com.cqcnihao.bigx.core.exception.BusinessException;
import com.wf.captcha.SpecCaptcha;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 验证码有效期（分钟） */
    private static final long CAPTCHA_TTL_MINUTES = 5;

    /** 连续失败次数上限，达到后锁定账号 */
    @Value("${big.auth.security.max-fail-count:5}")
    private int maxFailCount = 5;
    /** 账号锁定时长（分钟） */
    @Value("${big.auth.security.lock-minutes:10}")
    private long lockMinutes = 10;
    /** 同一 IP 登录失败次数上限（防止换账号爆破，也防止恶意锁死他人账号） */
    @Value("${big.auth.security.max-ip-fail-count:20}")
    private int maxIpFailCount = 20;
    /** IP 维度失败计数窗口（分钟） */
    @Value("${big.auth.security.ip-fail-window-minutes:10}")
    private long ipFailWindowMinutes = 10;
    /** 同一 IP 每分钟验证码请求上限 */
    @Value("${big.auth.security.max-captcha-per-minute:30}")
    private int maxCaptchaPerMinute = 30;

    private static final String CAPTCHA_KEY_PREFIX = "auth:captcha:";
    private static final String LOGIN_FAIL_PREFIX = "auth:login:fail:";
    private static final String LOGIN_LOCK_PREFIX = "auth:login:lock:";
    private static final String CAPTCHA_IP_PREFIX = "auth:captcha:ip:";
    private static final String LOGIN_IP_FAIL_PREFIX = "auth:login:ip:";

    private final SysUserMapper userMapper;
    private final StringRedisTemplate redisTemplate;

    @Override
    public Map<String, String> captcha() {
        String ip = resolveClientIp();
        checkIpRateLimit(CAPTCHA_IP_PREFIX + ip, maxCaptchaPerMinute, 1,
                "验证码请求过于频繁，请稍后再试");

        SpecCaptcha captcha = new SpecCaptcha(130, 48, 4);
        String key = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(CAPTCHA_KEY_PREFIX + key,
                captcha.text().toLowerCase(), CAPTCHA_TTL_MINUTES, TimeUnit.MINUTES);
        Map<String, String> data = new HashMap<>(2);
        data.put("captchaKey", key);
        data.put("captchaBase64", captcha.toBase64());
        return data;
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        String ip = resolveClientIp();
        if (isIpBlocked(ip)) {
            log.warn("IP {} 登录尝试过于频繁，已临时拒绝", ip);
            throw new BusinessException("429", "登录尝试过于频繁，请稍后再试");
        }

        verifyCaptcha(dto.getCaptchaKey(), dto.getCaptchaCode());

        String username = dto.getUsername();
        if (Boolean.TRUE.equals(redisTemplate.hasKey(LOGIN_LOCK_PREFIX + username))) {
            throw new BusinessException("429", "登录失败次数过多，账号已锁定，请稍后再试");
        }

        SysUser user = userMapper.selectByUsernameWithRoles(username);
        // 用户不存在与密码错误统一报错并计数，避免暴露账号是否存在
        if (user == null || !verifyPassword(dto.getPassword(), user.getPassword())) {
            recordLoginFail(username, ip);
            throw new BusinessException("401", "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new BusinessException("403", "账号已被禁用");
        }
        clearLoginFail(username);

        StpUtil.login(user.getId());
        List<String> roles = userMapper.selectRoleCodesByUserId(user.getId());
        List<String> permissions = userMapper.selectPermissionCodesByUserId(user.getId());

        // 只更新 last_login_time 单列，避免整行回写在并发下覆盖其他字段变更
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, user.getId())
                .set(SysUser::getLastLoginTime, LocalDateTime.now()));

        log.info("用户 {} 登录成功", username);

        LoginVO vo = new LoginVO();
        vo.setToken(StpUtil.getTokenValue());
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setRoles(roles != null ? roles : Collections.emptyList());
        vo.setPermissions(permissions != null ? permissions : Collections.emptyList());
        vo.setTokenTimeout(StpUtil.getTokenTimeout());
        return vo;
    }

    @Override
    public void logout() {
        long userId = StpUtil.getLoginIdAsLong();
        StpUtil.logout();
        log.info("用户 {} 登出成功", userId);
    }

    @Override
    public LoginVO getCurrentUser() {
        long userId = StpUtil.getLoginIdAsLong();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("401", "用户不存在");
        }
        List<String> roles = userMapper.selectRoleCodesByUserId(userId);
        List<String> permissions = userMapper.selectPermissionCodesByUserId(userId);

        LoginVO vo = new LoginVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setRoles(roles != null ? roles : Collections.emptyList());
        vo.setPermissions(permissions != null ? permissions : Collections.emptyList());
        vo.setTokenTimeout(StpUtil.getTokenTimeout());
        return vo;
    }

    /** 校验验证码；无论对错用完即作废，防止同一验证码被暴力复用 */
    private void verifyCaptcha(String captchaKey, String captchaCode) {
        if (!StringUtils.hasText(captchaKey) || !StringUtils.hasText(captchaCode)) {
            throw new BusinessException("400", "验证码不能为空");
        }
        String redisKey = CAPTCHA_KEY_PREFIX + captchaKey;
        String cached = redisTemplate.opsForValue().get(redisKey);
        redisTemplate.delete(redisKey);
        if (cached == null || !cached.equalsIgnoreCase(captchaCode.trim())) {
            throw new BusinessException("400", "验证码错误或已过期");
        }
    }

    private void recordLoginFail(String username, String ip) {
        String failKey = LOGIN_FAIL_PREFIX + username;
        Long count = redisTemplate.opsForValue().increment(failKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(failKey, lockMinutes, TimeUnit.MINUTES);
        }
        if (count != null && count >= maxFailCount) {
            redisTemplate.opsForValue().set(LOGIN_LOCK_PREFIX + username, "1", lockMinutes, TimeUnit.MINUTES);
            redisTemplate.delete(failKey);
            log.warn("账号 {} 连续登录失败 {} 次，锁定 {} 分钟", username, maxFailCount, lockMinutes);
        }
        recordIpFail(ip);
    }

    /** IP 维度失败计数：不锁具体账号，只限制该来源在窗口期内的登录尝试总量 */
    private void recordIpFail(String ip) {
        String ipKey = LOGIN_IP_FAIL_PREFIX + ip;
        Long count = redisTemplate.opsForValue().increment(ipKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(ipKey, ipFailWindowMinutes, TimeUnit.MINUTES);
        }
        if (count != null && count >= maxIpFailCount) {
            log.warn("IP {} 登录失败达到 {} 次，{} 分钟内拒绝其登录请求",
                    ip, maxIpFailCount, ipFailWindowMinutes);
        }
    }

    private boolean isIpBlocked(String ip) {
        String value = redisTemplate.opsForValue().get(LOGIN_IP_FAIL_PREFIX + ip);
        if (value == null) {
            return false;
        }
        try {
            return Long.parseLong(value) >= maxIpFailCount;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** 固定窗口限流：首次写入时设置窗口 TTL，超限直接抛出 429 */
    private void checkIpRateLimit(String key, int max, long windowMinutes, String message) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, windowMinutes, TimeUnit.MINUTES);
        }
        if (count != null && count > max) {
            throw new BusinessException("429", message);
        }
    }

    /**
     * 解析客户端 IP。
     * <p>
     * 经网关转发时 Spring Cloud Gateway 会把真实来源追加到 X-Forwarded-For 末尾，
     * 而首段可被客户端伪造（用于绕过按 IP 的限流），因此这里取最后一段。
     */
    private String resolveClientIp() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }
        HttpServletRequest request = attributes.getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            String[] parts = ip.split(",");
            ip = parts[parts.length - 1].trim();
        }
        return StringUtils.hasText(ip) ? ip : "unknown";
    }

    private void clearLoginFail(String username) {
        redisTemplate.delete(LOGIN_FAIL_PREFIX + username);
        redisTemplate.delete(LOGIN_LOCK_PREFIX + username);
    }

    /** 支持 {bcrypt} 前缀与裸 $2a/$2b/$2y bcrypt 散列（存量库为裸格式）；明文或不识别格式一律判定失败 */
    private boolean verifyPassword(String raw, String stored) {
        if (stored == null) {
            return false;
        }
        String hash = stored.startsWith("{bcrypt}") ? stored.substring(8) : stored;
        if (!hash.startsWith("$2a$") && !hash.startsWith("$2b$") && !hash.startsWith("$2y$")) {
            return false;
        }
        try {
            return BCrypt.checkpw(raw, hash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
