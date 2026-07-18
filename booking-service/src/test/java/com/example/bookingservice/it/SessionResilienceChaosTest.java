package com.example.bookingservice.it;

import com.example.bookingservice.domain.Session;
import com.example.bookingservice.domain.SessionStatus;
import com.example.bookingservice.domain.SessionType;
import com.example.bookingservice.repository.SessionRepository;
import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.ToxiproxyContainer;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chaos testing: put a Toxiproxy in front of the (WireMock) downstreams and inject network
 * faults to prove the service's resilience contract:
 * <ul>
 *   <li>a stalled <b>finance</b> (best-effort) does NOT break session completion, and the call is
 *       bounded by the RestClient read timeout instead of hanging;</li>
 *   <li>a stalled <b>mandatory</b> dependency (auth/vehicle) fails fast (bounded), not forever;</li>
 *   <li>the service recovers once the fault is removed.</li>
 * </ul>
 * The RestClient read timeout is 3s (see {@code RestClientConfig}); faults use 6s latency, so a
 * bounded response (&lt; 5s) demonstrates the timeout firing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
class SessionResilienceChaosTest {

    private static final String CLIENT_SUB = "client-chaos";
    private static final String MONITOR_SUB = "monitor-chaos";
    private static final String STAFF_SUB = "owner-chaos";
    private static final long READ_TIMEOUT_BUDGET_MS = 5_000;

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static final WireMockServer WIREMOCK = new WireMockServer(options().dynamicPort());
    static final ToxiproxyContainer TOXIPROXY =
            new ToxiproxyContainer("ghcr.io/shopify/toxiproxy:2.9.0");

    static Proxy financeProxy;
    static Proxy mandatoryProxy;   // shared by auth-service + vehicle-service

    static {
        POSTGRES.start();
        WIREMOCK.start();
        Testcontainers.exposeHostPorts(WIREMOCK.port());
        TOXIPROXY.start();
        try {
            ToxiproxyClient client = new ToxiproxyClient(TOXIPROXY.getHost(), TOXIPROXY.getControlPort());
            String upstream = "host.testcontainers.internal:" + WIREMOCK.port();
            financeProxy = client.createProxy("finance", "0.0.0.0:8666", upstream);
            mandatoryProxy = client.createProxy("mandatory", "0.0.0.0:8667", upstream);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to set up Toxiproxy proxies", e);
        }
    }

    @AfterAll
    static void tearDown() {
        TOXIPROXY.stop();
        WIREMOCK.stop();
        POSTGRES.stop();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        String financeUri = "http://" + TOXIPROXY.getHost() + ":" + TOXIPROXY.getMappedPort(8666);
        String mandatoryUri = "http://" + TOXIPROXY.getHost() + ":" + TOXIPROXY.getMappedPort(8667);
        registry.add("spring.cloud.discovery.client.simple.instances.finance-service[0].uri", () -> financeUri);
        registry.add("spring.cloud.discovery.client.simple.instances.auth-service[0].uri", () -> mandatoryUri);
        registry.add("spring.cloud.discovery.client.simple.instances.vehicle-service[0].uri", () -> mandatoryUri);
        // Token endpoint stays direct (not part of the chaos), served by WireMock.
        registry.add("spring.security.oauth2.client.provider.keycloak.token-uri",
                () -> "http://localhost:" + WIREMOCK.port() + "/token");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionRepository sessionRepository;

    @BeforeEach
    void setUp() {
        WIREMOCK.resetAll();
        sessionRepository.deleteAll();
        WIREMOCK.stubFor(WireMock.post(urlPathEqualTo("/token")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"access_token\":\"t\",\"token_type\":\"Bearer\",\"expires_in\":300}")));
    }

    @AfterEach
    void clearToxics() throws IOException {
        for (var toxic : financeProxy.toxics().getAll()) {
            toxic.remove();
        }
        for (var toxic : mandatoryProxy.toxics().getAll()) {
            toxic.remove();
        }
    }

