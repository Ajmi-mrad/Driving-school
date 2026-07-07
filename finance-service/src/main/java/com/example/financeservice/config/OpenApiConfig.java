package com.example.financeservice.config;

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
                title = "Finance Service API",
                version = "v1",
                description = "Gestion financière : forfaits, inscriptions, paiements et factures"),
        security = @SecurityRequirement(name = "bearer-jwt"))
@SecurityScheme(
        name = "bearer-jwt",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {
}
