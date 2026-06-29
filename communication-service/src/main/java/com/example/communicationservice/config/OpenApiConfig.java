package com.example.communicationservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Documentation OpenAPI / Swagger : schéma de sécurité {@code bearer-jwt} appliqué globalement
 * (bouton « Authorize » dans Swagger UI).
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Communication Service API",
                version = "v1",
                description = "Messagerie temps réel entre moniteurs et élèves (WebSocket/STOMP)"),
        security = @SecurityRequirement(name = "bearer-jwt"))
@SecurityScheme(
        name = "bearer-jwt",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {
}