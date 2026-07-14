package com.example.financeservice.it;

import com.example.financeservice.domain.Enrollment;
import com.example.financeservice.domain.EnrollmentStatus;
import com.example.financeservice.domain.Forfait;
import com.example.financeservice.repository.EnrollmentRepository;
import com.example.financeservice.repository.ForfaitRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Base for finance-service integration/chaos tests: real Postgres (Testcontainers + Flyway) and
 * MockMvc for inbound requests. Subclasses add the WireMock/Toxiproxy wiring for the downstreams
 * they exercise (auth-service, communication-service).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Testcontainers
abstract class AbstractFinanceIntegrationTest {

    @Container
    @org.springframework.boot.testcontainers.service.connection.ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected ForfaitRepository forfaitRepository;

    @Autowired
    protected EnrollmentRepository enrollmentRepository;

    /** Persists a forfait + an ACTIVE enrollment for the given client. */
    protected Enrollment persistActiveEnrollment(String clientId, BigDecimal totalPrice, BigDecimal amountPaid,
                                                 int drivingHours, int codeSessions) {
        Forfait forfait = new Forfait();
        forfait.setName("Pack test");
        forfait.setDrivingHours(drivingHours);
        forfait.setCodeSessions(codeSessions);
        forfait.setPrice(totalPrice);
        forfait.setActive(true);
        Forfait savedForfait = forfaitRepository.saveAndFlush(forfait);

        Enrollment enrollment = new Enrollment();
        enrollment.setClientId(clientId);
        enrollment.setForfaitId(savedForfait.getId());
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        enrollment.setTotalPrice(totalPrice);
        enrollment.setAmountPaid(amountPaid);
        enrollment.setRemainingDrivingHours(drivingHours);
        enrollment.setRemainingCodeSessions(codeSessions);
        enrollment.setEnrolledAt(Instant.now());
        return enrollmentRepository.saveAndFlush(enrollment);
    }

    protected static JwtRequestPostProcessor staff(String sub) {
        return jwt().jwt(jwt -> jwt.subject(sub)).authorities(new SimpleGrantedAuthority("ROLE_OWNER"));
    }
}
