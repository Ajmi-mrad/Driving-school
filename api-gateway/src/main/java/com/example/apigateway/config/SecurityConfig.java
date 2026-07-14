package com.example.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Sécurité de l'API Gateway (pile réactive WebFlux). La Gateway agit comme Resource Server : elle
 * valide la signature du JWT Keycloak (via le {@code jwk-set-uri}) et laisse passer le jeton vers les
 * microservices, qui appliquent ensuite leurs propres règles de rôles (RBAC). Cf. cahier §6.3.3.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        // Préflight CORS : jamais authentifié.
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Sondes et documentation : publiques.
                        .pathMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .pathMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(org.springframework.security.config.Customizer.withDefaults()));
        return http.build();
    }
}