    @Test
    void completionSurvivesStalledFinance_andIsBoundedByReadTimeout() throws Exception {
        WIREMOCK.stubFor(WireMock.post(urlPathEqualTo("/api/enrollments/consume"))
                .willReturn(aResponse().withStatus(200)));
        // Finance responds far slower than the read timeout.
        financeProxy.toxics().latency("finance-latency", ToxicDirection.DOWNSTREAM, 6_000);

        Session session = persistCompletableSession();

        long startMs = System.currentTimeMillis();
        mockMvc.perform(patch("/api/sessions/{id}/complete", session.getId()).with(staff(STAFF_SUB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        long elapsed = System.currentTimeMillis() - startMs;

        // Completion succeeded despite finance being unreachable (best-effort), and it did not hang.
        assertThat(elapsed).isLessThan(READ_TIMEOUT_BUDGET_MS);
        assertThat(sessionRepository.findById(session.getId()).orElseThrow().getStatus())
                .isEqualTo(SessionStatus.COMPLETED);
    }

    @Test
    void bookingFailsFastWhenMandatoryDependencyStalls() throws Exception {
        stubUser(CLIENT_SUB, "CLIENT", true);
        stubUser(MONITOR_SUB, "MONITOR", true);
        // The first mandatory call (validate client) stalls beyond the read timeout.
        mandatoryProxy.toxics().latency("auth-latency", ToxicDirection.DOWNSTREAM, 6_000);

        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant end = start.plus(60, ChronoUnit.MINUTES);
        String body = """
                {"type":"DRIVING","clientId":"%s","monitorId":"%s","startTime":"%s","endTime":"%s"}
                """.formatted(CLIENT_SUB, MONITOR_SUB, start, end);

        long startMs = System.currentTimeMillis();
        mockMvc.perform(post("/api/sessions").with(staff(STAFF_SUB))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is5xxServerError());
        long elapsed = System.currentTimeMillis() - startMs;

        assertThat(elapsed).isLessThan(READ_TIMEOUT_BUDGET_MS);   // bounded failure, not an infinite hang
        assertThat(sessionRepository.count()).isZero();
    }

    @Test
    void recoversAfterFaultRemoved() throws Exception {
        WIREMOCK.stubFor(WireMock.post(urlPathEqualTo("/api/enrollments/consume"))
                .willReturn(aResponse().withStatus(200)));
        // No toxics active (cleared by @AfterEach of previous tests / none added here).
        Session session = persistCompletableSession();

        mockMvc.perform(patch("/api/sessions/{id}/complete", session.getId()).with(staff(STAFF_SUB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        WIREMOCK.verify(postRequestedFor(urlPathEqualTo("/api/enrollments/consume")));
    }

    // ---- helpers ----

    private Session persistCompletableSession() {
        Instant end = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant start = end.minus(90, ChronoUnit.MINUTES);
        Session session = new Session();
        session.setType(SessionType.DRIVING);
        session.setClientId(CLIENT_SUB);
        session.setMonitorId(MONITOR_SUB);
        session.setVehicleId(UUID.randomUUID());
        session.setStartTime(start);
        session.setEndTime(end);
        session.setStatus(SessionStatus.CONFIRMED);
        return sessionRepository.saveAndFlush(session);
    }

    private void stubUser(String keycloakId, String role, boolean active) {
        WIREMOCK.stubFor(get(urlPathEqualTo("/api/users/by-keycloak/" + keycloakId)).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"keycloakId\":\"" + keycloakId + "\",\"roles\":[\"" + role + "\"],\"active\":" + active + "}")));
    }

    private static JwtRequestPostProcessor staff(String sub) {
        return jwt().jwt(jwt -> jwt.subject(sub)).authorities(new SimpleGrantedAuthority("ROLE_OWNER"));
    }
}
