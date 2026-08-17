package com.chain.ai.trade.engine.strategy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.engine.strategy.entity.dos.EntryRuleCondition;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EntryRuleConditionMapper extends BaseMapper<EntryRuleCondition> {
}
