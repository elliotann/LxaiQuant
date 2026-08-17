package com.chain.ai.trade.agent.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SkillLoader {

    private final Map<String, String> skillMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void load() {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources("classpath*:skills/*.md");
            if (resources.length == 0) {
                log.warn("No skill files found at classpath*:skills/*.md — skillMap will be empty");
                return;
            }
            for (Resource resource : resources) {
                String name = resource.getFilename().replace(".md", "");
                String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                skillMap.put(name, content);
                log.info("Loaded skill: {}", name);
            }
        } catch (IOException e) {
            log.warn("Failed to load skills from classpath*:skills/", e);
        }
    }

    public String getSkill(String name) {
        return skillMap.get(name);
    }

    public List<String> getSkillNames() {
        return new ArrayList<>(skillMap.keySet());
    }
}
