package com.chain.ai.trade.engine.controller.market;

import com.chain.ai.trade.common.entity.dto.ApiResponse;
import com.chain.ai.trade.engine.data.entity.dos.UserFavorite;
import com.chain.ai.trade.engine.data.service.IUserFavoriteService;
import com.chain.ai.trade.engine.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户自选股Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/user/favorites")
@RequiredArgsConstructor
public class UserFavoriteController {

    private final IUserFavoriteService userFavoriteService;

    @GetMapping
    public ApiResponse<List<UserFavorite>> getFavorites() {
        String userId = SecurityUtils.getCurrentUserId();
        log.info("获取用户自选股列表, userId={}", userId);
        return ApiResponse.success(userFavoriteService.getByUserId(userId));
    }

    @PostMapping("/{symbolId}")
    public ApiResponse<Void> addFavorite(@PathVariable Integer symbolId) {
        String userId = SecurityUtils.getCurrentUserId();
        log.info("添加自选股, userId={}, symbolId={}", userId, symbolId);
        userFavoriteService.addFavorite(userId, symbolId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{symbolId}")
    public ApiResponse<Void> removeFavorite(@PathVariable Integer symbolId) {
        String userId = SecurityUtils.getCurrentUserId();
        log.info("移除自选股, userId={}, symbolId={}", userId, symbolId);
        userFavoriteService.removeFavorite(userId, symbolId);
        return ApiResponse.success(null);
    }
}
