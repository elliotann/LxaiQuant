package com.chain.ai.trade.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.member.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 用户Mapper接口
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    User selectByUsername(String username);

    User selectByEmail(String email);

    User selectByPhone(String phone);

    @Update("UPDATE user SET credits_balance = credits_balance - #{cost}, version = version + 1 " +
            "WHERE user_id = #{userId} AND credits_balance >= #{cost}")
    int deductCredits(@Param("userId") String userId, @Param("cost") int cost);

    @Update("UPDATE user SET credits_balance = credits_balance + #{amount}, version = version + 1 " +
            "WHERE user_id = #{userId}")
    int addCredits(@Param("userId") String userId, @Param("amount") int amount);
}
