package com.chain.ai.trade.engine.controller.market;

import com.chain.ai.trade.engine.controller.dto.TrendlineData;
import com.chain.ai.trade.engine.controller.dto.TrendlineParams;
import com.chain.ai.trade.engine.controller.dto.TrendlineRequest;
import com.chain.ai.trade.engine.controller.dto.TrendlineResponse;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.param.KlineParam;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import com.chain.ai.trade.engine.data.utils.IndicatorWrapHelper;
import com.chain.ai.trade.engine.service.TrendlineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ta4j.core.BarSeries;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/kline")
public class TrendlineController {

    private final ICandlestickService candlestickService;
    private final TrendlineService trendlineService;

    @PostMapping("/trendline")
    public ResponseEntity<TrendlineResponse> getTrendlines(@RequestBody TrendlineRequest request) {
        TrendlineResponse response = new TrendlineResponse();
        try {
            String symbol = request.getSymbol();
            String interval = request.getInterval();
            int size = Math.min(request.getSize(), 2000);
            List<String> indicators = request.getIndicators();
            TrendlineParams params = request.getParams();

            if (symbol == null || symbol.isBlank()) {
                response.setSuccess(false);
                return ResponseEntity.badRequest().body(response);
            }
            if (indicators == null || indicators.isEmpty()) {
                response.setSuccess(true);
                response.setData(new TrendlineData());
                return ResponseEntity.ok(response);
            }
            if (params == null) {
                params = new TrendlineParams();
            }

            CandlestickIntervalEnum intervalEnum = parseInterval(interval);
            if (intervalEnum == null) {
                response.setSuccess(false);
                return ResponseEntity.badRequest().body(response);
            }

            KlineParam klineParam = KlineParam.builder()
                    .symbol(symbol)
                    .klineInterval(intervalEnum)
                    .size(size)
                    .build();
            List<Candlestick> klines = candlestickService.getLastKlines(klineParam);
            if (klines == null || klines.isEmpty()) {
                response.setSuccess(true);
                response.setData(new TrendlineData());
                return ResponseEntity.ok(response);
            }

            BarSeries series = IndicatorWrapHelper.buildSeries(klines);
            TrendlineData data = trendlineService.calculateTrendlines(series, indicators, params);
            response.setSuccess(true);
            response.setData(data);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("计算趋势线异常", e);
            response.setSuccess(false);
            return ResponseEntity.internalServerError().body(response);
        }
    }

    private CandlestickIntervalEnum parseInterval(String interval) {
        if (interval == null || interval.isBlank()) {
            return null;
        }
        try {
            return CandlestickIntervalEnum.valueOf(interval);
        } catch (IllegalArgumentException ignored) {
            for (CandlestickIntervalEnum value : CandlestickIntervalEnum.values()) {
                if (interval.equalsIgnoreCase(value.getCode())) {
                    return value;
                }
            }
            return null;
        }
    }
}
