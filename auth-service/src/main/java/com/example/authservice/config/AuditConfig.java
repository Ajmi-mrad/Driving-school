package com.example.authservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

/**
 * Active l'audit JPA et fournit l'auditeur courant : l'identifiant Keycloak ({@code sub}) extrait
 * du JWT de la requête, ou {@code "system"} en l'absence de contexte de sécurité (démarrage,
 * tâches techniques, tests).
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class AuditConfig {

    static final String SYSTEM = "system";

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return Optional.of(SYSTEM);
            }
            if (authentication.getPrincipal() instanceof Jwt jwt) {
                return Optional.of(jwt.getSubject());
            }
            return Optional.of(authentication.getName());
        };
    }
}