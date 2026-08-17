package com.chain.ai.trade.engine.controller.market;

import com.chain.ai.trade.common.entity.dto.ApiResponse;
import com.chain.ai.trade.engine.data.entity.dos.Symbol;
import com.chain.ai.trade.engine.data.service.ISymbolsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 统一标的查询Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/symbols")
@RequiredArgsConstructor
public class SymbolsController {

    private final ISymbolsService symbolsService;

    @GetMapping
    public ApiResponse<List<Symbol>> getSymbols(
            @RequestParam(value = "market", required = false) String market,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "isHot", required = false) Boolean isHot) {
        log.info("查询标的列表, market={}, keyword={}, isHot={}", market, keyword, isHot);
        if (Boolean.TRUE.equals(isHot)) {
            return ApiResponse.success(symbolsService.getHotSymbols(market));
        }
        return ApiResponse.success(symbolsService.searchSymbols(market, keyword));
    }
}
