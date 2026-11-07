package com.cqcnihao.bigx.auth.config;

import com.cqcnihao.bigx.auth.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * AuthStpInterfaceImpl 单元测试
 * <p>
 * 该类是 Sa-Token 校验角色/权限时的数据来源（DelegatingStpInterface → AuthStpInterface），
 * 重点验证 mapper 返回 null 时不会把 null 泄漏给 Sa-Token，从而避免鉴权时 NPE。
 */
class AuthStpInterfaceImplTest {

    private SysUserMapper userMapper;
    private AuthStpInterfaceImpl stpInterface;

    @BeforeEach
    void setUp() {
        userMapper = mock(SysUserMapper.class);
        stpInterface = new AuthStpInterfaceImpl(userMapper);
    }

    @Test
    void loadRoles_shouldReturnMapperResult() {
        when(userMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of("admin", "user"));

        assertEquals(List.of("admin", "user"), stpInterface.loadRoles(1L));
    }

    @Test
    void loadRoles_shouldReturnEmptyListWhenMapperReturnsNull() {
        when(userMapper.selectRoleCodesByUserId(1L)).thenReturn(null);

        List<String> roles = stpInterface.loadRoles(1L);

        assertNotNull(roles);
        assertTrue(roles.isEmpty());
    }

    @Test
    void loadPermissions_shouldReturnMapperResult() {
        when(userMapper.selectPermissionCodesByUserId(1L)).thenReturn(List.of("product:read"));

        assertEquals(List.of("product:read"), stpInterface.loadPermissions(1L));
    }

    @Test
    void loadPermissions_shouldReturnEmptyListWhenMapperReturnsNull() {
        when(userMapper.selectPermissionCodesByUserId(1L)).thenReturn(null);

        List<String> permissions = stpInterface.loadPermissions(1L);

        assertNotNull(permissions);
        assertTrue(permissions.isEmpty());
    }
}
