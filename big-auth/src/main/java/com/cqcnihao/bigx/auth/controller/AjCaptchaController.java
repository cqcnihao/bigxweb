package com.cqcnihao.bigx.auth.controller;

import com.anji.captcha.model.common.ResponseModel;
import com.anji.captcha.model.vo.CaptchaVO;
import com.anji.captcha.service.CaptchaService;
import com.anji.captcha.util.StringUtils;
import com.cqcnihao.bigx.common.Result;
import com.cqcnihao.bigx.encrypt.annotation.SmPlain;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AJ-Captcha 行为验证码端点（滑动拼图）
 * <p>
 * 复刻官方 {@code com.anji.captcha.controller.CaptchaController}（javax 版无法在 Boot 4 注册），
 * 路径挂在 {@code /auth} 路径族下以复用网关既有路由：
 * <ol>
 *   <li>{@code POST /auth/captcha/get} —— 获取滑动拼图底图与 token；</li>
 *   <li>{@code POST /auth/captcha/check} —— 前端滑块就位后核对，成功换取 captchaVerification 凭证；</li>
 *   <li>登录时携带凭证，由 AuthServiceImpl 调 {@link CaptchaService#verification} 做服务端二次校验
 *       （凭证一次性消费，180 秒有效，防重放）。</li>
 * </ol>
 * 标注 {@link SmPlain}：验证码必须在登录前明文交互，否则陷入「要验签先登录、要登录先验证码」死循环。
 *
 * @author bigweb
 */
@Tag(name = "行为验证码", description = "AJ-Captcha 滑动拼图（登录前交互）")
@SmPlain
@RestController
@RequestMapping("/auth/captcha")
@RequiredArgsConstructor
public class AjCaptchaController {

    private final CaptchaService captchaService;

    @Operation(summary = "获取滑动拼图验证码")
    @PostMapping("/get")
    public Result<ResponseModel> get(@RequestBody CaptchaVO data, HttpServletRequest request) {
        data.setBrowserInfo(getRemoteId(request));
        return Result.success(captchaService.get(data));
    }

    @Operation(summary = "核对滑块位置，成功返回二次校验凭证")
    @PostMapping("/check")
    public Result<ResponseModel> check(@RequestBody CaptchaVO data, HttpServletRequest request) {
        data.setBrowserInfo(getRemoteId(request));
        return Result.success(captchaService.check(data));
    }

    /** 浏览器指纹：IP（优先 X-Forwarded-For 第一段，由网关注入）+ User-Agent，供限流与防重放识别 */
    static String getRemoteId(HttpServletRequest request) {
        String xfwd = request.getHeader("X-Forwarded-For");
        String ip = getRemoteIpFromXfwd(xfwd);
        String ua = request.getHeader("user-agent");
        if (StringUtils.isNotBlank(ip)) {
            return ip + ua;
        }
        return request.getRemoteAddr() + ua;
    }

    private static String getRemoteIpFromXfwd(String xfwd) {
        if (StringUtils.isNotBlank(xfwd)) {
            String[] ipList = xfwd.split(",");
            return StringUtils.trim(ipList[0]);
        }
        return null;
    }
}
