package com.example.bookingservice.it;

import com.example.bookingservice.domain.Exam;
import com.example.bookingservice.domain.ExamStatus;
import com.example.bookingservice.domain.ExamType;
import com.example.bookingservice.repository.ExamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end (within the service boundary) exam flow: a real HTTP request drives the real
 * ExamService, which validates the student against a WireMock-backed auth-service, persists to a
 * real Postgres, and emits EXAM_* notifications to the WireMock-backed communication-service.
 */
class ExamFlowIntegrationTest extends AbstractBookingIntegrationTest {

    private static final String CLIENT_SUB = "client-123";
    private static final String STAFF_SUB = "owner-000";

    @Autowired
    private ExamRepository examRepository;

    @BeforeEach
    void cleanDb() {
        examRepository.deleteAll();
    }

    @Test
    void scheduleExam_computesAttemptOne_andNotifiesClient() throws Exception {
        stubUser(CLIENT_SUB, "CLIENT", true);

        Instant when = Instant.now().plus(5, ChronoUnit.DAYS);
        String body = """
                {"type":"CODE","clientId":"%s","scheduledAt":"%s","location":"Centre Tunis"}
                """.formatted(CLIENT_SUB, when);

        mockMvc.perform(post("/api/exams").with(staff(STAFF_SUB))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.attemptNumber").value(1))
                .andExpect(jsonPath("$.clientId").value(CLIENT_SUB));

        assertThat(examRepository.count()).isEqualTo(1);
        WIREMOCK.verify(postRequestedFor(urlPathEqualTo("/api/notifications/booking"))
                .withRequestBody(matchingJsonPath("$.type", equalTo("EXAM_SCHEDULED")))
                .withRequestBody(matchingJsonPath("$.recipientId", equalTo(CLIENT_SUB))));
    }

    @Test
    void scheduleSecondExam_afterPriorAttempt_incrementsAttemptNumber() throws Exception {
        stubUser(CLIENT_SUB, "CLIENT", true);
        persistExam(ExamType.CODE, ExamStatus.FAILED, 1, Instant.now().minus(20, ChronoUnit.DAYS));

        Instant when = Instant.now().plus(5, ChronoUnit.DAYS);
        String body = """
                {"type":"CODE","clientId":"%s","scheduledAt":"%s"}
                """.formatted(CLIENT_SUB, when);

        mockMvc.perform(post("/api/exams").with(staff(STAFF_SUB))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attemptNumber").value(2));
    }

    @Test
    void scheduleExam_rejectedWhenAlreadyScheduledOfSameType() throws Exception {
        stubUser(CLIENT_SUB, "CLIENT", true);
        persistExam(ExamType.CODE, ExamStatus.SCHEDULED, 1, Instant.now().plus(3, ChronoUnit.DAYS));

        Instant when = Instant.now().plus(6, ChronoUnit.DAYS);
        String body = """
                {"type":"CODE","clientId":"%s","scheduledAt":"%s"}
                """.formatted(CLIENT_SUB, when);

        mockMvc.perform(post("/api/exams").with(staff(STAFF_SUB))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void recordResult_passed_notifiesClient() throws Exception {
        Exam exam = persistExam(ExamType.DRIVING, ExamStatus.SCHEDULED, 1,
                Instant.now().plus(1, ChronoUnit.DAYS));

        mockMvc.perform(patch("/api/exams/{id}/result", exam.getId()).with(staff(STAFF_SUB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"PASSED\",\"note\":\"Bravo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PASSED"));

        WIREMOCK.verify(postRequestedFor(urlPathEqualTo("/api/notifications/booking"))
                .withRequestBody(matchingJsonPath("$.type", equalTo("EXAM_PASSED")))
                .withRequestBody(matchingJsonPath("$.recipientId", equalTo(CLIENT_SUB))));
    }

    @Test
    void recordResult_rejectedWhenExamNotScheduled() throws Exception {
        Exam exam = persistExam(ExamType.DRIVING, ExamStatus.PASSED, 1,
                Instant.now().minus(1, ChronoUnit.DAYS));

        mockMvc.perform(patch("/api/exams/{id}/result", exam.getId()).with(staff(STAFF_SUB))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"FAILED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void recordResult_noShow_notifiesClientExamFailed() throws Exception {
        Exam exam = persistExam(ExamType.CODE, ExamStatus.SCHEDULED, 1,
                Instant.now().plus(1, ChronoUnit.DAYS));

        mockMvc.perform(patch("/api/exams/{id}/result", exam.getId()).with(staff(STAFF_SUB))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"NO_SHOW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SHOW"));

        WIREMOCK.verify(postRequestedFor(urlPathEqualTo("/api/notifications/booking"))
                .withRequestBody(matchingJsonPath("$.type", equalTo("EXAM_FAILED")))
                .withRequestBody(matchingJsonPath("$.recipientId", equalTo(CLIENT_SUB))));
    }

    @Test
    void rescheduleExam_notifiesClient() throws Exception {
        Exam exam = persistExam(ExamType.DRIVING, ExamStatus.SCHEDULED, 1,
                Instant.now().plus(2, ChronoUnit.DAYS));
        Instant newWhen = Instant.now().plus(9, ChronoUnit.DAYS);

        mockMvc.perform(patch("/api/exams/{id}/reschedule", exam.getId()).with(staff(STAFF_SUB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledAt\":\"" + newWhen + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"));

        WIREMOCK.verify(postRequestedFor(urlPathEqualTo("/api/notifications/booking"))
                .withRequestBody(matchingJsonPath("$.type", equalTo("EXAM_RESCHEDULED")))
                .withRequestBody(matchingJsonPath("$.recipientId", equalTo(CLIENT_SUB))));
    }

    @Test
    void cancelExam_notifiesClient() throws Exception {
        Exam exam = persistExam(ExamType.DRIVING, ExamStatus.SCHEDULED, 1,
                Instant.now().plus(2, ChronoUnit.DAYS));

        mockMvc.perform(patch("/api/exams/{id}/cancel", exam.getId()).with(staff(STAFF_SUB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        WIREMOCK.verify(postRequestedFor(urlPathEqualTo("/api/notifications/booking"))
                .withRequestBody(matchingJsonPath("$.type", equalTo("EXAM_CANCELLED")))
                .withRequestBody(matchingJsonPath("$.recipientId", equalTo(CLIENT_SUB))));
    }

    @Test
    void listExams_asClient_returnsOnlyOwn() throws Exception {
        persistExam(ExamType.CODE, ExamStatus.SCHEDULED, 1, Instant.now().plus(1, ChronoUnit.DAYS));
        persistExamForClient("other-client", ExamType.DRIVING, ExamStatus.SCHEDULED,
                Instant.now().plus(2, ChronoUnit.DAYS));

        mockMvc.perform(get("/api/exams").with(client(CLIENT_SUB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].clientId").value(CLIENT_SUB));
    }

    @Test
    void scheduleExam_unknownClient_returnsBadRequest() throws Exception {
        stubUserNotFound(CLIENT_SUB);
        Instant when = Instant.now().plus(5, ChronoUnit.DAYS);
        String body = """
                {"type":"CODE","clientId":"%s","scheduledAt":"%s"}
                """.formatted(CLIENT_SUB, when);

        mockMvc.perform(post("/api/exams").with(staff(STAFF_SUB))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void scheduleExam_afterCancelledAttempt_keepsAttemptNumberOne() throws Exception {
        stubUser(CLIENT_SUB, "CLIENT", true);
        // A cancelled exam was never sat and must not count towards the attempt number.
        persistExam(ExamType.CODE, ExamStatus.CANCELLED, 1, Instant.now().minus(10, ChronoUnit.DAYS));

        Instant when = Instant.now().plus(5, ChronoUnit.DAYS);
        String body = """
                {"type":"CODE","clientId":"%s","scheduledAt":"%s"}
                """.formatted(CLIENT_SUB, when);

        mockMvc.perform(post("/api/exams").with(staff(STAFF_SUB))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attemptNumber").value(1));
    }

    @Test
    void scheduleExam_pastDate_returnsBadRequest() throws Exception {
        stubUser(CLIENT_SUB, "CLIENT", true);
        Instant when = Instant.now().minus(1, ChronoUnit.DAYS);
        String body = """
                {"type":"CODE","clientId":"%s","scheduledAt":"%s"}
                """.formatted(CLIENT_SUB, when);

        mockMvc.perform(post("/api/exams").with(staff(STAFF_SUB))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void scheduleCodeExam_withVehicle_returnsBadRequest() throws Exception {
        stubUser(CLIENT_SUB, "CLIENT", true);
        Instant when = Instant.now().plus(5, ChronoUnit.DAYS);
        String body = """
                {"type":"CODE","clientId":"%s","vehicleId":"%s","scheduledAt":"%s"}
                """.formatted(CLIENT_SUB, java.util.UUID.randomUUID(), when);

        mockMvc.perform(post("/api/exams").with(staff(STAFF_SUB))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clientCannotScheduleExam() throws Exception {
        Instant when = Instant.now().plus(5, ChronoUnit.DAYS);
        String body = """
                {"type":"CODE","clientId":"%s","scheduledAt":"%s"}
                """.formatted(CLIENT_SUB, when);

        mockMvc.perform(post("/api/exams").with(client(CLIENT_SUB))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    private Exam persistExam(ExamType type, ExamStatus status, int attempt, Instant scheduledAt) {
        return persistExamForClient(CLIENT_SUB, type, status, scheduledAt, attempt);
    }

    private Exam persistExamForClient(String clientId, ExamType type, ExamStatus status, Instant scheduledAt) {
        return persistExamForClient(clientId, type, status, scheduledAt, 1);
    }

    private Exam persistExamForClient(String clientId, ExamType type, ExamStatus status,
                                      Instant scheduledAt, int attempt) {
        Exam exam = new Exam();
        exam.setType(type);
        exam.setClientId(clientId);
        exam.setScheduledAt(scheduledAt);
        exam.setStatus(status);
        exam.setAttemptNumber(attempt);
        return examRepository.saveAndFlush(exam);
    }
}
