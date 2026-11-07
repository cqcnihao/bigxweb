package com.cqcnihao.bigx.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class LoginDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 32, message = "用户名长度 2-32 位")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 4, max = 128, message = "密码长度 4-128 位")
    private String password;

    private String captchaCode;

    private String captchaKey;
}