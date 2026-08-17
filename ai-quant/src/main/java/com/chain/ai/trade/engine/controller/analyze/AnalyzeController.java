package com.chain.ai.trade.engine.controller.analyze;

import com.chain.ai.trade.engine.controller.advice.LiveAdviceController;
import com.chain.ai.trade.common.entity.dto.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/analyze")
@RequiredArgsConstructor
public class AnalyzeController {

    private final LiveAdviceController liveAdviceController;

    @PostMapping("/manual")
    public ApiResponse<Map<String, Object>> manual(@RequestBody ManualAnalyzeRequest request, HttpServletResponse response) throws Exception {
        LiveAdviceController.LiveAdviceRequest req = new LiveAdviceController.LiveAdviceRequest();
        req.stream = Boolean.FALSE;
        req.symbolText = request != null ? request.symbol : null;
        req.interval = request != null ? request.interval : null;
        req.accountId = request != null ? request.accountId : null;
        req.robotId = request != null ? request.robotId : null;

        Object out = liveAdviceController.live(req, response);
        if (!(out instanceof Map)) {
            return ApiResponse.error(500, "分析失败");
        }
        Map<?, ?> m = (Map<?, ?>) out;
        Object err = m.get("error");
        if (err != null && !String.valueOf(err).isBlank()) {
            return ApiResponse.error(400, String.valueOf(err));
        }
        Object adviceId = m.get("adviceId");
        Object naturalReport = m.get("naturalReport");
        return ApiResponse.success(Map.of(
                "adviceId", adviceId == null ? "" : String.valueOf(adviceId),
                "naturalReport", naturalReport == null ? "" : String.valueOf(naturalReport)
        ));
    }

    public static class ManualAnalyzeRequest {
        public String symbol;
        public String interval;
        public String accountId;
        public String robotId;
    }
}

