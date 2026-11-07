package com.cqcnihao.bigx.auth.service;

import com.cqcnihao.bigx.auth.dto.LoginDTO;
import com.cqcnihao.bigx.auth.dto.LoginVO;

import java.util.Map;

public interface AuthService {

    /** 生成图形验证码，返回 captchaKey 与 captchaBase64 */
    Map<String, String> captcha();

    LoginVO login(LoginDTO dto);

    void logout();

    LoginVO getCurrentUser();
}