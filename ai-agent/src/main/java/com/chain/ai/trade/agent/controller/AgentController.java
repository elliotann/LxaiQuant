package com.chain.ai.trade.agent.controller;

import com.chain.ai.trade.agent.service.AgentService;
import com.chain.ai.trade.agent.service.SkillLoader;
import dev.langchain4j.service.TokenStream;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentService agentService;
    private final SkillLoader skillLoader;

    public AgentController(AgentService agentService, SkillLoader skillLoader) {
        this.agentService = agentService;
        this.skillLoader = skillLoader;
    }

    @PostMapping("/chat")
    public ResponseEntity<AgentResponse> chat(@RequestBody AgentRequest request) {
        String reply = agentService.chat(request.getSkill(), request.getMessage(), request.getSessionId());
        return ResponseEntity.ok(new AgentResponse(reply));
    }

    @PostMapping("/chat/stream")
    public SseEmitter chatStream(@RequestBody AgentRequest request) {
        SseEmitter emitter = new SseEmitter(300_000L);

        try {
            TokenStream tokenStream = agentService.chatStream(
                    request.getSkill(), request.getMessage(), request.getSessionId());

            tokenStream
                    .onPartialResponse(chunk -> {
                        try {
                            Map<String, Object> data = new HashMap<>();
                            data.put("response", chunk);
                            emitter.send(SseEmitter.event().data(data));
                        } catch (IOException e) {
                            // client disconnected
                        }
                    })
                    .onCompleteResponse(response -> {
                        try {
                            Map<String, Object> data = new HashMap<>();
                            data.put("response", "");
                            data.put("done", true);
                            emitter.send(SseEmitter.event().data(data));
                        } catch (IOException e) {
                            // ignore
                        }
                        emitter.complete();
                    })
                    .onError(emitter::completeWithError)
                    .start();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }

    @GetMapping("/skills")
    public ResponseEntity<List<String>> listSkills() {
        return ResponseEntity.ok(skillLoader.getSkillNames());
    }

    @Data
    @AllArgsConstructor
    public static class AgentRequest {
        private String skill;
        private String message;
        private String sessionId;
    }

    @Data
    @AllArgsConstructor
    public static class AgentResponse {
        private String response;
    }
}
