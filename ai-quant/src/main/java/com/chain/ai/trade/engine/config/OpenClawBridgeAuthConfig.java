package com.chain.ai.trade.engine.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class OpenClawBridgeAuthConfig implements WebMvcConfigurer {

    private final OpenClawBridgeAuthInterceptor openClawBridgeAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(openClawBridgeAuthInterceptor).addPathPatterns("/api/openclaw/**");
    }
}

