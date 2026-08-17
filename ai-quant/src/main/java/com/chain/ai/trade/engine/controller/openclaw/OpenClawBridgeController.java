package com.chain.ai.trade.engine.controller.openclaw;

import com.chain.ai.trade.engine.controller.TradingSummaryController;
import com.chain.ai.trade.engine.controller.signal.SignalController;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.param.KlineParam;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import com.chain.ai.trade.common.entity.dto.ApiResponse;
import com.chain.ai.trade.engine.controller.order.ManualController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/openclaw")
@RequiredArgsConstructor
public class OpenClawBridgeController {

    private final TradingSummaryController tradingSummaryController;
    private final ManualController manualController;
    private final SignalController signalController;
    private final ICandlestickService candlestickService;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health(@RequestHeader(value = "X-OpenClaw-Token", required = false) String token) {
        if (!authorized(token)) {
            return ResponseEntity.status(401).body(Map.of("error", "unauthorized"));
        }
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("ts", System.currentTimeMillis());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/risk/status")
    public ResponseEntity<ApiResponse<?>> riskStatus(
            @RequestHeader(value = "X-OpenClaw-Token", required = false) String token,
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String robotId) {
        if (!authorized(token)) {
            return ResponseEntity.status(401).body(ApiResponse.error("unauthorized"));
        }
        return ResponseEntity.ok(tradingSummaryController.getSummary(accountId, robotId, null));
    }

    @PostMapping("/orders/open")
    public ResponseEntity<ApiResponse<Map<String, Object>>> openOrder(
            @RequestHeader(value = "X-OpenClaw-Token", required = false) String token,
            @RequestBody ManualController.ManualOpenRequest req) {
        if (!authorized(token)) {
            return ResponseEntity.status(401).body(ApiResponse.error("unauthorized"));
        }
        return ResponseEntity.ok(manualController.open(req));
    }

    @PostMapping("/orders/close")
    public ResponseEntity<ApiResponse<Map<String, Object>>> closeOrder(
            @RequestHeader(value = "X-OpenClaw-Token", required = false) String token,
            @RequestBody ManualController.ManualCloseRequest req) {
        if (!authorized(token)) {
            return ResponseEntity.status(401).body(ApiResponse.error("unauthorized"));
        }
        return ResponseEntity.ok(manualController.close(req));
    }

    @GetMapping("/signals")
    public ResponseEntity<Map<String, Object>> signals(
            @RequestHeader(value = "X-OpenClaw-Token", required = false) String token,
            @RequestParam String symbol,
            @RequestParam(required = false, defaultValue = "3m") String interval,
            @RequestParam(required = false) String indicatorType,
            @RequestParam(required = false, defaultValue = "1") Integer pageNumber,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        if (!authorized(token)) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "unauthorized"));
        }
        return signalController.getSignalsData(null, null, symbol, interval, interval, indicatorType, pageNumber, pageSize, null, null);
    }

    @GetMapping("/price/latest")
    public ResponseEntity<ApiResponse<?>> latestPrice(
            @RequestHeader(value = "X-OpenClaw-Token", required = false) String token,
            @RequestParam String symbol,
            @RequestParam(required = false, defaultValue = "3m") String interval
    ) {
        if (!authorized(token)) {
            return ResponseEntity.status(401).body(ApiResponse.error(401, "unauthorized"));
        }
        String sym = normalizeSymbol(symbol);
        if (!isValidSymbol(sym)) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "symbol 格式不正确: " + symbol));
        }

        CandlestickIntervalEnum intervalEnum = parseInterval(interval);
        if (intervalEnum == null) {
            intervalEnum = CandlestickIntervalEnum.OKXMIN3;
        }

        KlineParam p = KlineParam.builder()
                .symbol(sym)
                .klineInterval(intervalEnum)
                .size(1)
                .build();
        List<Candlestick> list = candlestickService.getLastKlines(p);
        if (list == null || list.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.error(404, "无本地K线数据，请先导入或开启行情采集: " + sym));
        }
        Candlestick latest = list.get(list.size() - 1);

        Map<String, Object> data = new HashMap<>();
        data.put("symbol", sym);
        data.put("interval", intervalEnum.name());
        data.put("time", latest.getTimeStr());
        data.put("price", latest.getClosePrice());
        data.put("source", "db_kline");
        return ResponseEntity.ok(ApiResponse.success("查询成功", data));
    }

    private boolean authorized(String provided) {
        String required = System.getenv("OPENCLAW_BRIDGE_TOKEN");
        if (required == null || required.isBlank()) {
            return true;
        }
        return required.equals(provided);
    }

    private CandlestickIntervalEnum parseInterval(String interval) {
        if (interval == null) return null;
        String s = interval.trim();
        if (s.isEmpty()) return null;
        try {
            return CandlestickIntervalEnum.valueOf(s);
        } catch (Exception ignored) {
        }
        return switch (s.toLowerCase()) {
            case "1m" -> CandlestickIntervalEnum.OKXMIN1;
            case "3m" -> CandlestickIntervalEnum.OKXMIN3;
            case "5m" -> CandlestickIntervalEnum.OKXMIN5;
            case "15m" -> CandlestickIntervalEnum.OKXMIN15;
            case "30m" -> CandlestickIntervalEnum.OKXMIN30;
            case "1h", "60m" -> CandlestickIntervalEnum.OKXMIN60;
            case "4h" -> CandlestickIntervalEnum.OKX4HOUR;
            case "1d" -> CandlestickIntervalEnum.OKX1D;
            default -> null;
        };
    }

    private boolean isValidSymbol(String sym) {
        if (sym == null) return false;
        return sym.matches("^[A-Z0-9]{2,12}-[A-Z0-9]{2,12}(-SWAP)?$");
    }

    private String normalizeSymbol(String symbol) {
        String s = String.valueOf(symbol == null ? "" : symbol).trim();
        if (s.isEmpty()) return "";
        s = s.replaceAll("^实时建议\\s*[:：]\\s*", "");
        s = s.replaceAll("^标的\\s*[:：]\\s*", "");
        s = s.replaceAll("^symbol\\s*[:：]\\s*", "");
        s = s.trim().replaceAll("\\s+", "").replace("/", "-").toUpperCase();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("[A-Z0-9]{2,12}-[A-Z0-9]{2,12}(-SWAP)?")
                .matcher(s);
        if (m.find()) return m.group();
        if (s.matches("^[A-Z0-9]{2,12}$")) return s + "-USDT-SWAP";
        return s;
    }
}
