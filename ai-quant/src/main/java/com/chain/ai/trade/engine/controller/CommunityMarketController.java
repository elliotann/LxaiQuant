package com.chain.ai.trade.engine.controller;

import com.chain.ai.trade.common.utils.BeanUtil;
import com.chain.ai.trade.engine.strategy.entity.dos.TradingBot;
import com.chain.ai.trade.engine.strategy.service.ITradingBotService;
import com.chain.ai.trade.member.dto.communitymarket.*;
import com.chain.ai.trade.member.entity.CommunityMarketListing;
import com.chain.ai.trade.member.mapper.CommunityMarketListingMapper;
import com.chain.ai.trade.member.service.ICommunityMarketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 社区市场 Controller
 * 支持多类型商品：bot/indicator/strategy/signal
 */
@RestController
@RequestMapping("/api/community-market")
@RequiredArgsConstructor
@Slf4j
public class CommunityMarketController {

    private final ICommunityMarketService communityMarketService;
    private final ITradingBotService tradingBotService;
    private final CommunityMarketListingMapper communityMarketListingMapper;

    // ==================== 市场浏览 ====================

    /**
     * 获取市场列表（支持按类型/价格/标签筛选）
     */
    @GetMapping("/listings")
    public Map<String, Object> listListings(CommunityMarketQuery query, Authentication auth) {
        String userId = auth != null ? (String) auth.getPrincipal() : null;
        return success(communityMarketService.listListings(query, userId));
    }

    /**
     * 获取商品详情
     */
    @GetMapping("/listings/{id}")
    public Map<String, Object> getListingDetail(@PathVariable Long id, Authentication auth) {
        String userId = auth != null ? (String) auth.getPrincipal() : null;
        return success(communityMarketService.getListingDetail(id, userId));
    }

    /**
     * 获取表现数据
     */
    @GetMapping("/listings/{id}/performance")
    public Map<String, Object> getPerformance(@PathVariable Long id) {
        CommunityMarketDetailVO detail = communityMarketService.getListingDetail(id, null);
        return success(detail.getPerformance());
    }

    /**
     * 获取评论列表
     */
    @GetMapping("/listings/{id}/comments")
    public Map<String, Object> getComments(@PathVariable Long id,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int pageSize) {
        return success(communityMarketService.getComments(id, page, pageSize));
    }

    // ==================== 购买 & 同步 ====================

    /**
     * 购买商品，购买成功后为 bot 类型商品创建真实机器人副本
     */
    @PostMapping("/listings/{id}/purchase")
    public Map<String, Object> purchaseListing(@PathVariable Long id, Authentication auth) {
        String userId = (String) auth.getPrincipal();
        communityMarketService.purchaseListing(id, userId);

        // 购买成功后，为 bot 类型商品创建真实的 TradingBot 记录
        try {
            CommunityMarketListing listing = communityMarketListingMapper.selectById(id);
            if (listing != null && "bot".equals(listing.getProductType()) && listing.getSourceId() != null) {
                // sourceId 存的是源机器人主键 id，转 Long 查询
                TradingBot sellBot = tradingBotService.getById(Long.valueOf(listing.getSourceId()));
                if (sellBot == null) {
                    log.warn("源机器人不存在，无法创建副本: sourceId={}", listing.getSourceId());
                    return success(null);
                }
                TradingBot bot = new TradingBot();
                BeanUtil.copyProperties(sellBot, bot);
                bot.setId(null);
                bot.setBotName(listing.getName());
                bot.setUserId(userId);
                // 保留原创建者 ID（用于判断机器人所有权，购买的机器人应为只读）
                bot.setCreatedBy(sellBot.getCreatedBy() != null ? sellBot.getCreatedBy() : sellBot.getUserId());
                String configJson = listing.getConfigSnapshot() != null ? listing.getConfigSnapshot() : sellBot.getConfiguration();
                bot.setConfiguration(configJson);
                bot.setAllocatedCapital(BigDecimal.valueOf(1000));
                bot.setCurrentCapital(BigDecimal.valueOf(1000));
                bot.setEnabled(true);
                bot.setRemark("purchased");
                bot.setAccountId("purchased");

                tradingBotService.createBot(bot);
                log.info("购买机器人成功并创建副本: listingId={}, botId={}, userId={}", id, bot.getBotId(), userId);
            }
        } catch (Exception e) {
            log.warn("创建购买机器人副本失败: listingId={}, userId={}", id, userId, e);
        }

        return success(null);
    }

