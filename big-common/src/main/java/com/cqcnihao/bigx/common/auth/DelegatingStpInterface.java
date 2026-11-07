package com.cqcnihao.bigx.common.auth;

import cn.dev33.satoken.stp.StpInterface;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class DelegatingStpInterface implements StpInterface {

    private final AuthStpInterface delegate;

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        if (loginId == null || delegate == null) return Collections.emptyList();
        try {
            List<String> roles = delegate.loadRoles(Long.parseLong(loginId.toString()));
            return roles != null ? roles : new ArrayList<>();
        } catch (Exception e) {
            log.warn("DelegatingStpInterface.getRoleList fail, loginId={}", loginId, e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        if (loginId == null || delegate == null) return Collections.emptyList();
        try {
            List<String> perms = delegate.loadPermissions(Long.parseLong(loginId.toString()));
            return perms != null ? perms : new ArrayList<>();
        } catch (Exception e) {
            log.warn("DelegatingStpInterface.getPermissionList fail, loginId={}", loginId, e);
            return new ArrayList<>();
        }
    }
}