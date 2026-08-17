package com.chain.ai.trade.engine.controller.openclaw;

import com.chain.ai.trade.common.utils.RedisCache;
import com.chain.ai.trade.engine.controller.order.ManualController;
import com.chain.ai.trade.common.entity.dto.ApiResponse;
import com.chain.ai.trade.order.entity.vo.OrderVO;
import com.chain.ai.trade.order.service.ITradeOrderService;
import com.chain.ai.trade.member.entity.TradingAccount;
import com.chain.ai.trade.member.service.ITradingAccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@RestController
@RequestMapping("/api/openclaw/orders")
@RequiredArgsConstructor
public class OpenClawOrdersController {

    private static final long PREVIEW_TTL_SECONDS = 30 * 60;
    private static final String PREVIEW_KEY_PREFIX = "openclaw:order:preview:";

    private final RedisCache redisCache;
    private final ObjectMapper objectMapper;
    private final ITradeOrderService tradeOrderService;
    private final ITradingAccountService tradingAccountService;
    private final ManualController manualController;

    @PostMapping("/open/preview")
    public ResponseEntity<ApiResponse<?>> previewOpen(
            @RequestHeader(value = "X-OpenClaw-Token", required = false) String token,
            @RequestBody ManualController.ManualOpenRequest req
    ) {
        if (!authorized(token)) {
            return ResponseEntity.status(401).body(ApiResponse.error("unauthorized"));
        }
        try {
            PreviewResult<ManualController.ManualOpenRequest> normalized = normalizeOpen(req);
            String previewId = UUID.randomUUID().toString().replace("-", "");
            PreviewStore store = new PreviewStore();
            store.setType("OPEN");
            store.setPreviewId(previewId);
            store.setCreatedAtMs(System.currentTimeMillis());
            store.setOpen(normalized.value);
            redisCache.put(PREVIEW_KEY_PREFIX + previewId, objectMapper.writeValueAsString(store), PREVIEW_TTL_SECONDS);

            Map<String, Object> data = new HashMap<>();
            data.put("previewId", previewId);
            data.put("expiresInSeconds", PREVIEW_TTL_SECONDS);
            data.put("request", normalized.value);
            data.put("warnings", normalized.warnings);
            data.put("next", Map.of(
                    "tool", "quant_open_order_confirm",
                    "params", Map.of("previewId", previewId)
            ));
            return ResponseEntity.ok(ApiResponse.success("预检通过", data));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage() == null ? "预检失败" : e.getMessage()));
        }
    }

    @PostMapping("/open/confirm")
    public ResponseEntity<ApiResponse<?>> confirmOpen(
            @RequestHeader(value = "X-OpenClaw-Token", required = false) String token,
            @RequestBody ConfirmRequest req
    ) {
        if (!authorized(token)) {
            return ResponseEntity.status(401).body(ApiResponse.error("unauthorized"));
        }
        if (req == null || req.getPreviewId() == null || req.getPreviewId().isBlank()) {
            return ResponseEntity.ok(ApiResponse.error("previewId不能为空"));
        }
        try {
            PreviewStore store = loadPreview(req.getPreviewId());
            if (!"OPEN".equalsIgnoreCase(store.getType()) || store.getOpen() == null) {
                return ResponseEntity.ok(ApiResponse.error("previewId无效或类型不匹配"));
            }
            ApiResponse<?> result = manualController.open(store.getOpen());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage() == null ? "确认失败" : e.getMessage()));
        }
    }

    @PostMapping("/close/preview")
    public ResponseEntity<ApiResponse<?>> previewClose(
            @RequestHeader(value = "X-OpenClaw-Token", required = false) String token,
            @RequestBody ManualController.ManualCloseRequest req
    ) {
        if (!authorized(token)) {
            return ResponseEntity.status(401).body(ApiResponse.error("unauthorized"));
        }
        try {
            PreviewResult<ManualController.ManualCloseRequest> normalized = normalizeClose(req);
            ClosePlan plan = buildClosePlan(normalized.value);
            String previewId = UUID.randomUUID().toString().replace("-", "");
            PreviewStore store = new PreviewStore();
            store.setType("CLOSE");
            store.setPreviewId(previewId);
            store.setCreatedAtMs(System.currentTimeMillis());
            store.setClose(normalized.value);
            redisCache.put(PREVIEW_KEY_PREFIX + previewId, objectMapper.writeValueAsString(store), PREVIEW_TTL_SECONDS);

            Map<String, Object> data = new HashMap<>();
            data.put("previewId", previewId);
            data.put("expiresInSeconds", PREVIEW_TTL_SECONDS);
            data.put("request", normalized.value);
            data.put("plan", plan);
            data.put("warnings", normalized.warnings);
            data.put("next", Map.of(
                    "tool", "quant_close_order_confirm",
                    "params", Map.of("previewId", previewId)
            ));
            return ResponseEntity.ok(ApiResponse.success("预检通过", data));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage() == null ? "预检失败" : e.getMessage()));
        }
    }

    @PostMapping("/close/confirm")
    public ResponseEntity<ApiResponse<?>> confirmClose(
            @RequestHeader(value = "X-OpenClaw-Token", required = false) String token,
            @RequestBody ConfirmRequest req
    ) {
        if (!authorized(token)) {
            return ResponseEntity.status(401).body(ApiResponse.error("unauthorized"));
        }
        if (req == null || req.getPreviewId() == null || req.getPreviewId().isBlank()) {
            return ResponseEntity.ok(ApiResponse.error("previewId不能为空"));
        }
        try {
            PreviewStore store = loadPreview(req.getPreviewId());
            if (!"CLOSE".equalsIgnoreCase(store.getType()) || store.getClose() == null) {
                return ResponseEntity.ok(ApiResponse.error("previewId无效或类型不匹配"));
            }
            ApiResponse<?> result = manualController.close(store.getClose());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage() == null ? "确认失败" : e.getMessage()));
        }
    }

    @GetMapping("/positions")
    public ResponseEntity<ApiResponse<?>> positions(
            @RequestHeader(value = "X-OpenClaw-Token", required = false) String token,
            @RequestParam String accountId,
            @RequestParam String symbol
    ) {
        if (!authorized(token)) {
            return ResponseEntity.status(401).body(ApiResponse.error("unauthorized"));
        }
        if (accountId == null || accountId.isBlank()) {
            return ResponseEntity.ok(ApiResponse.error("accountId不能为空"));
        }
        if (symbol == null || symbol.isBlank()) {
            return ResponseEntity.ok(ApiResponse.error("symbol不能为空"));
        }
        List<OrderVO> orders = tradeOrderService.getPositionOrders(accountId, symbol);
        return ResponseEntity.ok(ApiResponse.success("查询成功", orders));
    }

    @GetMapping("/order-status")
    public ResponseEntity<ApiResponse<?>> orderStatus(
            @RequestHeader(value = "X-OpenClaw-Token", required = false) String token,
            @RequestParam(required = false) String orderId,
            @RequestParam(required = false) String orderSn
    ) {
        if (!authorized(token)) {
            return ResponseEntity.status(401).body(ApiResponse.error("unauthorized"));
        }
        if ((orderId == null || orderId.isBlank()) && (orderSn == null || orderSn.isBlank())) {
            return ResponseEntity.ok(ApiResponse.error("orderId或orderSn至少提供一个"));
        }
        Map<String, Object> out = new HashMap<>();
        if (orderId != null && !orderId.isBlank()) {
            out.put("orderId", orderId);
            out.put("status", tradeOrderService.getOrderStatus(orderId));
        }
        if (orderSn != null && !orderSn.isBlank()) {
            out.put("orderSn", orderSn);
            out.put("order", tradeOrderService.getOrderByOrderSn(orderSn));
        }
        return ResponseEntity.ok(ApiResponse.success("查询成功", out));
    }

    private PreviewStore loadPreview(String previewId) throws Exception {
        String raw = (String) redisCache.get(PREVIEW_KEY_PREFIX + previewId);
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("previewId已过期或不存在");
        }
        return objectMapper.readValue(raw, PreviewStore.class);
    }

    private PreviewResult<ManualController.ManualOpenRequest> normalizeOpen(ManualController.ManualOpenRequest req) {
        if (req == null) throw new IllegalArgumentException("请求体不能为空");
        List<String> warnings = new ArrayList<>();

        String accountId = trim(req.getAccountId());
        if (accountId.isBlank()) throw new IllegalArgumentException("accountId不能为空");
        TradingAccount account = tradingAccountService.getById(accountId);
        if (account == null) throw new IllegalArgumentException("账户不存在");
        if (account.getBindStatus() == TradingAccount.BindStatus.UNBIND) throw new IllegalArgumentException("账户未绑定");
        if (!account.isApiEnabled()) throw new IllegalArgumentException("账户API未启用");

        String symbol = trim(req.getSymbol());
        if (symbol.isBlank()) throw new IllegalArgumentException("symbol不能为空");

        String side = trim(req.getSide()).toUpperCase(Locale.ROOT);
        if (!(side.equals("LONG") || side.equals("SHORT") || side.equals("BUY") || side.equals("SELL"))) {
            throw new IllegalArgumentException("side必须为LONG/SHORT或BUY/SELL");
        }
        if (side.equals("BUY")) side = "LONG";
        if (side.equals("SELL")) side = "SHORT";

        String orderType = trim(req.getOrderType()).toUpperCase(Locale.ROOT);
        if (!(orderType.equals("MARKET") || orderType.equals("LIMIT"))) {
            throw new IllegalArgumentException("orderType必须为MARKET或LIMIT");
        }

        BigDecimal quantity = req.getQuantity();
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("quantity必须大于0");
        }
        BigDecimal flooredQty = quantity.setScale(0, RoundingMode.FLOOR);
        if (flooredQty.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("数量不足1，已拦截");
        }
        if (flooredQty.compareTo(quantity) != 0) {
            warnings.add("quantity已向下取整为 " + flooredQty);
        }

        BigDecimal limitPrice = req.getLimitPrice();
        if (orderType.equals("LIMIT")) {
            if (limitPrice == null || limitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("限价单必须提供有效的limitPrice");
            }
        }

        ManualController.ManualOpenRequest out = new ManualController.ManualOpenRequest();
        out.setAccountId(accountId);
        out.setRobotId(trim(req.getRobotId()));
        out.setSymbol(symbol);
        out.setSide(side);
        out.setOrderType(orderType);
        out.setQuantity(flooredQty);
        out.setLimitPrice(limitPrice);
        out.setTimeInForce(trim(req.getTimeInForce()));
        out.setLeverage(req.getLeverage());
        out.setChannel(trim(req.getChannel()).isBlank() ? "OPENCLAW" : trim(req.getChannel()));
        out.setMetadata(req.getMetadata());

        String requestId = trim(req.getRequestId());
        out.setRequestId(requestId.isBlank() ? ("oc-" + UUID.randomUUID().toString().replace("-", "")) : requestId);

        return new PreviewResult<>(out, warnings);
    }

    private PreviewResult<ManualController.ManualCloseRequest> normalizeClose(ManualController.ManualCloseRequest req) {
        if (req == null) throw new IllegalArgumentException("请求体不能为空");
        List<String> warnings = new ArrayList<>();

        String accountId = trim(req.getAccountId());
        if (accountId.isBlank()) throw new IllegalArgumentException("accountId不能为空");
        TradingAccount account = tradingAccountService.getById(accountId);
        if (account == null) throw new IllegalArgumentException("账户不存在");
        if (account.getBindStatus() == TradingAccount.BindStatus.UNBIND) throw new IllegalArgumentException("账户未绑定");
        if (!account.isApiEnabled()) throw new IllegalArgumentException("账户API未启用");

        String symbol = trim(req.getSymbol());
        if (symbol.isBlank()) throw new IllegalArgumentException("symbol不能为空");

        String side = trim(req.getSide()).toUpperCase(Locale.ROOT);
        if (!(side.equals("LONG") || side.equals("SHORT") || side.equals("BUY") || side.equals("SELL"))) {
            throw new IllegalArgumentException("side必须为LONG/SHORT或BUY/SELL");
        }
        if (side.equals("BUY")) side = "LONG";
        if (side.equals("SELL")) side = "SHORT";

        BigDecimal flooredQty = null;
        if (req.getQuantity() != null) {
            BigDecimal quantity = req.getQuantity();
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("quantity必须大于0");
            }
            flooredQty = quantity.setScale(0, RoundingMode.FLOOR);
            if (flooredQty.compareTo(BigDecimal.ONE) < 0) {
                throw new IllegalArgumentException("数量不足1，已拦截");
            }
            if (flooredQty.compareTo(quantity) != 0) {
                warnings.add("quantity已向下取整为 " + flooredQty);
            }
        }

        String orderType = trim(req.getOrderType()).toUpperCase(Locale.ROOT);
        if (orderType.isBlank()) orderType = "MARKET";
        if (!(orderType.equals("MARKET") || orderType.equals("LIMIT"))) {
            throw new IllegalArgumentException("orderType必须为MARKET或LIMIT");
        }
        BigDecimal limitPrice = req.getLimitPrice();
        if (orderType.equals("LIMIT")) {
            if (limitPrice == null || limitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("限价单必须提供有效的limitPrice");
            }
        }

        ManualController.ManualCloseRequest out = new ManualController.ManualCloseRequest();
        out.setAccountId(accountId);
        out.setRobotId(trim(req.getRobotId()));
        out.setSymbol(symbol);
        out.setSide(side);
        out.setQuantity(flooredQty);
        out.setOrderType(orderType);
        out.setLimitPrice(limitPrice);
        out.setChannel(trim(req.getChannel()).isBlank() ? "OPENCLAW" : trim(req.getChannel()));
        out.setMetadata(req.getMetadata());

        String requestId = trim(req.getRequestId());
        out.setRequestId(requestId.isBlank() ? ("oc-" + UUID.randomUUID().toString().replace("-", "")) : requestId);

        return new PreviewResult<>(out, warnings);
    }

    private ClosePlan buildClosePlan(ManualController.ManualCloseRequest req) {
        String side = req.getSide() == null ? "" : req.getSide().trim().toUpperCase(Locale.ROOT);
        String mappedSide = (side.equals("LONG") || side.equals("BUY")) ? "BUY" : "SELL";
        List<OrderVO> positionOrders = tradeOrderService.getPositionOrders(req.getAccountId(), req.getSymbol());
        List<OrderVO> matched = positionOrders == null ? List.of() : positionOrders.stream()
                .filter(o -> o != null && o.getOrderSide() != null && mappedSide.equalsIgnoreCase(o.getOrderSide()))
                .sorted(Comparator.comparing(OrderVO::getOrderTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<Map<String, Object>> orders = new ArrayList<>();
        BigDecimal totalRemaining = BigDecimal.ZERO;
        for (OrderVO o : matched) {
            String orderSn = o.getOrderSn();
            if (orderSn == null || orderSn.isBlank()) continue;
            BigDecimal rem = tradeOrderService.getRemainingPositionByOrderSn(orderSn);
            if (rem == null) rem = BigDecimal.ZERO;
            totalRemaining = totalRemaining.add(rem.max(BigDecimal.ZERO));
            Map<String, Object> item = new HashMap<>();
            item.put("orderSn", orderSn);
            item.put("orderTime", o.getOrderTime());
            item.put("remaining", rem);
            orders.add(item);
        }

        BigDecimal requested = req.getQuantity();
        BigDecimal willClose = requested == null ? totalRemaining : requested.min(totalRemaining);

        ClosePlan plan = new ClosePlan();
        plan.setMatchedOrders(orders);
        plan.setTotalRemaining(totalRemaining);
        plan.setRequestedClose(requested);
        plan.setWillClose(willClose);
        if (matched.isEmpty()) {
            plan.setNote("暂无可平仓订单");
        }
        return plan;
    }

    private boolean authorized(String provided) {
        String required = System.getenv("OPENCLAW_BRIDGE_TOKEN");
        if (required == null || required.isBlank()) {
            return true;
        }
        return required.equals(provided);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private record PreviewResult<T>(T value, List<String> warnings) {}

    @Data
    public static class ConfirmRequest {
        private String previewId;
    }

    @Data
    public static class PreviewStore {
        private String type;
        private String previewId;
        private Long createdAtMs;
        private ManualController.ManualOpenRequest open;
        private ManualController.ManualCloseRequest close;
    }

    @Data
    public static class ClosePlan {
        private List<Map<String, Object>> matchedOrders;
        private BigDecimal totalRemaining;
        private BigDecimal requestedClose;
        private BigDecimal willClose;
        private String note;
    }
}

