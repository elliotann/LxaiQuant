package com.chain.ai.trade.logs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.logs.entity.AccountFundChangeLogEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AccountFundChangeLogMapper extends BaseMapper<AccountFundChangeLogEntity> {
}