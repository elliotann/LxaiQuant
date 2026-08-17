package com.chain.ai.trade.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.member.entity.Permission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {

    @Select("SELECT p.* FROM permission p " +
            "JOIN role_permission rp ON p.id = rp.permission_id " +
            "JOIN role r ON rp.role_id = r.id " +
            "JOIN user_role_rel ur ON ur.role_id = r.id " +
            "WHERE ur.user_id = #{userId}")
    List<Permission> selectByUserId(@Param("userId") String userId);

    @Select("SELECT p.* FROM permission p " +
            "JOIN role_permission rp ON p.id = rp.permission_id " +
            "WHERE rp.role_id = #{roleId}")
    List<Permission> selectByRoleId(@Param("roleId") Integer roleId);
}
