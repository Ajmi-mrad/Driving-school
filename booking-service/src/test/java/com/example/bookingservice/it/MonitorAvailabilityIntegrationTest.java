package com.example.bookingservice.it;

import com.example.bookingservice.domain.MonitorAvailability;
import com.example.bookingservice.domain.Session;
import com.example.bookingservice.domain.SessionStatus;
import com.example.bookingservice.domain.SessionType;
import com.example.bookingservice.repository.MonitorAvailabilityRepository;
import com.example.bookingservice.repository.MonitorTimeOffRepository;
import com.example.bookingservice.repository.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flow bout-en-bout (dans les limites du service) de la disponibilité des moniteurs : une vraie
 * requête HTTP traverse le contrôleur et le service réel (validation du moniteur via WireMock), et
 * persiste dans un vrai Postgres. Vérifie le calcul des créneaux (déduction des séances/absences) et
 * le contrôle d'accès (staff/moniteur concerné uniquement).
 */
class MonitorAvailabilityIntegrationTest extends AbstractBookingIntegrationTest {

    private static final String MONITOR = "monitor-avail-1";
    private static final String STAFF_SUB = "owner-000";

    // Lundi 2026-01-05 ; heures locales Africa/Tunis (UTC+1) → 09:00 local = 08:00Z.
    private static final Instant DAY_FROM = Instant.parse("2026-01-05T00:00:00Z");
    private static final Instant DAY_TO = Instant.parse("2026-01-06T00:00:00Z");

    @Autowired
    private MonitorAvailabilityRepository availabilityRepository;
    @Autowired
    private MonitorTimeOffRepository timeOffRepository;
    @Autowired
    private SessionRepository sessionRepository;

    @BeforeEach
    void cleanDb() {
        sessionRepository.deleteAll();
        availabilityRepository.deleteAll();
        timeOffRepository.deleteAll();
        stubUser(MONITOR, "MONITOR", true);
    }

    @Test
    void putAvailability_thenFreeSlots_excludeBookedSessions() throws Exception {
        String rules = """
                {"rules":[{"dayOfWeek":"MONDAY","startTime":"09:00:00","endTime":"12:00:00"}]}
                """;
        mockMvc.perform(put("/api/monitors/{id}/availability", MONITOR).with(staff(STAFF_SUB))
                        .contentType(MediaType.APPLICATION_JSON).content(rules))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dayOfWeek").value("MONDAY"));

        // Séance réservée 10:00–11:00 local (09:00Z–10:00Z) pour ce moniteur.
        persistSession(Instant.parse("2026-01-05T09:00:00Z"), Instant.parse("2026-01-05T10:00:00Z"));

        // Créneaux d'une heure : 09:00–10:00 et 11:00–12:00 local (la séance de 10:00 exclut sa tranche).
        mockMvc.perform(get("/api/monitors/{id}/free-slots", MONITOR)
                        .param("from", DAY_FROM.toString())
                        .param("to", DAY_TO.toString())
                        .param("slotMinutes", "60")
                        .with(client("some-client")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].startTime").value("2026-01-05T08:00:00Z"))
                .andExpect(jsonPath("$[1].startTime").value("2026-01-05T10:00:00Z"));
    }

    @Test
    void freeSlots_multiDay_doesNotLeakBusyAcrossDays() throws Exception {
        // Régression : un créneau réservé le mardi ne doit pas corrompre les créneaux du lundi.
        persistRule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0));
        persistRule(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(12, 0));
        // Séance mardi 10:00–11:00 local (2026-01-06, 09:00Z–10:00Z).
        persistSession(Instant.parse("2026-01-06T09:00:00Z"), Instant.parse("2026-01-06T10:00:00Z"));

