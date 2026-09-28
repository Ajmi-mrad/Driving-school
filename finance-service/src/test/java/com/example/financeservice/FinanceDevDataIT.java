package com.example.financeservice;

import com.example.financeservice.dev.DevDataService;
import com.example.financeservice.dev.dto.FinanceSeedRequest;
import com.example.financeservice.dev.dto.SeedResult;
import com.example.financeservice.domain.Payment;
import com.example.financeservice.repository.AuditEventRepository;
import com.example.financeservice.repository.EnrollmentRepository;
import com.example.financeservice.repository.ForfaitRepository;
import com.example.financeservice.repository.InvoiceRepository;
import com.example.financeservice.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests d'intégration de l'outil de dev finance (seed / reset) sur une vraie base Postgres.
 * Le seed est appelé directement au niveau service ; la sécurité est couverte côté vehicle.
 */
@SpringBootTest(properties = "app.dev-tools.enabled=true")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FinanceDevDataIT {

    @Autowired
    private DevDataService devDataService;
    @Autowired
    private ForfaitRepository forfaitRepository;
    @Autowired
    private EnrollmentRepository enrollmentRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final String CLI1 = UUID.randomUUID().toString();
    private static final String CLI2 = UUID.randomUUID().toString();
    private static final String CLI3 = UUID.randomUUID().toString();

    private static FinanceSeedRequest fullRequest() {
        Map<String, String> users = new LinkedHashMap<>();
        users.put("u-cli-1", CLI1);
        users.put("u-cli-2", CLI2);
        users.put("u-cli-3", CLI3);
        return new FinanceSeedRequest(users);
    }

    @BeforeEach
    void clean() {
        devDataService.reset();
    }

    @Test
    void seed_populatesAllEntities_withClientIdsFromMap() {
        SeedResult result = devDataService.seed(fullRequest());

        assertThat(result.created()).isEqualTo(15);
        assertThat(result.skipped()).isZero();
        assertThat(forfaitRepository.count()).isEqualTo(4);
        assertThat(enrollmentRepository.count()).isEqualTo(3);
        assertThat(paymentRepository.count()).isEqualTo(4);
        assertThat(invoiceRepository.count()).isEqualTo(4);
        assertThat(enrollmentRepository.findAll())
                .extracting(e -> e.getClientId())
                .containsExactlyInAnyOrder(CLI1, CLI2, CLI3);
    }

    @Test
    void seed_isIdempotent_whenDataAlreadyExists() {
        devDataService.seed(fullRequest());
        SeedResult second = devDataService.seed(fullRequest());

        assertThat(second.created()).isZero();
        assertThat(second.skipped()).isEqualTo(15);
        assertThat(forfaitRepository.count()).isEqualTo(4); // not doubled
    }

    @Test
    void seed_missingUserKey_throwsAndRollsBack() {
        Map<String, String> partial = new LinkedHashMap<>();
        partial.put("u-cli-1", CLI1);
        partial.put("u-cli-2", CLI2); // u-cli-3 deliberately absent

        assertThatThrownBy(() -> devDataService.seed(new FinanceSeedRequest(partial)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(forfaitRepository.count()).isZero(); // whole @Transactional rolled back
        assertThat(enrollmentRepository.count()).isZero();
    }

    @Test
    void reset_truncatesEverything_andResetsInvoiceSequence() {
        devDataService.seed(fullRequest());
        jdbcTemplate.queryForObject("SELECT nextval('invoice_number_seq')", Long.class); // advance it

        devDataService.reset();

        assertThat(forfaitRepository.count()).isZero();
        assertThat(enrollmentRepository.count()).isZero();
        assertThat(paymentRepository.count()).isZero();
        assertThat(invoiceRepository.count()).isZero();
        assertThat(auditEventRepository.count()).isZero();
        Long next = jdbcTemplate.queryForObject("SELECT nextval('invoice_number_seq')", Long.class);
        assertThat(next).isEqualTo(1L); // numbering restarted
    }

    @Test
    void seedTimestamps_trackWallClock_notAStaticConstant() throws InterruptedException {
        devDataService.seed(fullRequest());
        Instant firstMax = latestPaidAt();

        devDataService.reset();
        Thread.sleep(1100);
        devDataService.seed(fullRequest());
        Instant secondMax = latestPaidAt();

        // Regression guard: a static class-load NOW would anchor both seeds identically.
        assertThat(secondMax).isAfter(firstMax);
        // And the anchor is real wall-clock: the most recent payment is ~10 days ago.
        Instant tenDaysAgo = Instant.now().minus(Duration.ofDays(10));
        assertThat(secondMax).isBetween(tenDaysAgo.minusSeconds(120), tenDaysAgo.plusSeconds(120));
    }

    private Instant latestPaidAt() {
        return paymentRepository.findAll().stream()
                .map(Payment::getPaidAt).max(Instant::compareTo).orElseThrow();
    }
}
