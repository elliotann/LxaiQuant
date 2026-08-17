package com.chain.ai.trade.member.service;

/**
 * 社区市场商品类型策略处理器
 * 每种商品类型（bot/indicator/strategy）实现该接口提供差异化逻辑
 */
public interface ProductMarketHandler {

    /** 返回处理的商品类型 */
    String getProductType();

    /** 验证源商品是否可发布 */
    void validateSource(String sourceId, String userId);

    /** 获取配置快照 */
    String getConfigSnapshot(String sourceId);

    /** 购买后的业务逻辑 */
    void onPurchaseComplete(String userId, Long listingId);

    /** 同步更新 */
    void syncUpdate(String userId, Long listingId);
}
