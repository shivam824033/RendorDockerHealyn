package com.healyn.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AuthProperties.Jwt.class, AuthProperties.Password.class})
public class AuthConfig {
}
