package com.example.bookingservice.client;

import com.example.bookingservice.client.dto.UserInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * Client REST vers l'auth-service (résolu via Eureka). Permet de valider qu'un élève/moniteur existe,
 * est actif et possède le bon rôle. Les appels portent un jeton de service ({@code client_credentials}).
 */
@Component
public class UserClient {

    private final RestClient restClient;

    public UserClient(@LoadBalanced RestClient.Builder builder,
                      OAuth2AuthorizedClientManager authorizedClientManager,
                      @Value("${booking.clients.registration-id:keycloak}") String registrationId,
                      @Value("${booking.clients.auth-service-url:http://auth-service}") String authServiceUrl) {
        this.restClient = builder.clone()
                .baseUrl(authServiceUrl)
                .requestInterceptor(new OAuth2ClientCredentialsInterceptor(authorizedClientManager, registrationId))
                .build();
    }

    /** Recherche un utilisateur par son identifiant Keycloak ({@code sub}). Vide si introuvable (404). */
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