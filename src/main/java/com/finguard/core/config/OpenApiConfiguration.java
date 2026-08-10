package com.finguard.core.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI finGuardOpenApi() {
        SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description(
                        "Use the JWT returned by POST /api/auth/login"
                );

        return new OpenAPI()
                .info(new Info()
                        .title("FinGuard Core API")
                        .version("v1")
                        .description(
                                "Transaction import, reconciliation, "
                                        + "exception review and audit API"
                        ))
                .components(new Components().addSecuritySchemes(
                        BEARER_AUTH,
                        bearerScheme
                ))
                .addSecurityItem(new SecurityRequirement().addList(
                        BEARER_AUTH
                ));
    }
}
