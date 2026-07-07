package com.example.bookingservice.client;

import com.example.bookingservice.client.dto.ConsumeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Client REST vers le finance-service : signale la consommation d'une séance réalisée afin de
 * décompter les heures/séances de l'inscription active de l'élève. L'appel est « best-effort » :
 * un échec est journalisé mais n'empêche pas la clôture de la séance côté planning.
 */
@Component
public class FinanceClient {

    private static final Logger log = LoggerFactory.getLogger(FinanceClient.class);

    private final RestClient restClient;

    public FinanceClient(@LoadBalanced RestClient.Builder builder,
                         OAuth2AuthorizedClientManager authorizedClientManager,
                         @Value("${booking.clients.registration-id:keycloak}") String registrationId,
                         @Value("${booking.clients.finance-service-url:http://finance-service}") String financeServiceUrl) {
        this.restClient = builder.clone()
                .baseUrl(financeServiceUrl)
                .requestInterceptor(new OAuth2ClientCredentialsInterceptor(authorizedClientManager, registrationId))
                .build();
    }

    public void reportConsumption(String clientId, int drivingHours, int codeSessions) {
        try {
            restClient.post()
                    .uri("/api/enrollments/consume")
                    .body(new ConsumeRequest(clientId, drivingHours, codeSessions))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException | OAuth2AuthorizationException ex) {
            // Inclut l'échec d'obtention du jeton (Keycloak indisponible) : l'appel reste best-effort.
            log.warn("Échec du décompte de consommation (client {}) auprès du finance-service : {}",
                    clientId, ex.getMessage());
        }
    }
}
