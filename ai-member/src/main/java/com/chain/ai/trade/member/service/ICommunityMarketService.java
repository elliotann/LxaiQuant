package com.chain.ai.trade.member.service;

import com.chain.ai.trade.member.dto.communitymarket.*;

import java.util.List;
import java.util.Map;

/**
 * 社区市场服务接口
 */
public interface ICommunityMarketService {

    /** 分页查询市场列表 */
    Map<String, Object> listListings(CommunityMarketQuery query, String currentUserId);

    /** 查询商品详情 */
    CommunityMarketDetailVO getListingDetail(Long id, String currentUserId);

    /** 购买商品 */
    void purchaseListing(Long listingId, String userId);

    /** 同步商品更新 */
    void syncListingUpdate(Long listingId, String userId);

    /** 获取我的已购列表 */
    List<CommunityPurchaseVO> getMyPurchases(String userId);

    /** 分页查询我发布的商品 */
    Map<String, Object> getMyListings(int page, int pageSize, String userId);

    /** 发布商品 */
    Long publishListing(CommunityPublishRequest request, String userId);

    /** 更新发布信息 */
    void updatePublishListing(Long id, CommunityPublishRequest request, String userId);

    /** 下架自己的商品 */
    void unpublishListing(Long id, String userId);

    /** 获取审核待处理列表 */
    Map<String, Object> getPendingList(int page, int pageSize, String productType);

    /** 审核统计 */
    Map<String, Object> getReviewStats();

    /** 审核操作 */
    void reviewListing(CommunityReviewRequest request, String reviewerId);

    /** 获取评论列表 */
    List<CommunityCommentVO> getComments(Long listingId, int page, int pageSize);

    /** 发表评论/评分 */
    void addComment(Long listingId, CommunityCommentRequest request, String userId);

    /** 修改评论 */
    void updateComment(Long listingId, Long commentId, CommunityCommentRequest request, String userId);

    /** 获取我的评论 */
    CommunityCommentVO getMyComment(Long listingId, String userId);
}
