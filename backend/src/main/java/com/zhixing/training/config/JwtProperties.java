package com.zhixing.training.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zhixing.jwt")
public record JwtProperties(String issuer, String audience, int accessMinutes, int refreshHours, String secret) { }
