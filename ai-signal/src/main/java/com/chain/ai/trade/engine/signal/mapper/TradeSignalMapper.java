package com.chain.ai.trade.engine.signal.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.engine.signal.entity.dos.TradeSignal;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 交易信号Mapper接口
 */
@Mapper
public interface TradeSignalMapper extends BaseMapper<TradeSignal> {

    /**
     * 根据技术信号ID列表物理删除关联的交易信号（绕过@TableLogic逻辑删除）
     */
    @Delete("<script>DELETE FROM trade_signal WHERE technical_signal_id IN " +
            "<foreach item='id' collection='ids' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int deletePhysicalByTechnicalSignalIds(@Param("ids") List<Long> ids);

}
