package com.cqcnihao.bigx.auth.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class LoginVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String token;

    private Long userId;

    private String username;

    private String nickname;

    private List<String> roles;

    private List<String> permissions;

    private Long tokenTimeout;
}