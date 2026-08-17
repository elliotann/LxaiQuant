package com.chain.ai.trade.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.member.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    @Select("SELECT r.* FROM role r " +
            "JOIN user_role_rel ur ON ur.role_id = r.id " +
            "WHERE ur.user_id = #{userId}")
    List<Role> selectByUserId(@Param("userId") String userId);

    @Select("SELECT r.* FROM role r WHERE r.role_code = #{roleCode}")
    Role selectByCode(@Param("roleCode") String roleCode);
}
