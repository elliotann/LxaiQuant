package com.chain.ai.trade.engine.controller;

import com.chain.ai.trade.engine.entity.LlmConfig;
import com.chain.ai.trade.engine.service.LlmConfigService;
import com.chain.ai.trade.member.util.AesGcmEncryptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
public class LlmGenerateController {

    private static final String PROVIDER_KEY_PREFIX = "provider:";
    private static final String DEEPSEEK_DEFAULT_BASE_URL = "https://api.deepseek.com";
    private static final String OPENCLAW_DEFAULT_BASE_URL = "http://192.168.1.17:18789";
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(30);

    private final LlmConfigService llmConfigService;
    private final ObjectMapper objectMapper;

    private static Duration getConnectTimeout() {
        try {
            String v = System.getenv("LLM_CONNECT_TIMEOUT_SECONDS");
            if (v == null || v.isBlank()) return Duration.ofSeconds(60);
            long s = Long.parseLong(v.trim());
            if (s <= 0) return Duration.ofSeconds(60);
            return Duration.ofSeconds(Math.min(300, s));
        } catch (Exception e) {
            return Duration.ofSeconds(60);
        }
    }

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(getConnectTimeout())
        .version(HttpClient.Version.HTTP_1_1)
        .proxy(new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                return List.of(Proxy.NO_PROXY);
            }

