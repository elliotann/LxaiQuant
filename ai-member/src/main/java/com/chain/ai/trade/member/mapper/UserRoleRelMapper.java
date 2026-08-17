package com.chain.ai.trade.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.member.annotation.DataScope;
import com.chain.ai.trade.member.entity.UserRoleRel;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@DataScope(field = "user_id")
public interface UserRoleRelMapper extends BaseMapper<UserRoleRel> {
}
