package com.chain.ai.trade.engine.controller.advice;

import com.chain.ai.trade.engine.controller.LlmGenerateController;
import com.chain.ai.trade.engine.entity.ReviewMetrics;
import com.chain.ai.trade.engine.entity.ReviewTask;
import com.chain.ai.trade.engine.mapper.ReviewTaskMapper;
import com.chain.ai.trade.engine.service.ReviewMetricsService;
import com.chain.ai.trade.engine.service.ReviewPromptBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/advice")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewMetricsService reviewMetricsService;
    private final ReviewPromptBuilder reviewPromptBuilder;
    private final LlmGenerateController llmGenerateController;
    private final ReviewTaskMapper reviewTaskMapper;
    private final ObjectMapper objectMapper;

    @PostMapping("/review")
    public Object review(@RequestBody ReviewRequest req, HttpServletResponse response) throws Exception {
        String raw = req == null ? "" : String.valueOf(req.symbolText == null ? "" : req.symbolText).trim();
        if (raw.isBlank()) {
            response.setStatus(400);
            return Map.of("error", "请输入标的");
        }
        String robotId = req == null ? "" : String.valueOf(req.robotId == null ? "" : req.robotId).trim();
        if (robotId.isBlank()) {
            response.setStatus(400);
            return Map.of("error", "请先选择机器人");
        }

        String accountId = req == null ? "" : String.valueOf(req.accountId == null ? "" : req.accountId).trim();

        boolean streamRequested = req != null && Boolean.TRUE.equals(req.stream);

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
        ZonedDateTime startTime;
        ZonedDateTime endTime = now;

        if (req != null && req.timeRange != null) {
            startTime = req.timeRange.start != null
                    ? ZonedDateTime.parse(req.timeRange.start, DateTimeFormatter.ISO_DATE_TIME)
                    : now.minusDays(30);
            endTime = req.timeRange.end != null
                    ? ZonedDateTime.parse(req.timeRange.end, DateTimeFormatter.ISO_DATE_TIME)
                    : now;
        } else {
            startTime = now.minusDays(30);
        }

        ReviewMetrics metrics = reviewMetricsService.calculate(
                robotId,
                Date.from(startTime.toInstant()),
                Date.from(endTime.toInstant())
        );

        String systemPrompt = reviewPromptBuilder.buildSystemPrompt();
        String userPrompt = reviewPromptBuilder.buildUserPrompt(
                raw, robotId, metrics, startTime, endTime
        );

        String conversationId = "rev_" + System.currentTimeMillis() + "_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        ReviewTask task = new ReviewTask();
        task.setConversationId(conversationId);
        task.setTimeRangeStart(Date.from(startTime.toInstant()));
        task.setTimeRangeEnd(Date.from(endTime.toInstant()));
        task.setRobotId(robotId);
        task.setStatus("processing");
        task.setCreateTime(new Date());
        task.setUpdateTime(new Date());
        reviewTaskMapper.insert(task);

        LlmGenerateController.GenerateRequest gen = new LlmGenerateController.GenerateRequest();
        gen.stream = streamRequested ? Boolean.FALSE : (req != null ? req.stream : null);
        gen.messages = new ArrayList<>();

        LlmGenerateController.Message sys = new LlmGenerateController.Message();
        sys.role = "system";
        sys.content = systemPrompt;
        gen.messages.add(sys);

        if (req != null && req.history != null) {
            for (LlmGenerateController.Message m : req.history) {
                if (m == null) continue;
                if (m.role == null || m.role.isBlank()) continue;
                String c = m.content == null ? "" : m.content;
                if (c.isBlank()) continue;
                LlmGenerateController.Message item = new LlmGenerateController.Message();
                item.role = m.role;
                item.content = c;
                gen.messages.add(item);
            }
        }

        LlmGenerateController.Message user = new LlmGenerateController.Message();
        user.role = "user";
        user.content = userPrompt;
        gen.messages.add(user);

        Object out = llmGenerateController.generate(gen, response);
        if (!(out instanceof Map)) return out;
        Map<?, ?> m = (Map<?, ?>) out;
        if (m.containsKey("error")) return out;
        Object respObj = m.get("response");
        if (respObj == null) return out;
        String respText = String.valueOf(respObj);

        Map<String, Object> next = new HashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (e == null || e.getKey() == null) continue;
            next.put(String.valueOf(e.getKey()), e.getValue());
        }
        next.put("response", respText);
        next.put("conversationId", conversationId);

        task.setReportJson(respText);
        task.setStatus("completed");
        task.setCompletedAt(new Date());
        task.setUpdateTime(new Date());
        reviewTaskMapper.updateById(task);

        if (streamRequested) {
            streamReviewResponse(response, respText, conversationId);
            return null;
        }
        return next;
    }

    private void streamReviewResponse(HttpServletResponse response, String report, String conversationId) throws Exception {
        response.setStatus(200);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/event-stream");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");

        try (ServletOutputStream out = response.getOutputStream()) {
            out.write(":ok\n\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            response.flushBuffer();

            String text = report == null ? "" : report;
            int chunkSize = 80;
            for (int i = 0; i < text.length(); i += chunkSize) {
                String part = text.substring(i, Math.min(text.length(), i + chunkSize));
                String payload = objectMapper.writeValueAsString(Map.of("response", part));
                out.write(("data: " + payload + "\n\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
                response.flushBuffer();
            }

            Map<String, Object> done = new LinkedHashMap<>();
            done.put("conversationId", conversationId);
            done.put("done", true);
            String endPayload = objectMapper.writeValueAsString(done);
            out.write(("data: " + endPayload + "\n\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            response.flushBuffer();
        }
    }

    public static class ReviewRequest {
        public Boolean stream;
        public String symbolText;
        public String accountId;
        public String robotId;
        public TimeRange timeRange;
        public List<LlmGenerateController.Message> history;
    }

    public static class TimeRange {
        public String start;
        public String end;
    }
}
