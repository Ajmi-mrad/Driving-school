package com.example.bookingservice.client;

import com.example.bookingservice.client.dto.NotificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Client REST vers le communication-service : émet les notifications in-app liées au cycle de vie
 * d'une séance (demande, validation, refus, report, annulation). Les appels portent un jeton de
 * service ({@code client_credentials}, rôle {@code SERVICE}).
 *
 * <p>« Best-effort » par conception : une notification ne doit jamais faire échouer la réservation
 * ou la décision qui l'a déclenchée. Tout échec (réseau, jeton, service indisponible) est journalisé
 * puis ignoré ; les appelants l'invoquent après le commit de la transaction métier.
 */
@Component
public class CommunicationClient {

    private static final Logger log = LoggerFactory.getLogger(CommunicationClient.class);

    private final RestClient restClient;

    public CommunicationClient(@LoadBalanced RestClient.Builder builder,
                               OAuth2AuthorizedClientManager authorizedClientManager,
                               @Value("${booking.clients.registration-id:keycloak}") String registrationId,
                               @Value("${booking.clients.communication-service-url:http://communication-service}") String communicationServiceUrl) {
        this.restClient = builder.clone()
                .baseUrl(communicationServiceUrl)
                .requestInterceptor(new OAuth2ClientCredentialsInterceptor(authorizedClientManager, registrationId))
                .build();
    }

    /** Envoie une notification. Aucun effet visible en cas d'échec (best-effort). */
    public void notify(NotificationRequest request) {
        try {
            restClient.post()
                    .uri("/api/notifications/booking")
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            // Best-effort : on englobe tout échec (RestClientException, échec d'obtention du jeton,
            // aucune instance résolue par le load balancer…) pour ne jamais perturber le planning.
            log.warn("Échec d'envoi de la notification {} au destinataire {} : {}",
                    request.type(), request.recipientId(), ex.getMessage());
        }
    }
}