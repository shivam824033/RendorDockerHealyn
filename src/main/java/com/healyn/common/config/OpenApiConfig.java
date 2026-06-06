package com.healyn.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI healynOpenApi(@Value("${server.port:8080}") String port) {
        return new OpenAPI()
                .info(new Info()
                        .title("Healyn API")
                        .version("0.1.0")
                        .description("""
                                Healyn — physiotherapy clinic backend.

                                **How to use this page**
                                1. Register or log in via the `auth` endpoints to obtain an `accessToken`.
                                2. Click **Authorize** (top right), paste the token, hit **Authorize**.
                                3. Every protected endpoint will now send `Authorization: Bearer <token>` automatically.

                                Tokens expire after 15 minutes. Use `POST /auth/refresh` to rotate.""")
                        .contact(new Contact().name("Healyn engineering"))
                        .license(new License().name("Proprietary")))
                .servers(List.of(new Server().url("http://localhost:" + port).description("Local")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME_NAME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the `accessToken` returned by /auth/login or /auth/register/complete.")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME));
    }
}
