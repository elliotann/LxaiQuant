package com.chain.ai.trade.engine.service.prompt;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class MarkdownPromptTemplateService {

    @Autowired
    private Environment environment;

    private static final String LIVE_ADVICE_TEMPLATE_PROPERTY = "advice.live.template";
    private static final String LIVE_ADVICE_TEMPLATE_PATH_PROPERTY = "advice.live.templatePath";
    private static final String LIVE_ADVICE_NON_OPENCLAW_TEMPLATE_PROPERTY = "advice.live.nonOpenclaw.template";
    private static final String LIVE_ADVICE_NON_OPENCLAW_TEMPLATE_PATH_PROPERTY = "advice.live.nonOpenclaw.templatePath";
    private static final String AI_FILTER_SIGNAL_TEMPLATE_PROPERTY = "ai.filter.signal.template";
    private static final String AI_FILTER_SIGNAL_TEMPLATE_PATH_PROPERTY = "ai.filter.signal.templatePath";

    public LoadedTemplate loadLiveAdviceTemplate() {
        return loadLiveAdviceTemplate(this.environment);
    }

    public LoadedTemplate loadLiveAdviceTemplate(Environment environment) {
        String name = environment != null ? environment.getProperty(LIVE_ADVICE_TEMPLATE_PROPERTY) : null;
        if (name == null || name.isBlank()) {
            name = "live_advice_v1";
        }

        String configuredPath = environment != null ? environment.getProperty(LIVE_ADVICE_TEMPLATE_PATH_PROPERTY) : null;
        return loadTemplate(name, configuredPath);
    }

    public LoadedTemplate loadLiveAdviceNonOpenclawTemplate(Environment environment) {
        String name = environment != null ? environment.getProperty(LIVE_ADVICE_NON_OPENCLAW_TEMPLATE_PROPERTY) : null;
        if (name == null || name.isBlank()) {
            name = "live_advice_non_openclaw_v1";
        }

        String configuredPath = environment != null ? environment.getProperty(LIVE_ADVICE_NON_OPENCLAW_TEMPLATE_PATH_PROPERTY) : null;
        return loadTemplate(name, configuredPath);
    }

    public LoadedTemplate loadAiFilterSignalTemplate(Environment environment) {
        String name = environment != null ? environment.getProperty(AI_FILTER_SIGNAL_TEMPLATE_PROPERTY) : null;
        if (name == null || name.isBlank()) {
            name = "ai_filter_signal_v1";
        }

        String configuredPath = environment != null ? environment.getProperty(AI_FILTER_SIGNAL_TEMPLATE_PATH_PROPERTY) : null;
        return loadTemplate(name, configuredPath);
    }

    public String render(String template, Map<String, String> vars) {
        if (template == null) return "";
        String out = template;
        if (vars != null) {
            for (Map.Entry<String, String> e : vars.entrySet()) {
                String k = e.getKey();
                if (k == null || k.isBlank()) continue;
                String v = e.getValue() == null ? "" : e.getValue();
                out = out.replace("{{" + k + "}}", v);
            }
        }
        return out;
    }

    private LoadedTemplate loadTemplate(String name, String configuredPath) {
        String md = readMarkdown(name, configuredPath);
        Sections s = parseSections(md);
        LoadedTemplate out = new LoadedTemplate();
        out.name = name;
        out.system = s.system;
        out.user = s.user;
        return out;
    }

    private String readMarkdown(String name, String configuredPath) {
        try {
            if (configuredPath != null && !configuredPath.isBlank()) {
                Path p = Path.of(configuredPath.trim());
                if (Files.exists(p)) {
                    return Files.readString(p, StandardCharsets.UTF_8);
                }
            }

            Path docPath1 = Path.of("ai-quant", "docs", "prompts", name + ".md");
            if (Files.exists(docPath1)) {
                return Files.readString(docPath1, StandardCharsets.UTF_8);
            }
            Path docPath2 = Path.of("docs", "prompts", name + ".md");
            if (Files.exists(docPath2)) {
                return Files.readString(docPath2, StandardCharsets.UTF_8);
            }

            ClassPathResource r = new ClassPathResource("prompts/" + name + ".md");
            if (r.exists()) {
                return new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private Sections parseSections(String md) {
        String text = md == null ? "" : md;
        String system = extractBlock(text, "<!--SYSTEM-->", "<!--/SYSTEM-->");
        String user = extractBlock(text, "<!--USER-->", "<!--/USER-->");
        if (system.isBlank() || user.isBlank()) {
            Map<String, String> byHeading = parseByHeadings(text);
            if (system.isBlank()) system = byHeading.getOrDefault("system", "");
            if (user.isBlank()) user = byHeading.getOrDefault("user", "");
        }
        Sections s = new Sections();
        s.system = system.trim();
        s.user = user.trim();
        return s;
    }

    private String extractBlock(String text, String startMarker, String endMarker) {
        int s = text.indexOf(startMarker);
        if (s < 0) return "";
        int e = text.indexOf(endMarker, s + startMarker.length());
        if (e < 0) return "";
        return text.substring(s + startMarker.length(), e);
    }

    private Map<String, String> parseByHeadings(String md) {
        Map<String, String> out = new HashMap<>();
        String[] lines = (md == null ? "" : md).split("\\R");
        String current = null;
        StringBuilder buf = new StringBuilder();
        for (String line : lines) {
            String t = line == null ? "" : line.trim();
            if (t.startsWith("##")) {
                if (current != null) {
                    out.put(current, buf.toString().trim());
                }
                buf.setLength(0);
                String h = t.replaceFirst("^##+\\s*", "").trim().toLowerCase(Locale.ROOT);
                if (h.contains("system")) current = "system";
                else if (h.contains("user")) current = "user";
                else current = null;
                continue;
            }
            if (current != null) {
                buf.append(line).append('\n');
            }
        }
        if (current != null) {
            out.put(current, buf.toString().trim());
        }
        return out;
    }

    public static class LoadedTemplate {
        public String name;
        public String system;
        public String user;
    }

    private static class Sections {
        String system;
        String user;
    }
}

