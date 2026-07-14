package com.example.financeservice.it;

import com.example.financeservice.domain.Enrollment;
import com.example.financeservice.domain.EnrollmentStatus;
import com.example.financeservice.domain.Forfait;
import com.example.financeservice.repository.EnrollmentRepository;
import com.example.financeservice.repository.ForfaitRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.ToxiproxyContainer;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chaos test for the payment-reminder flow. A Toxiproxy sits in front of the (WireMock)
 * communication-service. The reminder is best-effort:
 * <ul>
 *   <li>when communication is healthy, the reminder is delivered and the endpoint returns 204;</li>
 *   <li>when communication stalls, the endpoint degrades to a bounded 502 (not a hang, not a 500),
 *       because the RestClient read timeout (3s) fires and {@code NotificationClient} reports
 *       non-delivery.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
class PaymentReminderChaosTest {

    private static final String CLIENT = "client-remind";
    private static final long READ_TIMEOUT_BUDGET_MS = 5_000;

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static final WireMockServer WIREMOCK = new WireMockServer(options().dynamicPort());
    static final ToxiproxyContainer TOXIPROXY =
            new ToxiproxyContainer("ghcr.io/shopify/toxiproxy:2.9.0");

    static Proxy communicationProxy;

    static {
        POSTGRES.start();
        WIREMOCK.start();
        Testcontainers.exposeHostPorts(WIREMOCK.port());
        TOXIPROXY.start();
        try {
            ToxiproxyClient client = new ToxiproxyClient(TOXIPROXY.getHost(), TOXIPROXY.getControlPort());
            communicationProxy = client.createProxy("communication", "0.0.0.0:8666",
                    "host.testcontainers.internal:" + WIREMOCK.port());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to set up Toxiproxy proxy", e);
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

        String communicationUri = "http://" + TOXIPROXY.getHost() + ":" + TOXIPROXY.getMappedPort(8666);
        registry.add("spring.cloud.discovery.client.simple.instances.communication-service[0].uri",
                () -> communicationUri);
        registry.add("spring.security.oauth2.client.provider.keycloak.token-uri",
                () -> "http://localhost:" + WIREMOCK.port() + "/token");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ForfaitRepository forfaitRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @BeforeEach
    void setUp() {
        WIREMOCK.resetAll();
        enrollmentRepository.deleteAll();
        forfaitRepository.deleteAll();
        WIREMOCK.stubFor(WireMock.post(urlPathEqualTo("/token")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"access_token\":\"t\",\"token_type\":\"Bearer\",\"expires_in\":300}")));
    }

    @AfterEach
    void clearToxics() throws IOException {
        for (var toxic : communicationProxy.toxics().getAll()) {
            toxic.remove();
        }
    }

    @Test
    void reminderDelivered_whenCommunicationHealthy() throws Exception {
        WIREMOCK.stubFor(WireMock.post(urlPathEqualTo("/api/notifications/payment"))
                .willReturn(aResponse().withStatus(200)));
        Enrollment enrollment = persistUnpaidEnrollment();

        mockMvc.perform(post("/api/payments/{id}/remind", enrollment.getId()).with(staff("owner")))
                .andExpect(status().isNoContent());

        WIREMOCK.verify(postRequestedFor(urlPathEqualTo("/api/notifications/payment")));
    }

    @Test
    void reminderDegradesTo502_whenCommunicationStalls_bounded() throws Exception {
        WIREMOCK.stubFor(WireMock.post(urlPathEqualTo("/api/notifications/payment"))
                .willReturn(aResponse().withStatus(200)));
        // Communication responds far slower than the read timeout.
        communicationProxy.toxics().latency("comm-latency", ToxicDirection.DOWNSTREAM, 6_000);
        Enrollment enrollment = persistUnpaidEnrollment();

        long startMs = System.currentTimeMillis();
        mockMvc.perform(post("/api/payments/{id}/remind", enrollment.getId()).with(staff("owner")))
                .andExpect(status().isBadGateway());   // 502: best-effort reported non-delivery
        long elapsed = System.currentTimeMillis() - startMs;

        assertThat(elapsed).isLessThan(READ_TIMEOUT_BUDGET_MS);   // bounded, not an infinite hang
    }

    // ---- helpers ----

    private Enrollment persistUnpaidEnrollment() {
        Forfait forfait = new Forfait();
        forfait.setName("Pack test");
        forfait.setDrivingHours(20);
        forfait.setCodeSessions(10);
        forfait.setPrice(new BigDecimal("1000.00"));
        forfait.setActive(true);
        Forfait savedForfait = forfaitRepository.saveAndFlush(forfait);

        Enrollment enrollment = new Enrollment();
        enrollment.setClientId(CLIENT);
        enrollment.setForfaitId(savedForfait.getId());
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        enrollment.setTotalPrice(new BigDecimal("1000.00"));
        enrollment.setAmountPaid(BigDecimal.ZERO);       // fully outstanding -> reminder is valid
        enrollment.setRemainingDrivingHours(20);
        enrollment.setRemainingCodeSessions(10);
        enrollment.setEnrolledAt(Instant.now());
        return enrollmentRepository.saveAndFlush(enrollment);
    }

    private static JwtRequestPostProcessor staff(String sub) {
        return jwt().jwt(jwt -> jwt.subject(sub)).authorities(new SimpleGrantedAuthority("ROLE_OWNER"));
    }
}
