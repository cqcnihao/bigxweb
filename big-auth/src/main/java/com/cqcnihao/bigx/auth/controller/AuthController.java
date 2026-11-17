package com.cqcnihao.bigx.auth.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.cqcnihao.bigx.auth.dto.LoginDTO;
import com.cqcnihao.bigx.auth.dto.LoginVO;
import com.cqcnihao.bigx.auth.service.AuthService;
import com.cqcnihao.bigx.common.Result;
import com.cqcnihao.bigx.encrypt.annotation.SmEncrypt;
import com.cqcnihao.bigx.encrypt.annotation.SmPlain;
import com.cqcnihao.bigx.encrypt.client.CryptoConfigVO;
import com.cqcnihao.bigx.encrypt.service.SmCryptoService;
import com.cqcnihao.bigx.web.common.annotation.OperationLog;
import com.cqcnihao.bigx.web.common.controller.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "认证管理", description = "登录、登出、当前用户信息")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@SmEncrypt
public class AuthController extends BaseController {

    private final AuthService authService;
    /**
     * 延迟注入：关闭加密（BIG_ENCRYPT_ENABLED=false）时 SmCryptoService 不存在，
     * 若用构造器硬依赖会导致整个 auth 服务无法启动。
     */
    private final ObjectProvider<SmCryptoService> cryptoServiceProvider;

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(authService.login(dto));
    }

    @Operation(summary = "用户登出")
    @SaCheckLogin
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout();
        return Result.success();
    }

    @Operation(summary = "当前登录用户信息")
    @SaCheckLogin
    @GetMapping("/me")
    public Result<LoginVO> me() {
        return Result.success(authService.getCurrentUser());
    }

    @SmPlain
    @Operation(summary = "生成图形验证码")
    @GetMapping("/captcha")
    public Result<Map<String, String>> captcha() {
        return Result.success(authService.captcha());
    }

    @SmPlain
    @Operation(summary = "获取国密加密配置（前端动态装载 SM4 密钥 / SM2 公钥）")
    @GetMapping("/public-key")
    @OperationLog(module = "获取国密加密配置", operationType = "查询", description = "获取国密加密配置（前端动态装载 SM4 密钥 / SM2 公钥）")
    public Result<CryptoConfigVO> cryptoConfig() {
        SmCryptoService cryptoService = cryptoServiceProvider.getIfAvailable();
        if (cryptoService == null) {
            // 加密模块未启用：返回空配置，前端据此跳过信封加解密，服务仍可正常登录
            CryptoConfigVO empty = new CryptoConfigVO();
            empty.setMacEnabled(false);
            return Result.success(empty);
        }
        return Result.success(cryptoService.toCryptoConfigVO());
    }
}