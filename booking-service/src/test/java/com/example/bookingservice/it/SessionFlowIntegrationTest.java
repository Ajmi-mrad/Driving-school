package com.example.bookingservice.it;

import com.example.bookingservice.domain.Session;
import com.example.bookingservice.domain.SessionStatus;
import com.example.bookingservice.domain.SessionType;
import com.example.bookingservice.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end (within the service boundary) flow: a real HTTP request into the controller drives
 * the real SessionService, which makes real load-balanced RestClient calls to WireMock-backed
 * auth/vehicle/finance downstreams, and persists to a real Postgres.
 */
class SessionFlowIntegrationTest extends AbstractBookingIntegrationTest {

    private static final String CLIENT_SUB = "client-123";
    private static final String MONITOR_SUB = "monitor-456";
    private static final String STAFF_SUB = "owner-000";

    @Autowired
    private SessionRepository sessionRepository;

    @BeforeEach
    void cleanDb() {
        sessionRepository.deleteAll();
    }

    @Test
    void createDrivingSession_validatesAcrossServices_andAutoAssignsVehicle() throws Exception {
        String vehicleId = UUID.randomUUID().toString();
        stubUser(CLIENT_SUB, "CLIENT", true);
        stubUser(MONITOR_SUB, "MONITOR", true);
        stubAvailableVehicles(vehicleId);

        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant end = start.plus(90, ChronoUnit.MINUTES);
        String body = """
                {"type":"DRIVING","clientId":"%s","monitorId":"%s","startTime":"%s","endTime":"%s"}
                """.formatted(CLIENT_SUB, MONITOR_SUB, start, end);

        mockMvc.perform(post("/api/sessions").with(staff(STAFF_SUB))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))          // auto-validation off by default
                .andExpect(jsonPath("$.clientId").value(CLIENT_SUB))
                .andExpect(jsonPath("$.vehicleId").value(vehicleId));      // auto-assigned from available

        assertThat(sessionRepository.count()).isEqualTo(1);
    }

    @Test
    void createSession_rejectedWhenClientHasOverlap() throws Exception {
        stubUser(CLIENT_SUB, "CLIENT", true);
        stubUser(MONITOR_SUB, "MONITOR", true);
        stubAvailableVehicles(UUID.randomUUID().toString());

        Instant start = Instant.now().plus(3, ChronoUnit.DAYS);
        Instant end = start.plus(60, ChronoUnit.MINUTES);
        // Pre-existing active session for the same client on an overlapping slot.
        persistSession(SessionStatus.CONFIRMED, CLIENT_SUB, MONITOR_SUB, start.plus(15, ChronoUnit.MINUTES),
                end.plus(15, ChronoUnit.MINUTES), null);

        String body = """
                {"type":"DRIVING","clientId":"%s","monitorId":"%s","startTime":"%s","endTime":"%s"}
                """.formatted(CLIENT_SUB, MONITOR_SUB, start, end);

        mockMvc.perform(post("/api/sessions").with(staff(STAFF_SUB))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void completeSession_reportsConsumptionToFinance() throws Exception {
        stubConsumeOk();
        // A confirmed driving session that has already ended is eligible for completion.
        Instant end = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant start = end.minus(90, ChronoUnit.MINUTES);
        Session session = persistSession(SessionStatus.CONFIRMED, CLIENT_SUB, MONITOR_SUB, start, end,
                UUID.randomUUID());

        mockMvc.perform(patch("/api/sessions/{id}/complete", session.getId()).with(staff(STAFF_SUB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Consumption reporting fires after commit (2h driving derived from 90 min -> ceil = 2).
        WIREMOCK.verify(postRequestedFor(urlPathEqualTo("/api/enrollments/consume")));
    }

    private Session persistSession(SessionStatus status, String clientId, String monitorId,
                                   Instant start, Instant end, UUID vehicleId) {
        Session session = new Session();
        session.setType(SessionType.DRIVING);
        session.setClientId(clientId);
        session.setMonitorId(monitorId);
        session.setVehicleId(vehicleId);
        session.setStartTime(start);
        session.setEndTime(end);
        session.setStatus(status);
        return sessionRepository.saveAndFlush(session);
    }
}