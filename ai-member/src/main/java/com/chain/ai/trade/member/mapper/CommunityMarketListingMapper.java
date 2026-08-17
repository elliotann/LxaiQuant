package com.chain.ai.trade.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.member.entity.CommunityMarketListing;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 社区市场商品Mapper
 */
@Mapper
public interface CommunityMarketListingMapper extends BaseMapper<CommunityMarketListing> {

    @Update("UPDATE community_market_listings SET view_count = view_count + 1 WHERE id = #{id}")
    int incrementViewCount(@Param("id") Long id);

    @Update("UPDATE community_market_listings SET purchase_count = purchase_count + 1 WHERE id = #{id}")
    int incrementPurchaseCount(@Param("id") Long id);
}
