package com.example.authservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Documentation OpenAPI / Swagger. Déclare un schéma de sécurité {@code bearer-jwt} (JWT Keycloak)
 * appliqué globalement, ce qui active le bouton « Authorize » dans Swagger UI.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Auth Service API",
                version = "v1",
                description = "Gestion des utilisateurs de l'auto-école (synchronisation Keycloak ↔ base locale)"),
        security = @SecurityRequirement(name = "bearer-jwt"))
@SecurityScheme(
        name = "bearer-jwt",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {
}
