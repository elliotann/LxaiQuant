package com.chain.ai.trade.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.member.entity.CommunityPurchase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 社区市场购买记录Mapper
 */
@Mapper
public interface CommunityPurchaseMapper extends BaseMapper<CommunityPurchase> {

    @Update("UPDATE community_purchases SET last_sync_time = NOW() WHERE id = #{id}")
    int updateSyncTime(@Param("id") Long id);
}
