package com.chain.ai.trade.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.order.entity.dos.TradeExitBatch;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Mapper;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 交易订单平仓Mapper接口
 */
@Mapper
public interface TradeOrderCloseMapper extends BaseMapper<TradeExitBatch> {

    @Select({
            "<script>",
            "select coalesce(sum(c.income), 0) ",
            "from ai_trade_exit_batch c ",
            "join ai_trade_position o on o.position_id = c.position_id COLLATE utf8mb4_unicode_ci ",
            "where o.robot_id = #{robotId} ",
            "<if test='startTime != null'>",
            "  and c.exit_time <![CDATA[>=]]> #{startTime} ",
            "</if>",
            "<if test='endTime != null'>",
            "  and c.exit_time <![CDATA[<=]]> #{endTime} ",
            "</if>",
            "</script>",
    })
    BigDecimal sumIncomeByRobotId(
            @Param("robotId") String robotId,
            @Param("startTime") Date startTime,
            @Param("endTime") Date endTime
    );

    @Select({
            "<script>",
            "select coalesce(sum(c.income), 0) ",
            "from ai_trade_exit_batch c ",
            "join ai_trade_position o on o.position_id = c.position_id COLLATE utf8mb4_unicode_ci ",
            "where o.account_id = #{accountId} ",
            "<if test='startTime != null'>",
            "  and c.exit_time <![CDATA[>=]]> #{startTime} ",
            "</if>",
            "<if test='endTime != null'>",
            "  and c.exit_time <![CDATA[<=]]> #{endTime} ",
            "</if>",
            "</script>",
    })
    BigDecimal sumIncomeByAccountId(
            @Param("accountId") String accountId,
            @Param("startTime") Date startTime,
            @Param("endTime") Date endTime
    );
}
