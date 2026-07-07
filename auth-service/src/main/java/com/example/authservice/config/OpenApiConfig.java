package com.example.authservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentation OpenAPI / Swagger. Le schéma de sécurité {@code keycloak} est un flux OAuth2 : le
 * bouton « Authorize » de Swagger UI dialogue directement avec Keycloak pour obtenir un JWT (flux
 * {@code password} — identifiants saisis dans la pop-up — ou {@code authorizationCode} avec PKCE).
 * Les URL pointent vers Keycloak tel que joignable <b>depuis le navigateur</b> ({@code localhost:8080}
 * en dev), surchargeable par variable d'env.
 */
@Configuration
public class OpenApiConfig {

    private static final String SCHEME = "keycloak";

    @Bean
    public OpenAPI authOpenApi(
            @Value("${auth.swagger.auth-url}") String authUrl,
            @Value("${auth.swagger.token-url}") String tokenUrl) {

        SecurityScheme keycloak = new SecurityScheme()
                .type(SecurityScheme.Type.OAUTH2)
                .description("JWT Keycloak (realm auto-ecole). Flux password ou authorization_code (PKCE).")
                .flows(new OAuthFlows()
                        .password(new OAuthFlow().tokenUrl(tokenUrl))
                        .authorizationCode(new OAuthFlow()
                                .authorizationUrl(authUrl)
                                .tokenUrl(tokenUrl)));

        return new OpenAPI()
                .info(new Info()
                        .title("Auth Service API")
                        .version("v1")
                        .description("Gestion des utilisateurs de l'auto-école (synchronisation Keycloak ↔ base locale)"))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME))
                .components(new Components().addSecuritySchemes(SCHEME, keycloak));
    }
}
