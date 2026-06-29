package com.example.authservice.config;

import com.example.authservice.keycloak.KeycloakProperties;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Construit le client Admin Keycloak. Il s'authentifie en {@code client_credentials} avec le
 * client confidentiel {@code auth-service} (dont le compte de service porte les rôles
 * realm-management : manage-users, view-users, query-users).
 */
@Configuration
@EnableConfigurationProperties(KeycloakProperties.class)
public class KeycloakAdminConfig {

    @Bean
    public Keycloak keycloak(KeycloakProperties properties) {
        return KeycloakBuilder.builder()
                .serverUrl(properties.getServerUrl())
                .realm(properties.getRealm())
                .clientId(properties.getClientId())
                .clientSecret(properties.getClientSecret())
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .build();
    }
}