package com.cqcnihao.bigx.common.auth;

import cn.dev33.satoken.stp.StpInterface;

import java.util.List;

public interface AuthStpInterface {

    List<String> loadRoles(Long userId);

    List<String> loadPermissions(Long userId);
}