            @Override
            public void connectFailed(URI uri, java.net.SocketAddress sa, java.io.IOException ioe) {
            }
        })
        .build();

    private volatile AesGcmEncryptor encryptor;

    @PostMapping("/generate")
    public Object generate(@RequestBody GenerateRequest req, HttpServletResponse response) throws Exception {
        boolean stream = Boolean.TRUE.equals(req.stream);

        LlmConfig active = llmConfigService.getActiveSelection();
        String provider = active != null && active.getProvider() != null ? active.getProvider() : "ollama";
        String model = active != null && active.getModel() != null ? active.getModel() : null;

        if (!"ollama".equals(provider) && !"deepseek".equals(provider) && !"openclaw".equals(provider)) {
            response.setStatus(400);
            return Map.of("error", "不支持的 provider: " + provider);
        }

        if ("ollama".equals(provider)) {
            if (model == null || model.isBlank()) model = "qwen3:4b";
            if (stream) {
                streamOllama(req, model, response);
                return null;
            }
            return callOllamaOnce(req, model);
        }

        if ("deepseek".equals(provider)) {
            LlmConfig cfg = llmConfigService.getByKey(PROVIDER_KEY_PREFIX + "deepseek");
            if (cfg == null || cfg.getApiKeyEnc() == null || cfg.getApiKeyEnc().isBlank()) {
                response.setStatus(400);
                return Map.of("error", "DeepSeek API Key 未配置");
            }

            String apiKey = getEncryptor().decrypt(cfg.getApiKeyEnc());
            String baseUrl = cfg.getApiBaseUrl();
            if (baseUrl == null || baseUrl.isBlank()) baseUrl = DEEPSEEK_DEFAULT_BASE_URL;
            String deepseekModel = model != null && !model.isBlank() ? model : (cfg.getModel() != null ? cfg.getModel() : "deepseek-chat");

            if (stream) {
                streamDeepSeek(req, baseUrl, apiKey, deepseekModel, response);
                return null;
            }
            return callDeepSeekOnce(req, baseUrl, apiKey, deepseekModel);
        }

        LlmConfig cfg = llmConfigService.getByKey(PROVIDER_KEY_PREFIX + "openclaw");
        if (cfg == null || cfg.getApiKeyEnc() == null || cfg.getApiKeyEnc().isBlank()) {
            response.setStatus(400);
            return Map.of("error", "OpenClaw Token 未配置");
        }
        String token = getEncryptor().decrypt(cfg.getApiKeyEnc());
        String baseUrl = cfg.getApiBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = OPENCLAW_DEFAULT_BASE_URL;
        OpenClawConfig oc = parseOpenClawExtra(cfg.getExtraConfig());
        String openclawModel = model != null && !model.isBlank()
                ? model
                : (cfg.getModel() != null && !cfg.getModel().isBlank() ? cfg.getModel() : "deepseek/deepseek-reasoner");

        String targetUrl = buildOpenClawUrl(baseUrl);
        try {
            if (stream) {
                streamOpenClaw(req, baseUrl, token, openclawModel, oc, response);
                return null;
            }
            return callOpenClawOnce(req, baseUrl, token, openclawModel, oc);
        } catch (HttpConnectTimeoutException e) {
            response.setStatus(502);
            return Map.of(
                    "error",
                    "连接 OpenClaw 超时（connect timed out）。请检查 OpenClaw 是否在局域网监听（bind=lan）与防火墙放行。\n" +
                            "target=" + targetUrl
            );
        } catch (java.net.ConnectException e) {
            response.setStatus(502);
            return Map.of(
                    "error",
                    "无法连接 OpenClaw（connect refused）。请检查 OpenClaw 是否已启动、端口是否正确。\n" +
                            "target=" + targetUrl
            );
        }
    }

    /**
     * Non-streaming LLM call without Servlet dependency.
     */
    public String generateText(List<Message> messages) throws Exception {
        LlmConfig active = llmConfigService.getActiveSelection();
        String provider = active != null && active.getProvider() != null ? active.getProvider() : "ollama";
        String model = active != null && active.getModel() != null ? active.getModel() : null;

        if (!"ollama".equals(provider) && !"deepseek".equals(provider) && !"openclaw".equals(provider)) {
            throw new IllegalArgumentException("不支持的 provider: " + provider);
        }

        GenerateRequest req = new GenerateRequest();
        req.stream = false;
        req.messages = messages;

        if ("ollama".equals(provider)) {
            if (model == null || model.isBlank()) model = "qwen3:4b";
            Map<String, Object> result = callOllamaOnce(req, model);
            if (result.containsKey("error")) throw new RuntimeException((String) result.get("error"));
            return (String) result.get("response");
        }

        if ("deepseek".equals(provider)) {
            LlmConfig cfg = llmConfigService.getByKey(PROVIDER_KEY_PREFIX + "deepseek");
            if (cfg == null || cfg.getApiKeyEnc() == null || cfg.getApiKeyEnc().isBlank()) {
                throw new RuntimeException("DeepSeek API Key 未配置");
            }
            String apiKey = getEncryptor().decrypt(cfg.getApiKeyEnc());
            String baseUrl = cfg.getApiBaseUrl();
            if (baseUrl == null || baseUrl.isBlank()) baseUrl = DEEPSEEK_DEFAULT_BASE_URL;
            String deepseekModel = model != null && !model.isBlank() ? model : (cfg.getModel() != null ? cfg.getModel() : "deepseek-chat");
            Map<String, Object> result = callDeepSeekOnce(req, baseUrl, apiKey, deepseekModel);
            if (result.containsKey("error")) throw new RuntimeException((String) result.get("error"));
            return (String) result.get("response");
        }

        LlmConfig cfg = llmConfigService.getByKey(PROVIDER_KEY_PREFIX + "openclaw");
        if (cfg == null || cfg.getApiKeyEnc() == null || cfg.getApiKeyEnc().isBlank()) {
            throw new RuntimeException("OpenClaw Token 未配置");
        }
        String token = getEncryptor().decrypt(cfg.getApiKeyEnc());
        String baseUrl = cfg.getApiBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = OPENCLAW_DEFAULT_BASE_URL;
        OpenClawConfig oc = parseOpenClawExtra(cfg.getExtraConfig());
        String openclawModel = model != null && !model.isBlank()
                ? model
                : (cfg.getModel() != null && !cfg.getModel().isBlank() ? cfg.getModel() : "deepseek/deepseek-reasoner");
        try {
            Map<String, Object> result = callOpenClawOnce(req, baseUrl, token, openclawModel, oc);
            if (result.containsKey("error")) throw new RuntimeException((String) result.get("error"));
            return (String) result.get("response");
        } catch (HttpConnectTimeoutException e) {
            throw new RuntimeException("连接 OpenClaw 超时: " + buildOpenClawUrl(baseUrl), e);
        } catch (java.net.ConnectException e) {
            throw new RuntimeException("无法连接 OpenClaw: " + buildOpenClawUrl(baseUrl), e);
        }
    }

    private Map<String, Object> callOpenClawOnce(GenerateRequest req, String baseUrl, String token, String model, OpenClawConfig oc) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("stream", false);
        body.put("input", toOpenResponsesInput(req.messages));
        if (oc.user != null && !oc.user.isBlank()) body.put("user", oc.user);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(buildOpenClawUrl(baseUrl)))
            .timeout(REQUEST_TIMEOUT)
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json");
        if (oc.agentId != null && !oc.agentId.isBlank()) builder.header("x-openclaw-agent-id", oc.agentId);
        if (oc.sessionKey != null && !oc.sessionKey.isBlank()) builder.header("x-openclaw-session-key", oc.sessionKey);

        HttpRequest httpRequest = builder
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> resp = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2) {
            return Map.of("error", resp.body() == null ? ("HTTP " + resp.statusCode()) : resp.body());
        }

        JsonNode json = objectMapper.readTree(resp.body());
        JsonNode error = json.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            String msg = error.path("message").asText(error.asText(""));
            return Map.of("error", msg == null || msg.isBlank() ? resp.body() : msg);
        }

        String text = extractOpenResponsesText(json);
        return Map.of("response", text);
    }

    private void streamOpenClaw(GenerateRequest req, String baseUrl, String token, String model, OpenClawConfig oc, HttpServletResponse response) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("stream", true);
        body.put("input", toOpenResponsesInput(req.messages));
        if (oc.user != null && !oc.user.isBlank()) body.put("user", oc.user);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(buildOpenClawUrl(baseUrl)))
            .timeout(REQUEST_TIMEOUT)
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream");
        if (oc.agentId != null && !oc.agentId.isBlank()) builder.header("x-openclaw-agent-id", oc.agentId);
        if (oc.sessionKey != null && !oc.sessionKey.isBlank()) builder.header("x-openclaw-session-key", oc.sessionKey);

        HttpRequest httpRequest = builder
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
            .build();

        HttpResponse<InputStream> resp = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() / 100 != 2) {
            response.setStatus(resp.statusCode());
            response.setContentType(MediaType.TEXT_PLAIN_VALUE);
            try (InputStream is = resp.body()) {
                response.getWriter().write(new String(is.readAllBytes(), StandardCharsets.UTF_8));
            }
            return;
        }

        response.setStatus(200);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");

        try (InputStream is = resp.body();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
             ServletOutputStream out = response.getOutputStream()) {
            boolean downstreamClosed = false;
            boolean wroteAnyDelta = false;
            try {
                out.write(":ok\n\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
                response.flushBuffer();
            } catch (Exception e) {
                if (isClientAbort(e)) {
                    downstreamClosed = true;
                } else {
                    throw e;
                }
            }

            String eventType = null;
            List<String> dataLines = new ArrayList<>();
            String lastError = null;

            String line;
            while ((line = reader.readLine()) != null) {
                String raw = line;
                String trimmed = raw.trim();

                if (trimmed.isEmpty()) {
                    if (dataLines.isEmpty()) {
                        eventType = null;
                        continue;
                    }
                    String payload = String.join("\n", dataLines).trim();
                    dataLines.clear();

                    if ("[DONE]".equals(payload)) {
                        if (!downstreamClosed) {
                            try {
                                if (lastError != null && !lastError.isBlank()) {
                                    writeError(out, lastError);
                                } else {
                                    writeDone(out);
                                }
                                response.flushBuffer();
                            } catch (Exception e) {
                                if (!isClientAbort(e)) throw e;
                                downstreamClosed = true;
                            }
                        }
                        break;
                    }

                    if (!payload.isEmpty()) {
                        JsonNode json = objectMapper.readTree(payload);
                        if (json.has("error")) {
                            String error = json.path("error").path("message").asText(json.path("error").asText(""));
                            if (error == null || error.isBlank()) error = payload;
                            if (!downstreamClosed) {
                                try {
                                    writeError(out, error);
                                    response.flushBuffer();
                                } catch (Exception e) {
                                    if (!isClientAbort(e)) throw e;
                                    downstreamClosed = true;
                                }
                            }
                            break;
                        }

                        if ("response.output_text.delta".equals(eventType)) {
                            String delta = json.path("delta").asText("");
                            if (delta != null && !delta.isBlank()) {
                                if (!downstreamClosed) {
                                    try {
                                        writeChunk(out, delta);
                                        wroteAnyDelta = true;
                                        response.flushBuffer();
                                    } catch (Exception e) {
                                        if (!isClientAbort(e)) throw e;
                                        downstreamClosed = true;
                                    }
                                }
                            }
                        } else if ("response.output_text.done".equals(eventType)) {
                            String text = json.path("text").asText("");
                            if (!downstreamClosed) {
                                try {
                                    if (!wroteAnyDelta && text != null && !text.isBlank()) {
                                        writeChunk(out, text);
                                    } else if (lastError != null && !lastError.isBlank()) {
                                        writeError(out, lastError);
                                        response.flushBuffer();
                                        break;
                                    }
                                    writeDone(out);
                                    response.flushBuffer();
                                } catch (Exception e) {
                                    if (!isClientAbort(e)) throw e;
                                    downstreamClosed = true;
                                }
                            }
                            break;
                        } else if ("response.failed".equals(eventType)) {
                            String error = json.path("response").path("error").path("message").asText("");
                            if (error == null || error.isBlank()) {
                                error = json.path("error").path("message").asText("");
                            }
                            if (error == null || error.isBlank()) {
                                error = "OpenClaw 内部错误";
                            }
                            lastError = error;
                        } else if ("response.completed".equals(eventType)) {
                            String status = json.path("response").path("status").asText("");
                            if ("failed".equalsIgnoreCase(status) && lastError != null && !lastError.isBlank()) {
                                if (!downstreamClosed) {
                                    try {
                                        writeError(out, lastError);
                                        response.flushBuffer();
                                    } catch (Exception e) {
                                        if (!isClientAbort(e)) throw e;
                                        downstreamClosed = true;
                                    }
                                }
                                break;
                            }
                        }
                    }

                    eventType = null;
                    continue;
                }

                if (trimmed.startsWith(":")) {
                    continue;
                }

                if (trimmed.startsWith("event:")) {
                    eventType = trimmed.substring("event:".length()).trim();
                    continue;
                }

                if (trimmed.startsWith("data:")) {
                    dataLines.add(raw.substring(raw.indexOf("data:") + "data:".length()).trim());
                }
            }
        }
    }

    private String buildOpenClawUrl(String baseUrl) {
        String b = baseUrl == null ? "" : baseUrl.trim();
        if (b.isBlank()) return "/v1/responses";

        b = b.replaceAll("^(https?://[^/]+)/:(\\d+)(/.*)?$", "$1:$2$3");
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        if (b.endsWith("/v1")) return b + "/responses";
        if (b.contains("/v1/")) return b;
        return b + "/v1/responses";
    }

    private List<Map<String, Object>> toOpenResponsesInput(List<Message> messages) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (messages == null) return out;
        for (Message m : messages) {
            if (m == null) continue;
            if (m.role == null || m.role.isBlank()) continue;
            String content = m.content == null ? "" : m.content;
            if (content.isBlank()) continue;
            Map<String, Object> item = new HashMap<>();
            item.put("type", "message");
            item.put("role", m.role);
            item.put("content", content);
            out.add(item);
        }
        return out;
    }

    private String extractOpenClawDelta(String eventType, JsonNode json) {
        String delta = json.path("delta").asText("");
        if (delta != null && !delta.isBlank()) return delta;
        delta = json.path("text").asText("");
        if (delta != null && !delta.isBlank()) return delta;
        delta = json.path("content").asText("");
        if (delta != null && !delta.isBlank()) return delta;
        if (eventType != null && eventType.contains("output_text")) {
            JsonNode contentParts = json.path("content");
            if (contentParts.isArray() && contentParts.size() > 0) {
                String t = contentParts.get(0).path("text").asText("");
                if (t != null && !t.isBlank()) return t;
            }
        }
        return "";
    }

    private String extractOpenResponsesText(JsonNode root) {
        String t = root.path("output_text").asText("");
        if (t != null && !t.isBlank()) return t;
        JsonNode output = root.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (content.isArray()) {
                    for (JsonNode part : content) {
                        String text = part.path("text").asText("");
                        if (text != null && !text.isBlank()) return text;
                    }
                }
                String text = item.path("text").asText("");
                if (text != null && !text.isBlank()) return text;
            }
        }
        return "";
    }

    private OpenClawConfig parseOpenClawExtra(String extraConfig) {
        OpenClawConfig cfg = new OpenClawConfig();
        cfg.agentId = "main";
        if (extraConfig == null || extraConfig.isBlank()) return cfg;
        try {
            JsonNode json = objectMapper.readTree(extraConfig);
            String agentId = json.path("agentId").asText("");
            if (agentId != null && !agentId.isBlank()) cfg.agentId = agentId;
            String sessionKey = json.path("sessionKey").asText("");
            if (sessionKey != null && !sessionKey.isBlank()) cfg.sessionKey = sessionKey;
            String user = json.path("user").asText("");
            if (user != null && !user.isBlank()) cfg.user = user;
        } catch (Exception ignored) {
        }
        return cfg;
    }

    private static class OpenClawConfig {
        String agentId;
        String sessionKey;
        String user;
    }

    private Map<String, Object> callOllamaOnce(GenerateRequest req, String model) throws Exception {
        String prompt = buildPrompt(req.messages);
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("stream", false);
        body.put("prompt", prompt);

        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:11434/api/generate"))
            .timeout(REQUEST_TIMEOUT)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> resp = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2) {
            return Map.of("error", resp.body() == null ? ("HTTP " + resp.statusCode()) : resp.body());
        }
        JsonNode json = objectMapper.readTree(resp.body());
        String text = json.path("response").asText("");
        return Map.of("response", text);
    }

    private void streamOllama(GenerateRequest req, String model, HttpServletResponse response) throws Exception {
        String prompt = buildPrompt(req.messages);
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("stream", true);
        body.put("prompt", prompt);

        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:11434/api/generate"))
            .timeout(REQUEST_TIMEOUT)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
            .build();

        HttpResponse<InputStream> resp = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() / 100 != 2) {
            response.setStatus(resp.statusCode());
            response.setContentType(MediaType.TEXT_PLAIN_VALUE);
            response.getWriter().write("HTTP " + resp.statusCode());
            return;
        }

        response.setStatus(200);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");

        try (InputStream is = resp.body();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
             ServletOutputStream out = response.getOutputStream()) {
            out.write(":ok\n\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            response.flushBuffer();
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                JsonNode json = objectMapper.readTree(trimmed);
                String delta = json.path("response").asText("");
                boolean done = json.path("done").asBoolean(false);
                if (!delta.isEmpty()) {
                    writeChunk(out, delta);
                    response.flushBuffer();
                }
                if (done) {
                    writeDone(out);
                    response.flushBuffer();
                    break;
                }
            }
        }
    }

    private Map<String, Object> callDeepSeekOnce(GenerateRequest req, String baseUrl, String apiKey, String model) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("stream", false);
        body.put("messages", toOpenAiMessages(req.messages));

        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(buildDeepSeekUrl(baseUrl)))
            .timeout(REQUEST_TIMEOUT)
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> resp = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2) {
            return Map.of("error", resp.body() == null ? ("HTTP " + resp.statusCode()) : resp.body());
        }

        JsonNode json = objectMapper.readTree(resp.body());
        JsonNode message = json.path("choices").path(0).path("message");
        String text = message.path("content").asText("");
        if (text == null || text.isBlank()) {
            text = message.path("reasoning_content").asText("");
        }
        return Map.of("response", text);
    }

    private void streamDeepSeek(GenerateRequest req, String baseUrl, String apiKey, String model, HttpServletResponse response) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("stream", true);
        body.put("messages", toOpenAiMessages(req.messages));

        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(buildDeepSeekUrl(baseUrl)))
            .timeout(REQUEST_TIMEOUT)
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
            .build();

        HttpResponse<InputStream> resp = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() / 100 != 2) {
            response.setStatus(resp.statusCode());
            response.setContentType(MediaType.TEXT_PLAIN_VALUE);
            try (InputStream is = resp.body()) {
                response.getWriter().write(new String(is.readAllBytes(), StandardCharsets.UTF_8));
            }
            return;
        }

        response.setStatus(200);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");

        try (InputStream is = resp.body();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
             ServletOutputStream out = response.getOutputStream()) {
            out.write(":ok\n\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            response.flushBuffer();
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                if (!trimmed.startsWith("data:")) continue;
                String payload = trimmed.substring("data:".length()).trim();
                if (payload.isEmpty()) continue;
                if ("[DONE]".equals(payload)) {
                    writeDone(out);
                    response.flushBuffer();
                    break;
                }

                JsonNode json = objectMapper.readTree(payload);
                if (json.has("error")) {
                    String error = json.path("error").path("message").asText(json.path("error").asText(""));
                    if (error == null || error.isBlank()) error = payload;
                    writeError(out, error);
                    response.flushBuffer();
                    break;
                }

                JsonNode deltaNode = json.path("choices").path(0).path("delta");
                String delta = deltaNode.path("content").asText("");
                if (delta == null || delta.isBlank()) {
                    delta = deltaNode.path("reasoning_content").asText("");
                }
                if (delta != null && !delta.isBlank()) {
                    writeChunk(out, delta);
                    response.flushBuffer();
                }
            }
        }
    }

    private void writeChunk(ServletOutputStream out, String delta) throws Exception {
        Map<String, Object> line = new HashMap<>();
        line.put("response", delta);
        line.put("done", false);
        writeSseJson(out, line);
    }

    private void writeDone(ServletOutputStream out) throws Exception {
        Map<String, Object> line = new HashMap<>();
        line.put("done", true);
        writeSseJson(out, line);
    }

    private void writeError(ServletOutputStream out, String error) throws Exception {
        Map<String, Object> line = new HashMap<>();
        line.put("error", error);
        line.put("done", true);
        writeSseJson(out, line);
    }

    private void writeSseJson(ServletOutputStream out, Map<String, Object> payload) throws Exception {
        out.write("data: ".getBytes(StandardCharsets.UTF_8));
        out.write(objectMapper.writeValueAsBytes(payload));
        out.write('\n');
        out.write('\n');
        out.flush();
    }

    private boolean isClientAbort(Throwable e) {
        Throwable t = e;
        while (t != null) {
            String name = t.getClass().getName();
            if (name.endsWith("ClientAbortException")) return true;
            if (t instanceof IOException) {
                String msg = t.getMessage();
                if (msg != null) {
                    String m = msg.toLowerCase();
                    if (m.contains("broken pipe") || m.contains("connection reset") || m.contains("forcibly closed")) return true;
                }
            }
            t = t.getCause();
        }
        return false;
    }

    private List<Map<String, String>> toOpenAiMessages(List<Message> messages) {
        List<Map<String, String>> out = new ArrayList<>();
        if (messages == null) return out;
        for (Message m : messages) {
            if (m == null) continue;
            if (m.role == null || m.role.isBlank()) continue;
            String content = m.content == null ? "" : m.content;
            if (content.isBlank()) continue;
            Map<String, String> msg = new HashMap<>();
            msg.put("role", m.role);
            msg.put("content", content);
            out.add(msg);
        }
        return out;
    }

    private String buildDeepSeekUrl(String baseUrl) {
        String b = baseUrl.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        if (b.endsWith("/v1")) return b + "/chat/completions";
        return b + "/v1/chat/completions";
    }

    private String buildPrompt(List<Message> messages) {
        if (messages == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Message m : messages) {
            if (m == null) continue;
            if (m.role == null || m.role.isBlank()) continue;
            String content = m.content == null ? "" : m.content;
            if (content.isBlank()) continue;
            if ("system".equals(m.role)) {
                sb.append(content).append('\n');
                continue;
            }
            if ("user".equals(m.role)) {
                sb.append("用户：").append(content).append('\n');
                continue;
            }
            if ("assistant".equals(m.role)) {
                sb.append("小灵宝：").append(content).append('\n');
            }
        }
        sb.append("小灵宝：");
        return sb.toString();
    }

    private AesGcmEncryptor getEncryptor() {
        AesGcmEncryptor local = encryptor;
        if (local != null) return local;
        synchronized (this) {
            if (encryptor != null) return encryptor;
            String encryptionKey = System.getenv("ACCOUNT_SECRET_KEY");
            if (encryptionKey == null || encryptionKey.isEmpty()) {
                encryptionKey = "MDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDA=";
            }
            encryptor = new AesGcmEncryptor(encryptionKey);
            return encryptor;
        }
    }

    public static class GenerateRequest {
        public Boolean stream;
        public List<Message> messages;
    }

    public static class Message {
        public String role;
        public String content;
    }
}
