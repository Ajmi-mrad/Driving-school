package com.example.bookingservice.config;

import java.time.Duration;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.client.RestClient;

/**
 * Infrastructure des appels inter-services :
 * <ul>
 *   <li>un {@link RestClient.Builder} {@link LoadBalanced @LoadBalanced} : les URIs du type
 *       {@code http://auth-service} sont résolues via Eureka + Spring Cloud LoadBalancer ;</li>
 *   <li>un {@link OAuth2AuthorizedClientManager} non-interactif ({@code client_credentials}) qui
 *       obtient et rafraîchit le jeton de service utilisé par {@code UserClient}/{@code VehicleClient}.</li>
 * </ul>
 */
@Configuration
public class RestClientConfig {

    @Bean
    @LoadBalanced
    RestClient.Builder loadBalancedRestClientBuilder() {
        // Bounded connect/read timeouts so a stalled downstream fails fast instead of
        // hanging the request thread indefinitely (best-effort calls stay best-effort).
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(2))
                .withReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder()
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings));
    }

    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository clientRegistrationRepository,
                                                          OAuth2AuthorizedClientService authorizedClientService) {
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrationRepository,
                        authorizedClientService);
        manager.setAuthorizedClientProvider(
                OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
        return manager;
    }
}