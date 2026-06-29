package com.example.bookingservice.client;

import com.example.bookingservice.client.dto.VehicleInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Client REST vers le vehicle-service (résolu via Eureka). Permet de récupérer un véhicule et de
 * lister les véhicules disponibles pour l'affectation automatique.
 */
@Component
public class VehicleClient {

    private final RestClient restClient;

    public VehicleClient(@LoadBalanced RestClient.Builder builder,
                         OAuth2AuthorizedClientManager authorizedClientManager,
                         @Value("${booking.clients.registration-id:keycloak}") String registrationId,
                         @Value("${booking.clients.vehicle-service-url:http://vehicle-service}") String vehicleServiceUrl) {
        this.restClient = builder.clone()
                .baseUrl(vehicleServiceUrl)
                .requestInterceptor(new OAuth2ClientCredentialsInterceptor(authorizedClientManager, registrationId))
                .build();
    }

    /** Récupère un véhicule par son id. Vide si introuvable (404). */
    public Optional<VehicleInfo> get(UUID vehicleId) {
        try {
            VehicleInfo info = restClient.get()
                    .uri("/api/vehicles/{id}", vehicleId)
                    .retrieve()
                    .body(VehicleInfo.class);
            return Optional.ofNullable(info);
        } catch (HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        }
    }

    /** Liste les véhicules au statut AVAILABLE (disponibilité « courante », non liée au créneau). */
    public List<VehicleInfo> listAvailable() {
        VehicleInfo[] vehicles = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/vehicles")
                        .queryParam("status", VehicleInfo.AVAILABLE)
                        .build())
                .retrieve()
                .body(VehicleInfo[].class);
        return vehicles == null ? List.of() : Arrays.asList(vehicles);
    }
}