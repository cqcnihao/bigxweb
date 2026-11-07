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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
    private static final int MAX_FAIL_COUNT = 5;
    /** 账号锁定时长（分钟） */
    private static final long LOCK_MINUTES = 10;

    private static final String CAPTCHA_KEY_PREFIX = "auth:captcha:";
    private static final String LOGIN_FAIL_PREFIX = "auth:login:fail:";
    private static final String LOGIN_LOCK_PREFIX = "auth:login:lock:";

    private final SysUserMapper userMapper;
    private final StringRedisTemplate redisTemplate;

    @Override
    public Map<String, String> captcha() {
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
        verifyCaptcha(dto.getCaptchaKey(), dto.getCaptchaCode());

        String username = dto.getUsername();
        if (Boolean.TRUE.equals(redisTemplate.hasKey(LOGIN_LOCK_PREFIX + username))) {
            throw new BusinessException("429", "登录失败次数过多，账号已锁定，请稍后再试");
        }

        SysUser user = userMapper.selectByUsernameWithRoles(username);
        // 用户不存在与密码错误统一报错并计数，避免暴露账号是否存在
        if (user == null || !verifyPassword(dto.getPassword(), user.getPassword())) {
            recordLoginFail(username);
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

    private void recordLoginFail(String username) {
        String failKey = LOGIN_FAIL_PREFIX + username;
        Long count = redisTemplate.opsForValue().increment(failKey);
        if (count != null && count == 1L) {
            redisTemplate.expire(failKey, LOCK_MINUTES, TimeUnit.MINUTES);
        }
        if (count != null && count >= MAX_FAIL_COUNT) {
            redisTemplate.opsForValue().set(LOGIN_LOCK_PREFIX + username, "1", LOCK_MINUTES, TimeUnit.MINUTES);
            redisTemplate.delete(failKey);
            log.warn("账号 {} 连续登录失败 {} 次，锁定 {} 分钟", username, MAX_FAIL_COUNT, LOCK_MINUTES);
        }
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