    /**
     * 同步商品更新
     */
    @PostMapping("/listings/{id}/sync")
    public Map<String, Object> syncListingUpdate(@PathVariable Long id, Authentication auth) {
        String userId = (String) auth.getPrincipal();
        communityMarketService.syncListingUpdate(id, userId);
        return success(null);
    }

    /**
     * 获取我的已购列表
     */
    @GetMapping("/my-purchases")
    public Map<String, Object> getMyPurchases(Authentication auth) {
        String userId = (String) auth.getPrincipal();
        return success(communityMarketService.getMyPurchases(userId));
    }

    // ==================== 发布管理 ====================

    /**
     * 发布商品（提交审核）
     */
    @PostMapping("/listings")
    public Map<String, Object> publishListing(@RequestBody CommunityPublishRequest request, Authentication auth) {
        String userId = (String) auth.getPrincipal();
        Long id = communityMarketService.publishListing(request, userId);
        return success(id);
    }

    /**
     * 获取我发布的商品列表
     */
    @GetMapping("/my-listings")
    public Map<String, Object> getMyListings(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int pageSize,
                                              Authentication auth) {
        String userId = (String) auth.getPrincipal();
        return success(communityMarketService.getMyListings(page, pageSize, userId));
    }

    /**
     * 更新发布信息
     */
    @PutMapping("/listings/{id}")
    public Map<String, Object> updatePublishListing(@PathVariable Long id,
                                                     @RequestBody CommunityPublishRequest request,
                                                     Authentication auth) {
        String userId = (String) auth.getPrincipal();
        communityMarketService.updatePublishListing(id, request, userId);
        return success(null);
    }

    /**
     * 下架自己的商品
     */
    @DeleteMapping("/listings/{id}")
    public Map<String, Object> unpublishListing(@PathVariable Long id, Authentication auth) {
        String userId = (String) auth.getPrincipal();
        communityMarketService.unpublishListing(id, userId);
        return success(null);
    }

    // ==================== 评论 ====================

    /**
     * 获取我的评论
     */
    @GetMapping("/listings/{id}/my-comment")
    public Map<String, Object> getMyComment(@PathVariable Long id, Authentication auth) {
        String userId = (String) auth.getPrincipal();
        return success(communityMarketService.getMyComment(id, userId));
    }

    /**
     * 发表评论/评分
     */
    @PostMapping("/listings/{id}/comments")
    public Map<String, Object> addComment(@PathVariable Long id,
                                           @RequestBody CommunityCommentRequest request,
                                           Authentication auth) {
        String userId = (String) auth.getPrincipal();
        communityMarketService.addComment(id, request, userId);
        return success(null);
    }

    /**
     * 修改评论
     */
    @PutMapping("/listings/{id}/comments/{commentId}")
    public Map<String, Object> updateComment(@PathVariable Long id,
                                              @PathVariable Long commentId,
                                              @RequestBody CommunityCommentRequest request,
                                              Authentication auth) {
        String userId = (String) auth.getPrincipal();
        communityMarketService.updateComment(id, commentId, request, userId);
        return success(null);
    }

    // ==================== 管理后台 ====================

    /**
     * 获取待审核列表（支持按商品类型筛选）
     */
    @GetMapping("/admin/pending")
    public Map<String, Object> getPendingList(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int pageSize,
                                               @RequestParam(required = false) String productType,
                                               Authentication auth) {
        checkAdmin(auth);
        return success(communityMarketService.getPendingList(page, pageSize, productType));
    }

    /**
     * 审核统计
     */
    @GetMapping("/admin/stats")
    public Map<String, Object> getReviewStats(Authentication auth) {
        checkAdmin(auth);
        return success(communityMarketService.getReviewStats());
    }

    /**
     * 审核操作
     */
    @PostMapping("/admin/review")
    public Map<String, Object> reviewListing(@RequestBody CommunityReviewRequest request, Authentication auth) {
        checkAdmin(auth);
        String reviewerId = (String) auth.getPrincipal();
        communityMarketService.reviewListing(request, reviewerId);
        return success(null);
    }

    // ==================== 辅助方法 ====================

    private static Map<String, Object> success(Object data) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "ok");
        result.put("data", data);
        return result;
    }

    private static void checkAdmin(Authentication auth) {
        if (auth == null || !"ADMIN".equals(auth.getDetails())) {
            throw new RuntimeException("无权限访问");
        }
    }
}
