package com.chain.ai.trade.engine.controller.market;

import com.chain.ai.trade.engine.controller.dto.AiRadarOpportunityDTO;
import com.chain.ai.trade.common.entity.dto.ApiResponse;
import com.chain.ai.trade.engine.service.AiRadarService;
import com.chain.ai.trade.engine.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/ai-radar")
@RequiredArgsConstructor
public class AiRadarController {

    private final AiRadarService aiRadarService;

    @GetMapping("/opportunities")
    public ApiResponse<List<AiRadarOpportunityDTO>> getOpportunities(
            @RequestParam(value = "force", required = false, defaultValue = "false") boolean force) {
        String userId = SecurityUtils.getCurrentUserId();
        log.info("获取AI雷达交易机会, force={}, userId={}", force, userId);
        List<AiRadarOpportunityDTO> opportunities = aiRadarService.scanOpportunities(userId);
        return ApiResponse.success(opportunities);
    }
}
