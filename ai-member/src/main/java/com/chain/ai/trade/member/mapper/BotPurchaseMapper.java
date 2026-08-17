package com.chain.ai.trade.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.member.entity.BotPurchase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 机器人购买记录Mapper接口
 */
@Mapper
public interface BotPurchaseMapper extends BaseMapper<BotPurchase> {

    @Update("UPDATE bot_purchases SET last_sync_time = NOW() WHERE id = #{id}")
    int updateSyncTime(@Param("id") Long id);
}
