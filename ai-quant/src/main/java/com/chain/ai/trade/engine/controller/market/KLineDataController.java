package com.chain.ai.trade.engine.controller.market;

import com.chain.ai.trade.engine.data.entity.dos.Candlestick;
import com.chain.ai.trade.engine.data.entity.param.KlineParam;
import com.chain.ai.trade.engine.data.entity.param.CandlestickRequest;
import com.chain.ai.trade.engine.data.service.ICandlestickService;
import com.chain.ai.trade.engine.data.entity.constants.CandlestickIntervalEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * K线数据控制器
 * 提供K线数据查询和管理相关的 REST API 接口
 */
@RestController
@RequestMapping("/api/kline")
@RequiredArgsConstructor
@Slf4j
public class KLineDataController {

    private final ICandlestickService candlestickService;

    /**
     * 获取K线数据
     */
    @PostMapping("/data")
    public ResponseEntity<Map<String, Object>> getKlineData(@RequestBody Map<String, Object> requestBody) {
        log.info("收到获取K线数据请求: {}", requestBody);

        try {
            // 解析前端参数并构建KlineParam
            String symbol = (String) requestBody.get("symbol");
            String intervalStr = (String) requestBody.get("interval");
            Integer size = requestBody.get("size") != null ? ((Number) requestBody.get("size")).intValue() : 300;
            String memberId = (String) requestBody.get("memberId");
            String accountId = (String) requestBody.get("thirdAccountId");

            // 转换时间间隔枚举
            CandlestickIntervalEnum klineInterval = CandlestickIntervalEnum.OKXMIN3; // 默认值
            if (intervalStr != null && !intervalStr.trim().isEmpty()) {
                try {
                    klineInterval = CandlestickIntervalEnum.valueOf(intervalStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    log.warn("无效的时间间隔: {}, 使用默认值 OKXMIN3", intervalStr);
                }
            }

            // 构建查询参数
            KlineParam.KlineParamBuilder paramBuilder = KlineParam.builder()
                    .symbol(symbol)
                    .klineInterval(klineInterval)
                    .size(size);

            // 设置可选参数
            if (memberId != null) {
                paramBuilder.memberId(memberId);
            }
            if (accountId != null) {
                paramBuilder.accountId(accountId);
            }

            KlineParam queryParam = paramBuilder.build();

            // 查询K线数据
            List<Candlestick> klineDatas = candlestickService.getKlines4KChart(queryParam);

            // 构建返回结果，符合前端期望的格式
            Map<String, Object> data = new HashMap<>();
            data.put("klineDatas", klineDatas);
            data.put("count", klineDatas.size());
            data.put("symbol", symbol);
            data.put("interval", klineInterval);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", data);
            result.put("message", "查询成功");

            log.info("K线数据查询完成，返回{}条数据", klineDatas.size());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("获取K线数据失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "获取K线数据失败: " + e.getMessage());
            result.put("data", null);
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 获取增量K线数据
     * 对应前端 getUnshiftData API 调用
     */
    @GetMapping("/unshift")
    public ResponseEntity<Map<String, Object>> getUnshiftData(
            @RequestParam(required = false) String memberId,
            @RequestParam(required = false) String thirdAccountId,
            @RequestParam String symbol,
            @RequestParam String interval,
            @RequestParam(required = false) String dataInterval,
            @RequestParam(required = false) String memberPlatform,
            @RequestParam(required = false) String indicatorType,
            @RequestParam(defaultValue = "1") Integer pageNumber,
            @RequestParam(defaultValue = "200") Integer pageSize,
            @RequestParam(defaultValue = "200") Integer size,
            @RequestParam String direction,
            @RequestParam(required = false) Long from,
            @RequestParam(required = false) Long to,
            @RequestParam(required = false, defaultValue = "false") Boolean toTime) {

        log.info("收到会员增量K线数据请求: memberId={}, thirdAccountId={}, symbol={}, interval={}, direction={}, from={}, to={}, toTime={}",
                memberId, thirdAccountId, symbol, interval, direction, from, to, toTime);

        try {
            // 转换时间间隔枚举
            CandlestickIntervalEnum klineInterval = CandlestickIntervalEnum.OKXMIN3;
            if (interval != null && !interval.trim().isEmpty()) {
                try {
                    klineInterval = CandlestickIntervalEnum.valueOf(interval.toUpperCase());
                } catch (IllegalArgumentException e) {
                    log.warn("无效的时间间隔: {}, 使用默认值 OKXMIN3", interval);
                }
            }
            size=1000;
            // 创建查询参数
            CandlestickRequest candlestickRequest = CandlestickRequest.builder()
                    .symbol(symbol)
                    .interval(klineInterval)
                    .size(size)
                    .build();

            // 设置时间范围参数
            // 前端发送的from和to都是秒级时间戳，服务层需要秒级时间戳
            if (from != null) {
                candlestickRequest.setFrom(from*1000);
            }

            if (to != null) {
                candlestickRequest.setTo(to*1000);
            }



            // 设置可选参数
            if (memberId != null) {
                candlestickRequest.setMemberId(memberId);
            }

            // 查询K线数据
            List<Candlestick> klineDatas = candlestickService.getByQry(candlestickRequest);

            // 构建返回结果，符合前端期望的格式
            Map<String, Object> data = new HashMap<>();
            data.put("klineDatas", klineDatas);
            data.put("count", klineDatas.size());
            data.put("symbol", symbol);
            data.put("interval", klineInterval);
            data.put("direction", direction);
            data.put("pageNumber", pageNumber);
            data.put("pageSize", pageSize);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", data);
            result.put("message", "增量查询成功");

            log.info("会员增量K线数据查询完成，返回{}条数据", klineDatas.size());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("获取会员增量K线数据失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "获取增量K线数据失败: " + e.getMessage());
            result.put("data", null);
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 获取最新K线数据
     */
    @PostMapping("/latest-data")
    public ResponseEntity<Map<String, Object>> getLatestKlineData(@RequestBody Map<String, Object> requestBody) {
        log.info("收到获取最新K线数据请求: {}", requestBody);

        try {
            // 解析前端参数并构建KlineParam
            String symbol = (String) requestBody.get("symbol");
            String intervalStr = (String) requestBody.get("interval");
            Integer size = requestBody.get("size") != null ? ((Number) requestBody.get("size")).intValue() : 300;
            String memberId = (String) requestBody.get("memberId");
            String accountId = (String) requestBody.get("thirdAccountId");

            // 转换时间间隔枚举
            CandlestickIntervalEnum klineInterval = CandlestickIntervalEnum.OKXMIN3; // 默认值
            if (intervalStr != null && !intervalStr.trim().isEmpty()) {
                try {
                    klineInterval = CandlestickIntervalEnum.valueOf(intervalStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    log.warn("无效的时间间隔: {}, 使用默认值 OKXMIN3", intervalStr);
                }
            }

            // 构建查询参数
            KlineParam.KlineParamBuilder paramBuilder = KlineParam.builder()
                    .symbol(symbol)
                    .klineInterval(klineInterval)
                    .size(size);

            // 设置可选参数
            if (memberId != null) {
                paramBuilder.memberId(memberId);
            }
            if (accountId != null) {
                paramBuilder.accountId(accountId);
            }

            KlineParam queryParam = paramBuilder.build();

            // 查询最新K线数据
            List<Candlestick> klineDatas = candlestickService.getKlines4KChart(queryParam);

            // 构建返回结果，符合前端期望的格式
            Map<String, Object> data = new HashMap<>();
            data.put("klineDatas", klineDatas);
            data.put("count", klineDatas.size());
            data.put("symbol", symbol);
            data.put("interval", klineInterval);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", data);
            result.put("message", "查询成功");

            log.info("最新K线数据查询完成，返回{}条数据", klineDatas.size());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("获取最新K线数据失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "获取最新K线数据失败: " + e.getMessage());
            result.put("data", null);
            return ResponseEntity.badRequest().body(result);
        }
    }

}
