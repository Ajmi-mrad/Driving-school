package com.example.financeservice.client;

import com.example.financeservice.client.dto.PaymentNotificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;

/**
 * Client REST vers le communication-service : déclenche un rappel de paiement (relance) qui est
 * persisté et poussé en temps réel au client. L'appel est « best-effort » : un échec est journalisé
 * mais ne fait pas échouer l'opération financière appelante.
 */
@Component
public class NotificationClient {

    private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);

    private final RestClient restClient;

    public NotificationClient(@LoadBalanced RestClient.Builder builder,
                              OAuth2AuthorizedClientManager authorizedClientManager,
                              @Value("${finance.clients.registration-id:keycloak}") String registrationId,
                              @Value("${finance.clients.communication-service-url:http://communication-service}") String communicationServiceUrl) {
        this.restClient = builder.clone()
                .baseUrl(communicationServiceUrl)
                .requestInterceptor(new OAuth2ClientCredentialsInterceptor(authorizedClientManager, registrationId))
                .build();
    }

    /** Envoie une relance ; renvoie {@code true} si le communication-service a bien accepté l'appel. */
    public boolean sendPaymentReminder(String clientId, BigDecimal amount, String message) {
        try {
            restClient.post()
                    .uri("/api/notifications/payment")
                    .body(new PaymentNotificationRequest(clientId, amount, message))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException | OAuth2AuthorizationException ex) {
            // Inclut l'échec d'obtention du jeton (Keycloak indisponible) : l'appel reste best-effort.
            log.warn("Échec de l'envoi du rappel de paiement au client {} : {}", clientId, ex.getMessage());
            return false;
        }
    }
}
