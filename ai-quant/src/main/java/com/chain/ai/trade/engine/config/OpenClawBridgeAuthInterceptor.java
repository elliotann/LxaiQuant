package com.chain.ai.trade.engine.config;

import com.chain.ai.trade.common.entity.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class OpenClawBridgeAuthInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;
    private final Environment environment;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith("/api/openclaw/")) {
            return true;
        }

        String required = environment.getProperty("openclaw.bridge.token");
        if (required == null || required.isBlank()) {
            required = System.getenv("OPENCLAW_BRIDGE_TOKEN");
        }
        if (required == null || required.isBlank()) {
            writeJson(response, 503, ApiResponse.error(503, "OPENCLAW_BRIDGE_TOKEN 未配置（请在 application.yml 配置 openclaw.bridge.token 或设置环境变量 OPENCLAW_BRIDGE_TOKEN）"));
            return false;
        }

        String provided = request.getHeader("X-OpenClaw-Token");
        if (provided == null || provided.isBlank() || !required.equals(provided)) {
            writeJson(response, 401, ApiResponse.error(401, "unauthorized"));
            return false;
        }

        return true;
    }

    private void writeJson(HttpServletResponse response, int status, Object body) throws Exception {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
        response.getWriter().flush();
    }
}
