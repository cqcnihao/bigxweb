package com.cqcnihao.bigx.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cqcnihao.bigx.auth.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT u.*, GROUP_CONCAT(DISTINCT r.role_code) AS role_codes " +
            "FROM sys_user u " +
            "LEFT JOIN sys_user_role ur ON u.id = ur.user_id " +
            "LEFT JOIN sys_role r ON ur.role_code = r.role_code AND r.status = 1 " +
            "WHERE u.username = #{username} " +
            "GROUP BY u.id")
    SysUser selectByUsernameWithRoles(@Param("username") String username);

    @Select("SELECT r.role_code FROM sys_role r " +
            "INNER JOIN sys_user_role ur ON r.role_code = ur.role_code " +
            "WHERE ur.user_id = #{userId} AND r.status = 1")
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    @Select("SELECT p.perm_code FROM sys_permission p " +
            "INNER JOIN sys_role_permission rp ON p.perm_code = rp.perm_code " +
            "INNER JOIN sys_user_role ur ON rp.role_code = ur.role_code " +
            "WHERE ur.user_id = #{userId} AND p.status = 1")
    List<String> selectPermissionCodesByUserId(@Param("userId") Long userId);
}