        // Fenêtre lundi 00:00Z → mercredi 00:00Z.
        mockMvc.perform(get("/api/monitors/{id}/free-slots", MONITOR)
                        .param("from", DAY_FROM.toString())
                        .param("to", "2026-01-07T00:00:00Z")
                        .param("slotMinutes", "60")
                        .with(staff(STAFF_SUB)))
                .andExpect(status().isOk())
                // Lundi 3 créneaux intacts + mardi 2 (10–11 réservé) = 5. Aucun créneau hors 09–12.
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].startTime").value("2026-01-05T08:00:00Z")) // lundi 09:00 local
                .andExpect(jsonPath("$[2].startTime").value("2026-01-05T10:00:00Z")) // lundi 11:00 local
                .andExpect(jsonPath("$[3].startTime").value("2026-01-06T08:00:00Z")) // mardi 09:00 local
                .andExpect(jsonPath("$[4].startTime").value("2026-01-06T10:00:00Z")); // mardi 11:00 local
    }

    @Test
    void freeSlots_excludeTimeOff() throws Exception {
        String rules = """
                {"rules":[{"dayOfWeek":"MONDAY","startTime":"09:00:00","endTime":"12:00:00"}]}
                """;
        mockMvc.perform(put("/api/monitors/{id}/availability", MONITOR).with(staff(STAFF_SUB))
                        .contentType(MediaType.APPLICATION_JSON).content(rules))
                .andExpect(status().isOk());

        // Absence 09:00–11:00 local (08:00Z–10:00Z) → seule la tranche 11:00–12:00 reste.
        String timeOff = """
                {"startTime":"2026-01-05T08:00:00Z","endTime":"2026-01-05T10:00:00Z","reason":"Congé"}
                """;
        mockMvc.perform(post("/api/monitors/{id}/time-off", MONITOR).with(staff(STAFF_SUB))
                        .contentType(MediaType.APPLICATION_JSON).content(timeOff))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/monitors/{id}/free-slots", MONITOR)
                        .param("from", DAY_FROM.toString())
                        .param("to", DAY_TO.toString())
                        .with(staff(STAFF_SUB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].startTime").value("2026-01-05T10:00:00Z"));
    }

    @Test
    void monitorCanManageOwnAvailability() throws Exception {
        String rules = """
                {"rules":[{"dayOfWeek":"TUESDAY","startTime":"14:00:00","endTime":"18:00:00"}]}
                """;
        mockMvc.perform(put("/api/monitors/{id}/availability", MONITOR).with(monitor(MONITOR))
                        .contentType(MediaType.APPLICATION_JSON).content(rules))
                .andExpect(status().isOk());
        assertThat(availabilityRepository.findByMonitorIdOrderByDayOfWeekAscStartTimeAsc(MONITOR)).hasSize(1);
    }

    @Test
    void monitorCannotManageAnotherMonitor() throws Exception {
        String rules = """
                {"rules":[{"dayOfWeek":"MONDAY","startTime":"09:00:00","endTime":"12:00:00"}]}
                """;
        mockMvc.perform(put("/api/monitors/{id}/availability", MONITOR).with(monitor("monitor-other"))
                        .contentType(MediaType.APPLICATION_JSON).content(rules))
                .andExpect(status().isForbidden());
    }

    @Test
    void clientCannotManageAvailability() throws Exception {
        String rules = """
                {"rules":[{"dayOfWeek":"MONDAY","startTime":"09:00:00","endTime":"12:00:00"}]}
                """;
        mockMvc.perform(put("/api/monitors/{id}/availability", MONITOR).with(client("client-1"))
                        .contentType(MediaType.APPLICATION_JSON).content(rules))
                .andExpect(status().isForbidden());
    }

    @Test
    void booking_rejectedOutsideDeclaredHours_allowedWithin() throws Exception {
        // Le moniteur ne travaille que le lundi 09:00–12:00 (heure locale).
        persistRule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0));
        stubUser("client-x", "CLIENT", true);
        stubAvailableVehicles(UUID.randomUUID().toString());

        // Lundi 13:00–14:00 local (12:00Z–13:00Z) → hors horaires → 409.
        mockMvc.perform(post("/api/sessions").with(staff(STAFF_SUB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionBody("2026-01-05T12:00:00Z", "2026-01-05T13:00:00Z")))
                .andExpect(status().isConflict());

        // Lundi 10:00–11:00 local (09:00Z–10:00Z) → dans les horaires → 201.
        mockMvc.perform(post("/api/sessions").with(staff(STAFF_SUB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionBody("2026-01-05T09:00:00Z", "2026-01-05T10:00:00Z")))
                .andExpect(status().isCreated());
    }

    @Test
    void confirm_rejectedWhenMonitorBecomesUnavailableAfterBooking() throws Exception {
        persistRule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0));
        stubUser("client-x", "CLIENT", true);
        stubAvailableVehicles(UUID.randomUUID().toString());

        // Séance lundi 10:00–11:00 local (09:00Z–10:00Z) créée dans les horaires → PENDING.
        mockMvc.perform(post("/api/sessions").with(staff(STAFF_SUB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionBody("2026-01-05T09:00:00Z", "2026-01-05T10:00:00Z")))
                .andExpect(status().isCreated());

        // Une absence est déclarée après coup, couvrant la séance.
        mockMvc.perform(post("/api/monitors/{id}/time-off", MONITOR).with(staff(STAFF_SUB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startTime":"2026-01-05T09:00:00Z","endTime":"2026-01-05T10:00:00Z","reason":"Congé"}
                                """))
                .andExpect(status().isCreated());

        // La confirmation doit être refusée : le moniteur n'est plus disponible sur le créneau.
        String sessionId = sessionRepository.findAll().get(0).getId().toString();
        mockMvc.perform(patch("/api/sessions/{id}/confirm", sessionId).with(staff(STAFF_SUB)))
                .andExpect(status().isConflict());
    }

    @Test
    void timeOffAndAvailability_readableOnlyByStaffOrTheMonitor() throws Exception {
        persistRule(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0));

        // Un élève ne peut lire ni les horaires ni les absences (motif sensible).
        mockMvc.perform(get("/api/monitors/{id}/time-off", MONITOR).with(client("client-1")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/monitors/{id}/availability", MONITOR).with(client("client-1")))
                .andExpect(status().isForbidden());

        // Un autre moniteur non plus.
        mockMvc.perform(get("/api/monitors/{id}/time-off", MONITOR).with(monitor("monitor-other")))
                .andExpect(status().isForbidden());

        // Le staff et le moniteur concerné, oui.
        mockMvc.perform(get("/api/monitors/{id}/time-off", MONITOR).with(staff(STAFF_SUB)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/monitors/{id}/availability", MONITOR).with(monitor(MONITOR)))
                .andExpect(status().isOk());

        // Les créneaux calculés restent ouverts à l'élève (nécessaires pour réserver, sans motif exposé).
        mockMvc.perform(get("/api/monitors/{id}/free-slots", MONITOR)
                        .param("from", DAY_FROM.toString())
                        .param("to", DAY_TO.toString())
                        .with(client("client-1")))
                .andExpect(status().isOk());
    }

    // ---- helpers ----

    private String sessionBody(String start, String end) {
        return """
                {"type":"DRIVING","clientId":"client-x","monitorId":"%s","startTime":"%s","endTime":"%s"}
                """.formatted(MONITOR, start, end);
    }

    private void persistRule(DayOfWeek day, LocalTime start, LocalTime end) {
        MonitorAvailability rule = new MonitorAvailability();
        rule.setMonitorId(MONITOR);
        rule.setDayOfWeek(day);
        rule.setStartTime(start);
        rule.setEndTime(end);
        availabilityRepository.save(rule);
    }

    private static JwtRequestPostProcessor monitor(String sub) {
        return jwt().jwt(jwt -> jwt.subject(sub)).authorities(new SimpleGrantedAuthority("ROLE_MONITOR"));
    }

    private void persistSession(Instant start, Instant end) {
        Session session = new Session();
        session.setType(SessionType.DRIVING);
        session.setClientId("client-booked");
        session.setMonitorId(MONITOR);
        session.setStartTime(start);
        session.setEndTime(end);
        session.setStatus(SessionStatus.CONFIRMED);
        sessionRepository.save(session);
    }
}