package com.chain.ai.trade.engine.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chain.ai.trade.engine.data.entity.dos.UserFavorite;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户自选股Mapper接口
 */
@Mapper
public interface UserFavoriteMapper extends BaseMapper<UserFavorite> {

}
