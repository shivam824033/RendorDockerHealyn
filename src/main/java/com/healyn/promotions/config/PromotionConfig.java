package com.healyn.promotions.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PromotionProperties.class)
public class PromotionConfig {
}
