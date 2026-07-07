package com.example.financeservice.client;

import com.example.financeservice.client.dto.UserInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * Client REST vers l'auth-service : résolution d'un utilisateur par son identifiant Keycloak.
 * Authentifié en {@code client_credentials} et résolu via Eureka ({@code http://auth-service}).
 */
@Component
public class UserClient {

    private final RestClient restClient;

    public UserClient(@LoadBalanced RestClient.Builder builder,
                      OAuth2AuthorizedClientManager authorizedClientManager,
                      @Value("${finance.clients.registration-id:keycloak}") String registrationId,
                      @Value("${finance.clients.auth-service-url:http://auth-service}") String authServiceUrl) {
        this.restClient = builder.clone()
                .baseUrl(authServiceUrl)
                .requestInterceptor(new OAuth2ClientCredentialsInterceptor(authorizedClientManager, registrationId))
                .build();
    }

    public Optional<UserInfo> getByKeycloakId(String keycloakId) {
        try {
            UserInfo info = restClient.get()
                    .uri("/api/users/by-keycloak/{kid}", keycloakId)
                    .retrieve()
                    .body(UserInfo.class);
            return Optional.ofNullable(info);
        } catch (HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        }
    }
}
