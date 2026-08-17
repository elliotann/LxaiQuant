package com.chain.ai.trade.engine.data.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chain.ai.trade.engine.data.entity.dos.UserFavorite;

import java.util.List;

/**
 * 用户自选股服务接口
 */
public interface IUserFavoriteService extends IService<UserFavorite> {

    /**
     * 获取用户自选股列表
     * @param userId 用户ID
     * @return 自选股列表
     */
    List<UserFavorite> getByUserId(String userId);

    /**
     * 添加自选股
     * @param userId 用户ID
     * @param symbolId 标的ID
     * @return 是否成功
     */
    boolean addFavorite(String userId, Integer symbolId);

    /**
     * 移除自选股
     * @param userId 用户ID
     * @param symbolId 标的ID
     * @return 是否成功
     */
    boolean removeFavorite(String userId, Integer symbolId);

    /**
     * 判断是否已自选
     * @param userId 用户ID
     * @param symbolId 标的ID
     * @return 是否已自选
     */
    boolean isFavorited(String userId, Integer symbolId);
}
