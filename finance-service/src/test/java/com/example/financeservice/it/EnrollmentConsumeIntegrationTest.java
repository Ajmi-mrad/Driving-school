package com.example.financeservice.it;

import com.example.financeservice.domain.Enrollment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the consumption endpoint the booking-service calls when a session completes:
 * it deducts driving hours / code sessions from the client's active enrollment, floored at zero.
 */
class EnrollmentConsumeIntegrationTest extends AbstractFinanceIntegrationTest {

    private static final String CLIENT = "client-consume";

    @BeforeEach
    void cleanDb() {
        enrollmentRepository.deleteAll();
        forfaitRepository.deleteAll();
    }

    @Test
    void consume_deductsHoursFromActiveEnrollment() throws Exception {
        Enrollment enrollment = persistActiveEnrollment(CLIENT, new BigDecimal("1000.00"), BigDecimal.ZERO, 20, 10);

        mockMvc.perform(post("/api/enrollments/consume").with(staff("owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"%s\",\"drivingHours\":2,\"codeSessions\":1}".formatted(CLIENT)))
                .andExpect(status().isNoContent());

        Enrollment updated = enrollmentRepository.findById(enrollment.getId()).orElseThrow();
        assertThat(updated.getRemainingDrivingHours()).isEqualTo(18);
        assertThat(updated.getRemainingCodeSessions()).isEqualTo(9);
    }

    @Test
    void consume_neverGoesBelowZero() throws Exception {
        Enrollment enrollment = persistActiveEnrollment(CLIENT, new BigDecimal("500.00"), BigDecimal.ZERO, 3, 1);

        mockMvc.perform(post("/api/enrollments/consume").with(staff("owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"%s\",\"drivingHours\":99,\"codeSessions\":99}".formatted(CLIENT)))
                .andExpect(status().isNoContent());

        Enrollment updated = enrollmentRepository.findById(enrollment.getId()).orElseThrow();
        assertThat(updated.getRemainingDrivingHours()).isZero();
        assertThat(updated.getRemainingCodeSessions()).isZero();
    }
}
