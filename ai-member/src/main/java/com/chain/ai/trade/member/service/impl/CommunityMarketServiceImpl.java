package com.chain.ai.trade.member.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chain.ai.trade.member.dto.communitymarket.*;
import com.chain.ai.trade.member.entity.*;
import com.chain.ai.trade.member.mapper.*;
import com.chain.ai.trade.member.service.ICommunityMarketService;
import com.chain.ai.trade.member.service.ICreditsService;
import com.chain.ai.trade.member.service.IUserService;
import com.chain.ai.trade.member.service.ProductMarketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 社区市场服务实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommunityMarketServiceImpl implements ICommunityMarketService {

    private final CommunityMarketListingMapper listingMapper;
    private final CommunityPurchaseMapper purchaseMapper;
    private final CommunityPerformanceMapper performanceMapper;
    private final CommunityCommentMapper commentMapper;
    private final ICreditsService creditsService;
    private final IUserService userService;
    private final List<ProductMarketHandler> productHandlers;

    private static final int PLATFORM_FEE_RATE_PERCENT = 10;

    /** 按类型映射处理器 */
    private Map<String, ProductMarketHandler> handlerMap() {
        return productHandlers.stream()
                .collect(Collectors.toMap(ProductMarketHandler::getProductType, Function.identity()));
    }

    @Override
    public Map<String, Object> listListings(CommunityMarketQuery query, String currentUserId) {
        Page<CommunityMarketListing> page = new Page<>(query.getPage(), query.getPageSize());
        LambdaQueryWrapper<CommunityMarketListing> wrapper = new LambdaQueryWrapper<>();

        // 只显示已上架的
        wrapper.eq(CommunityMarketListing::getStatus, "approved");

        // 商品类型筛选
        if (StringUtils.hasText(query.getProductType())) {
            wrapper.eq(CommunityMarketListing::getProductType, query.getProductType());
        }

        // 关键字搜索（名称+描述）
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w
                    .like(CommunityMarketListing::getName, query.getKeyword())
                    .or()
                    .like(CommunityMarketListing::getDescription, query.getKeyword()));
        }

        // 价格筛选
        if (StringUtils.hasText(query.getPricingType())) {
            wrapper.eq(CommunityMarketListing::getPricingType, query.getPricingType());
        }

        // 标签筛选
        if (StringUtils.hasText(query.getTag())) {
            wrapper.like(CommunityMarketListing::getTags, query.getTag());
        }

        // 排序
        if (StringUtils.hasText(query.getSortBy())) {
            switch (query.getSortBy()) {
                case "newest" -> wrapper.orderByDesc(CommunityMarketListing::getCreatedAt);
                case "popular" -> wrapper.orderByDesc(CommunityMarketListing::getPurchaseCount);
                case "rating" -> wrapper.orderByDesc(CommunityMarketListing::getAvgRating);
                case "price_asc" -> wrapper.orderByAsc(CommunityMarketListing::getPrice);
                case "price_desc" -> wrapper.orderByDesc(CommunityMarketListing::getPrice);
                default -> wrapper.orderByDesc(CommunityMarketListing::getCreatedAt);
            }
        } else {
            wrapper.orderByDesc(CommunityMarketListing::getCreatedAt);
        }

        IPage<CommunityMarketListing> pageResult = listingMapper.selectPage(page, wrapper);

        // 查询当前用户的已购列表
        Set<Long> purchasedIds;
        if (StringUtils.hasText(currentUserId)) {
            List<CommunityPurchase> purchases = purchaseMapper.selectList(
                    new LambdaQueryWrapper<CommunityPurchase>().eq(CommunityPurchase::getUserId, currentUserId));
            purchasedIds = purchases.stream().map(CommunityPurchase::getListingId).collect(Collectors.toSet());
        } else {
            purchasedIds = Collections.emptySet();
        }

        // 组装 VO
        List<CommunityMarketListVO> items = pageResult.getRecords().stream()
                .map(listing -> toListVO(listing, currentUserId, purchasedIds))
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("total", pageResult.getTotal());
        result.put("page", query.getPage());
        result.put("pageSize", query.getPageSize());
        return result;
    }

    @Override
    public CommunityMarketDetailVO getListingDetail(Long id, String currentUserId) {
        CommunityMarketListing listing = listingMapper.selectById(id);
        if (listing == null) {
            throw new RuntimeException("商品不存在");
        }

        // 浏览次数+1
        listingMapper.incrementViewCount(id);

        // 是否已购买
        boolean isPurchased = false;
        if (StringUtils.hasText(currentUserId)) {
            Long count = purchaseMapper.selectCount(
                    new LambdaQueryWrapper<CommunityPurchase>()
                            .eq(CommunityPurchase::getListingId, id)
                            .eq(CommunityPurchase::getUserId, currentUserId));
            isPurchased = count > 0;
        }

        // 表现数据
        CommunityPerformanceVO performanceVO = null;
        CommunityPerformance performance = performanceMapper.selectOne(
                new LambdaQueryWrapper<CommunityPerformance>().eq(CommunityPerformance::getListingId, id));
        if (performance != null) {
            performanceVO = new CommunityPerformanceVO();
            performanceVO.setUsageCount(performance.getUsageCount());
            performanceVO.setPerformanceData(performance.getPerformanceData());
            performanceVO.setUpdatedAt(performance.getUpdatedAt());
        }

        // 评论列表
        List<CommunityComment> comments = commentMapper.selectList(
                new LambdaQueryWrapper<CommunityComment>()
                        .eq(CommunityComment::getListingId, id)
                        .orderByDesc(CommunityComment::getCreatedAt)
                        .last("LIMIT 20"));
        List<CommunityCommentVO> commentVOs = comments.stream().map(this::toCommentVO).collect(Collectors.toList());

        // 组装详情VO
        CommunityMarketDetailVO vo = new CommunityMarketDetailVO();
        vo.setId(listing.getId());
        vo.setProductType(listing.getProductType());
        vo.setName(listing.getName());
        vo.setDescription(listing.getDescription());
        vo.setPreviewImage(listing.getPreviewImage());
        vo.setSourceId(listing.getSourceId());
        vo.setConfigSnapshot(listing.getConfigSnapshot());
        vo.setPricingType(listing.getPricingType());
        vo.setPrice(listing.getPrice());
        vo.setVipFree(listing.getVipFree());
        vo.setAuthorId(listing.getAuthorId());
        setAuthorInfo(vo, listing.getAuthorId());
        vo.setAvgRating(listing.getAvgRating());
        vo.setRatingCount(listing.getRatingCount());
        vo.setPurchaseCount(listing.getPurchaseCount());
        vo.setViewCount(listing.getViewCount());
        vo.setIsOwn(listing.getAuthorId().equals(currentUserId));
        vo.setIsPurchased(isPurchased);
        vo.setHasUpdate(listing.getHasUpdate());
        vo.setStatus(listing.getStatus());
        vo.setTags(listing.getTags());
        vo.setCreatedAt(listing.getCreatedAt());
        vo.setPerformance(performanceVO);
        vo.setComments(commentVOs);
        return vo;
    }

    @Override
    @Transactional
    public void purchaseListing(Long listingId, String userId) {
        CommunityMarketListing listing = listingMapper.selectById(listingId);
        if (listing == null || !"approved".equals(listing.getStatus())) {
            throw new RuntimeException("该商品不可购买");
        }

        // 是否已购买
        Long count = purchaseMapper.selectCount(
                new LambdaQueryWrapper<CommunityPurchase>()
                        .eq(CommunityPurchase::getListingId, listingId)
                        .eq(CommunityPurchase::getUserId, userId));
        if (count > 0) {
            throw new RuntimeException("已购买该商品，请勿重复购买");
        }

        // 如果是付费商品，扣积分
        BigDecimal creditsSpent = BigDecimal.ZERO;
        if ("paid".equals(listing.getPricingType())) {
            boolean vipFree = Boolean.TRUE.equals(listing.getVipFree());
            if (!vipFree || !isVip(userId)) {
                int price = listing.getPrice().intValue();
                boolean deducted = creditsService.deductCredits(userId, price,
                        "COMMUNITY_MARKET_" + listingId, "购买商品：" + listing.getName());
                if (!deducted) {
                    throw new RuntimeException("积分不足");
                }
                creditsSpent = listing.getPrice();

                // 给发布者结算（扣除平台手续费）
                int sellerAmount = price * (100 - PLATFORM_FEE_RATE_PERCENT) / 100;
                creditsService.addCredits(listing.getAuthorId(), sellerAmount,
                        "COMMUNITY_SALE", "COMMUNITY_MARKET_" + listingId,
                        "商品销售分成：" + listing.getName());
            }
        }

        // 创建购买记录
        CommunityPurchase purchase = CommunityPurchase.builder()
                .listingId(listingId)
                .userId(userId)
                .purchaseTime(LocalDateTime.now())
                .creditsSpent(creditsSpent)
                .build();
        purchaseMapper.insert(purchase);

        // 更新购买计数
        listingMapper.incrementPurchaseCount(listingId);

        // 调用商品类型处理器的购买后逻辑
        ProductMarketHandler handler = handlerMap().get(listing.getProductType());
        if (handler != null) {
            handler.onPurchaseComplete(userId, listingId);
        }
    }

    @Override
    @Transactional
    public void syncListingUpdate(Long listingId, String userId) {
        CommunityPurchase purchase = purchaseMapper.selectOne(
                new LambdaQueryWrapper<CommunityPurchase>()
                        .eq(CommunityPurchase::getListingId, listingId)
                        .eq(CommunityPurchase::getUserId, userId));
        if (purchase == null) {
            throw new RuntimeException("未购买该商品，无法同步");
        }

        purchaseMapper.updateSyncTime(purchase.getId());

        // 重置更新标记
        CommunityMarketListing listing = listingMapper.selectById(listingId);
        if (listing != null && Boolean.TRUE.equals(listing.getHasUpdate())) {
            listing.setHasUpdate(false);
            listing.setUpdatedAt(LocalDateTime.now());
            listingMapper.updateById(listing);
        }

        // 调用商品类型处理器的同步逻辑
        ProductMarketHandler handler = handlerMap().get(listing.getProductType());
        if (handler != null) {
            handler.syncUpdate(userId, listingId);
        }
    }

    @Override
    public List<CommunityPurchaseVO> getMyPurchases(String userId) {
        List<CommunityPurchase> purchases = purchaseMapper.selectList(
                new LambdaQueryWrapper<CommunityPurchase>()
                        .eq(CommunityPurchase::getUserId, userId)
                        .orderByDesc(CommunityPurchase::getPurchaseTime));

        return purchases.stream().map(p -> {
            CommunityMarketListing listing = listingMapper.selectById(p.getListingId());
            CommunityPurchaseVO vo = new CommunityPurchaseVO();
            vo.setId(p.getId());
            vo.setListingId(p.getListingId());
            vo.setProductType(listing != null ? listing.getProductType() : null);
            vo.setListingName(listing != null ? listing.getName() : "未知");
            vo.setListingDescription(listing != null ? listing.getDescription() : "");
            vo.setPreviewImage(listing != null ? listing.getPreviewImage() : null);
            vo.setSourceId(listing != null ? listing.getSourceId() : null);
            vo.setConfigSnapshot(listing != null ? listing.getConfigSnapshot() : null);
            vo.setAuthorId(listing != null ? listing.getAuthorId() : null);
            if (listing != null) {
                User author = userService.getById(listing.getAuthorId());
                vo.setAuthorNickname(author != null ? author.getUsername() : "未知");
            }
            vo.setPurchaseTime(p.getPurchaseTime());
            vo.setCreditsSpent(p.getCreditsSpent());
            vo.setLastSyncTime(p.getLastSyncTime());
            vo.setHasUpdate(listing != null ? listing.getHasUpdate() : false);
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getMyListings(int page, int pageSize, String userId) {
        Page<CommunityMarketListing> p = listingMapper.selectPage(
                new Page<>(page, pageSize),
                new LambdaQueryWrapper<CommunityMarketListing>()
                        .eq(CommunityMarketListing::getAuthorId, userId)
                        .orderByDesc(CommunityMarketListing::getCreatedAt));
        List<CommunityMarketListVO> items = p.getRecords().stream()
                .map(listing -> toListVO(listing, userId, Collections.emptySet()))
                .collect(Collectors.toList());
        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("total", p.getTotal());
        return result;
    }

    @Override
    @Transactional
    public Long publishListing(CommunityPublishRequest request, String userId) {
        if (!StringUtils.hasText(request.getProductType())) {
            throw new RuntimeException("请选择商品类型");
        }
        if (!StringUtils.hasText(request.getSourceId())) {
            throw new RuntimeException("请选择要发布的商品");
        }
        if (!StringUtils.hasText(request.getName())) {
            throw new RuntimeException("请输入商品名称");
        }

        // 验证源商品
        ProductMarketHandler handler = handlerMap().get(request.getProductType());
        if (handler != null) {
            handler.validateSource(request.getSourceId(), userId);
        }

        // 获取配置快照
        String configSnapshot = handler != null ? handler.getConfigSnapshot(request.getSourceId()) : null;

        CommunityMarketListing listing = CommunityMarketListing.builder()
                .productType(request.getProductType())
                .sourceId(request.getSourceId())
                .name(request.getName())
                .description(request.getDescription())
                .previewImage(request.getPreviewImage())
                .configSnapshot(configSnapshot)
                .pricingType(request.getPricingType() != null ? request.getPricingType() : "free")
                .price(request.getPrice() != null ? request.getPrice() : BigDecimal.ZERO)
                .vipFree(request.getVipFree() != null ? request.getVipFree() : false)
                .authorId(userId)
                .status("pending")
                .viewCount(0)
                .purchaseCount(0)
                .avgRating(BigDecimal.ZERO)
                .ratingCount(0)
                .hasUpdate(false)
                .tags(request.getTags() != null ? String.join(",", request.getTags()) : null)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        listingMapper.insert(listing);
        return listing.getId();
    }

    @Override
    @Transactional
    public void updatePublishListing(Long id, CommunityPublishRequest request, String userId) {
        CommunityMarketListing listing = listingMapper.selectById(id);
        if (listing == null || !listing.getAuthorId().equals(userId)) {
            throw new RuntimeException("无权修改该发布信息");
        }

        listing.setName(request.getName());
        listing.setDescription(request.getDescription());
        listing.setPreviewImage(request.getPreviewImage());
        listing.setPricingType(request.getPricingType());
        listing.setPrice(request.getPrice());
        listing.setVipFree(request.getVipFree());
        listing.setTags(request.getTags() != null ? String.join(",", request.getTags()) : null);
        if ("rejected".equals(listing.getStatus())) {
            listing.setStatus("pending");
        }
        listing.setUpdatedAt(LocalDateTime.now());
        listingMapper.updateById(listing);
    }

    @Override
    @Transactional
    public void unpublishListing(Long id, String userId) {
        CommunityMarketListing listing = listingMapper.selectById(id);
        if (listing == null || !listing.getAuthorId().equals(userId)) {
            throw new RuntimeException("无权下架该商品");
        }
        listing.setStatus("offline");
        listing.setUpdatedAt(LocalDateTime.now());
        listingMapper.updateById(listing);
    }

    @Override
    public Map<String, Object> getPendingList(int page, int pageSize, String productType) {
        Page<CommunityMarketListing> pageObj = new Page<>(page, pageSize);
        LambdaQueryWrapper<CommunityMarketListing> wrapper = new LambdaQueryWrapper<CommunityMarketListing>()
                .eq(CommunityMarketListing::getStatus, "pending")
                .orderByDesc(CommunityMarketListing::getCreatedAt);

        if (StringUtils.hasText(productType)) {
            wrapper.eq(CommunityMarketListing::getProductType, productType);
        }

        IPage<CommunityMarketListing> pageResult = listingMapper.selectPage(pageObj, wrapper);

        List<Map<String, Object>> items = pageResult.getRecords().stream().map(listing -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", listing.getId());
            item.put("productType", listing.getProductType());
            item.put("name", listing.getName());
            item.put("description", listing.getDescription());
            item.put("pricingType", listing.getPricingType());
            item.put("price", listing.getPrice());
            item.put("authorId", listing.getAuthorId());
            User author = userService.getById(listing.getAuthorId());
            item.put("authorNickname", author != null ? author.getUsername() : "未知");
            item.put("createdAt", listing.getCreatedAt());
            item.put("sourceId", listing.getSourceId());
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("total", pageResult.getTotal());
        return result;
    }

    @Override
    public Map<String, Object> getReviewStats() {
        long pendingCount = listingMapper.selectCount(
                new LambdaQueryWrapper<CommunityMarketListing>().eq(CommunityMarketListing::getStatus, "pending"));
        long approvedCount = listingMapper.selectCount(
                new LambdaQueryWrapper<CommunityMarketListing>().eq(CommunityMarketListing::getStatus, "approved"));
        long rejectedCount = listingMapper.selectCount(
                new LambdaQueryWrapper<CommunityMarketListing>().eq(CommunityMarketListing::getStatus, "rejected"));

        Map<String, Object> stats = new HashMap<>();
        stats.put("pending", pendingCount);
        stats.put("approved", approvedCount);
        stats.put("rejected", rejectedCount);
        return stats;
    }

    @Override
    @Transactional
    public void reviewListing(CommunityReviewRequest request, String reviewerId) {
        CommunityMarketListing listing = listingMapper.selectById(request.getId());
        if (listing == null) {
            throw new RuntimeException("商品不存在");
        }
        if (!"pending".equals(listing.getStatus())) {
            throw new RuntimeException("该商品已被审核");
        }

        if ("approved".equals(request.getAction())) {
            listing.setStatus("approved");
        } else if ("rejected".equals(request.getAction())) {
            listing.setStatus("rejected");
        } else {
            throw new RuntimeException("无效的审核操作");
        }

        listing.setReviewNote(request.getNote());
        listing.setReviewerId(reviewerId);
        listing.setReviewedAt(LocalDateTime.now());
        listing.setUpdatedAt(LocalDateTime.now());
        listingMapper.updateById(listing);
    }

    @Override
    public List<CommunityCommentVO> getComments(Long listingId, int page, int pageSize) {
        Page<CommunityComment> pageObj = new Page<>(page, pageSize);
        IPage<CommunityComment> pageResult = commentMapper.selectPage(pageObj,
                new LambdaQueryWrapper<CommunityComment>()
                        .eq(CommunityComment::getListingId, listingId)
                        .orderByDesc(CommunityComment::getCreatedAt));
        return pageResult.getRecords().stream().map(this::toCommentVO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void addComment(Long listingId, CommunityCommentRequest request, String userId) {
        if (request.getRating() != null && (request.getRating() < 1 || request.getRating() > 5)) {
            throw new RuntimeException("评分范围为1-5");
        }

        CommunityComment comment = CommunityComment.builder()
                .listingId(listingId)
                .userId(userId)
                .rating(request.getRating())
                .content(request.getContent())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        commentMapper.insert(comment);

        recalcRating(listingId);
    }

    @Override
    @Transactional
    public void updateComment(Long listingId, Long commentId, CommunityCommentRequest request, String userId) {
        CommunityComment comment = commentMapper.selectById(commentId);
        if (comment == null || !comment.getUserId().equals(userId)) {
            throw new RuntimeException("无权修改该评论");
        }
        if (request.getRating() != null && (request.getRating() < 1 || request.getRating() > 5)) {
            throw new RuntimeException("评分范围为1-5");
        }

        comment.setRating(request.getRating());
        comment.setContent(request.getContent());
        comment.setUpdatedAt(LocalDateTime.now());
        commentMapper.updateById(comment);

        recalcRating(listingId);
    }

    @Override
    public CommunityCommentVO getMyComment(Long listingId, String userId) {
        CommunityComment comment = commentMapper.selectOne(
                new LambdaQueryWrapper<CommunityComment>()
                        .eq(CommunityComment::getListingId, listingId)
                        .eq(CommunityComment::getUserId, userId));
        return comment != null ? toCommentVO(comment) : null;
    }

    // =================== 私有辅助方法 ===================

    private CommunityMarketListVO toListVO(CommunityMarketListing listing, String currentUserId, Set<Long> purchasedIds) {
        CommunityMarketListVO vo = new CommunityMarketListVO();
        vo.setId(listing.getId());
        vo.setProductType(listing.getProductType());
        vo.setName(listing.getName());
        vo.setDescription(listing.getDescription());
        vo.setSourceId(listing.getSourceId());
        vo.setPreviewImage(listing.getPreviewImage());
        vo.setPricingType(listing.getPricingType());
        vo.setPrice(listing.getPrice());
        vo.setVipFree(listing.getVipFree());
        vo.setAuthorId(listing.getAuthorId());
        setAuthorInfo(vo, listing.getAuthorId());
        vo.setAvgRating(listing.getAvgRating());
        vo.setRatingCount(listing.getRatingCount());
        vo.setPurchaseCount(listing.getPurchaseCount());
        vo.setViewCount(listing.getViewCount());
        vo.setIsOwn(listing.getAuthorId().equals(currentUserId));
        vo.setIsPurchased(purchasedIds.contains(listing.getId()));
        vo.setHasUpdate(listing.getHasUpdate());
        vo.setStatus(listing.getStatus());
        vo.setTags(listing.getTags());
        vo.setCreatedAt(listing.getCreatedAt());
        return vo;
    }

    private void setAuthorInfo(CommunityMarketListVO vo, String authorId) {
        User author = userService.getById(authorId);
        if (author != null) {
            vo.setAuthorNickname(author.getUsername());
            vo.setAuthorAvatar(null);
        } else {
            vo.setAuthorNickname("未知");
        }
    }

    private void setAuthorInfo(CommunityMarketDetailVO vo, String authorId) {
        User author = userService.getById(authorId);
        if (author != null) {
            vo.setAuthorNickname(author.getUsername());
            vo.setAuthorAvatar(null);
        } else {
            vo.setAuthorNickname("未知");
        }
    }

    private CommunityCommentVO toCommentVO(CommunityComment comment) {
        CommunityCommentVO vo = new CommunityCommentVO();
        vo.setId(comment.getId());
        vo.setUserId(comment.getUserId());
        User user = userService.getById(comment.getUserId());
        vo.setUserNickname(user != null ? user.getUsername() : "未知");
        vo.setUserAvatar(null);
        vo.setRating(comment.getRating());
        vo.setContent(comment.getContent());
        vo.setCreatedAt(comment.getCreatedAt());
        vo.setUpdatedAt(comment.getUpdatedAt());
        return vo;
    }

    private void recalcRating(Long listingId) {
        List<CommunityComment> comments = commentMapper.selectList(
                new LambdaQueryWrapper<CommunityComment>().eq(CommunityComment::getListingId, listingId));
        CommunityMarketListing listing = listingMapper.selectById(listingId);
        if (listing == null) return;

        if (comments.isEmpty()) {
            listing.setAvgRating(BigDecimal.ZERO);
            listing.setRatingCount(0);
        } else {
            double avg = comments.stream()
                    .filter(c -> c.getRating() != null)
                    .mapToInt(CommunityComment::getRating)
                    .average()
                    .orElse(0);
            listing.setAvgRating(BigDecimal.valueOf(Math.round(avg * 100.0) / 100.0));
            listing.setRatingCount(comments.size());
        }
        listingMapper.updateById(listing);
    }

    private boolean isVip(String userId) {
        User user = userService.getById(userId);
        return user != null && "VIP".equals(user.getMembershipLevel());
    }
}
