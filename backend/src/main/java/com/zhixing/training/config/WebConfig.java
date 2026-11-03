package com.zhixing.training.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class WebConfig implements WebMvcConfigurer {
    private final String[] origins;
    public WebConfig(org.springframework.core.env.Environment env) {
        origins = Arrays.stream(env.getProperty("zhixing.cors-origins", "http://127.0.0.1:5173").split(","))
                .map(String::trim).filter(s -> !s.isBlank()).toArray(String[]::new);
    }
    @Override public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins(origins).allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "X-XSRF-TOKEN", "X-Request-Id").allowCredentials(true).maxAge(3600);
    }
}
