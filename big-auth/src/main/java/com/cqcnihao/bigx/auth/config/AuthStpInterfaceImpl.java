package com.cqcnihao.bigx.auth.config;

import com.cqcnihao.bigx.auth.mapper.SysUserMapper;
import com.cqcnihao.bigx.common.auth.AuthStpInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AuthStpInterfaceImpl implements AuthStpInterface {

    private final SysUserMapper userMapper;

    @Override
    public List<String> loadRoles(Long userId) {
        List<String> roles = userMapper.selectRoleCodesByUserId(userId);
        return roles != null ? roles : Collections.emptyList();
    }

    @Override
    public List<String> loadPermissions(Long userId) {
        List<String> perms = userMapper.selectPermissionCodesByUserId(userId);
        return perms != null ? perms : Collections.emptyList();
    }
}