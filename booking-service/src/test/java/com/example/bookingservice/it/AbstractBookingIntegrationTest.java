package com.example.bookingservice.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Base for booking-service integration tests. Provides a real Postgres (Testcontainers +
 * Flyway) for the app under test and a single WireMock that impersonates every downstream
 * (auth-service, vehicle-service, finance-service) plus the Keycloak token endpoint.
 *
 * <p>The production clients are {@code @LoadBalanced} and call {@code http://<service>}; the
 * {@code it} profile disables Eureka and we register static SimpleDiscoveryClient instances that
 * point those service ids at WireMock, so the real load-balanced RestClient path is exercised.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Testcontainers
abstract class AbstractBookingIntegrationTest {

    @Container
    @org.springframework.boot.testcontainers.service.connection.ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static final WireMockServer WIREMOCK = new WireMockServer(options().dynamicPort());

    static {
        WIREMOCK.start();
    }

    @AfterAll
    static void stopWireMock() {
        WIREMOCK.stop();
    }

    @DynamicPropertySource
    static void downstreamProperties(DynamicPropertyRegistry registry) {
        String base = "http://localhost:" + WIREMOCK.port();
        // Load balancer resolves these service ids from static discovery -> WireMock.
        registry.add("spring.cloud.discovery.client.simple.instances.auth-service[0].uri", () -> base);
        registry.add("spring.cloud.discovery.client.simple.instances.vehicle-service[0].uri", () -> base);
        registry.add("spring.cloud.discovery.client.simple.instances.finance-service[0].uri", () -> base);
        // OAuth2 client_credentials token endpoint (direct, not load-balanced).
        registry.add("spring.security.oauth2.client.provider.keycloak.token-uri", () -> base + "/token");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @BeforeEach
    void resetWireMock() {
        WIREMOCK.resetAll();
        stubTokenEndpoint();
    }

    // ---- WireMock stubs ----

    /** Keycloak client_credentials token endpoint: any downstream call first fetches a service token. */
    protected void stubTokenEndpoint() {
        WIREMOCK.stubFor(post(urlPathEqualTo("/token")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"access_token\":\"test-token\",\"token_type\":\"Bearer\",\"expires_in\":300}")));
    }

    protected void stubUser(String keycloakId, String role, boolean active) {
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/users/by-keycloak/" + keycloakId)).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"keycloakId\":\"" + keycloakId + "\",\"roles\":[\"" + role + "\"],\"active\":" + active + "}")));
    }

    protected void stubUserNotFound(String keycloakId) {
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/users/by-keycloak/" + keycloakId))
                .willReturn(aResponse().withStatus(404)));
    }

    protected void stubAvailableVehicles(String... vehicleIds) {
        StringBuilder body = new StringBuilder("[");
        for (int i = 0; i < vehicleIds.length; i++) {
            if (i > 0) {
                body.append(',');
            }
            body.append("{\"id\":\"").append(vehicleIds[i]).append("\",\"status\":\"AVAILABLE\"}");
        }
        body.append(']');
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/vehicles"))
                .withQueryParam("status", equalTo("AVAILABLE"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(body.toString())));
    }

    protected void stubConsumeOk() {
        WIREMOCK.stubFor(post(urlPathEqualTo("/api/enrollments/consume"))
                .willReturn(aResponse().withStatus(200)));
    }

    // ---- Inbound auth helpers (JWT injected via spring-security-test) ----

    protected static JwtRequestPostProcessor staff(String sub) {
        return jwt().jwt(jwt -> jwt.subject(sub)).authorities(new SimpleGrantedAuthority("ROLE_OWNER"));
    }

    protected static JwtRequestPostProcessor client(String sub) {
        return jwt().jwt(jwt -> jwt.subject(sub)).authorities(new SimpleGrantedAuthority("ROLE_CLIENT"));
    }
}