package com.example.bookingservice.service;

import com.example.bookingservice.client.UserClient;
import com.example.bookingservice.client.dto.UserInfo;
import com.example.bookingservice.domain.MonitorAvailability;
import com.example.bookingservice.domain.MonitorTimeOff;
import com.example.bookingservice.domain.Session;
import com.example.bookingservice.domain.SessionStatus;
import com.example.bookingservice.exception.CrossServiceValidationException;
import com.example.bookingservice.exception.TimeOffNotFoundException;
import com.example.bookingservice.mapper.MonitorMapper;
import com.example.bookingservice.repository.MonitorAvailabilityRepository;
import com.example.bookingservice.repository.MonitorTimeOffRepository;
import com.example.bookingservice.repository.SessionRepository;
import com.example.bookingservice.web.dto.AvailabilityRuleRequest;
import com.example.bookingservice.web.dto.CreateTimeOffRequest;
import com.example.bookingservice.web.dto.FreeSlotResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires (Mockito) de la disponibilité des moniteurs. L'essentiel : le calcul des créneaux
 * réservables ({@code computeFreeSlots}) — heures de travail moins séances réservées moins absences,
 * découpé en tranches qui tiennent entièrement — ainsi que les garde-fous de validation et d'accès.
 */
@ExtendWith(MockitoExtension.class)
class MonitorAvailabilityServiceTest {

    private static final String MONITOR = "monitor-1";
    private static final ZoneId ZONE = ZoneId.of("Africa/Tunis");
    /** Un lundi concret, pour aligner règle hebdomadaire et intervalle demandé. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 1, 5);
    private static final LocalDate TUESDAY = MONDAY.plusDays(1);

    @Mock
    private MonitorAvailabilityRepository availabilityRepository;
    @Mock
    private MonitorTimeOffRepository timeOffRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private MonitorMapper monitorMapper;
    @Mock
    private UserClient userClient;

    @InjectMocks
    private MonitorAvailabilityService service;

    // ---- computeFreeSlots ----

    @Test
    void computeFreeSlots_subtractsBookedSessionsAndTimeOff_andKeepsOnlyWholeSlots() {
        Instant from = at(MONDAY, 0, 0);
        Instant to = at(MONDAY.plusDays(1), 0, 0);

        // Lundi 09:00–12:00 déclaré ; séance 10:00–11:00 ; absence 09:00–09:30.
        when(availabilityRepository.findByMonitorIdOrderByDayOfWeekAscStartTimeAsc(MONITOR))
                .thenReturn(List.of(rule(DayOfWeek.MONDAY, 9, 0, 12, 0)));
        when(sessionRepository.findMonitorSessionsInRange(eq(MONITOR), any(), any(), any()))
                .thenReturn(List.of(session(at(MONDAY, 10, 0), at(MONDAY, 11, 0))));
        when(timeOffRepository.findByMonitorIdAndStartTimeLessThanAndEndTimeGreaterThan(eq(MONITOR), any(), any()))
                .thenReturn(List.of(timeOff(at(MONDAY, 9, 0), at(MONDAY, 9, 30))));

        List<FreeSlotResponse> slots = service.computeFreeSlots(MONITOR, from, to, 60);

        // Restant : [09:30,10:00) (30 min, écarté) et [11:00,12:00) (une tranche de 60 min).
        assertThat(slots).containsExactly(new FreeSlotResponse(at(MONDAY, 11, 0), at(MONDAY, 12, 0)));
    }

    @Test
    void computeFreeSlots_slicesWindowIntoConsecutiveSlots_whenNothingBooked() {
        Instant from = at(MONDAY, 0, 0);
        Instant to = at(MONDAY.plusDays(1), 0, 0);
        when(availabilityRepository.findByMonitorIdOrderByDayOfWeekAscStartTimeAsc(MONITOR))
                .thenReturn(List.of(rule(DayOfWeek.MONDAY, 9, 0, 12, 0)));
        when(sessionRepository.findMonitorSessionsInRange(eq(MONITOR), any(), any(), any()))
                .thenReturn(List.of());
        when(timeOffRepository.findByMonitorIdAndStartTimeLessThanAndEndTimeGreaterThan(eq(MONITOR), any(), any()))
                .thenReturn(List.of());

        List<FreeSlotResponse> slots = service.computeFreeSlots(MONITOR, from, to, 60);

        assertThat(slots).containsExactly(
                new FreeSlotResponse(at(MONDAY, 9, 0), at(MONDAY, 10, 0)),
                new FreeSlotResponse(at(MONDAY, 10, 0), at(MONDAY, 11, 0)),
                new FreeSlotResponse(at(MONDAY, 11, 0), at(MONDAY, 12, 0)));
    }

    @Test
    void computeFreeSlots_multiDay_busyOnLaterDayDoesNotLeakIntoEarlierWindow() {
        // Régression : une séance sur mardi ne doit pas étirer/corrompre les créneaux du lundi.
        Instant from = at(MONDAY, 0, 0);
        Instant to = at(MONDAY.plusDays(2), 0, 0); // jusqu'à mercredi 00:00
        when(availabilityRepository.findByMonitorIdOrderByDayOfWeekAscStartTimeAsc(MONITOR))
                .thenReturn(List.of(rule(DayOfWeek.MONDAY, 9, 0, 12, 0), rule(DayOfWeek.TUESDAY, 9, 0, 12, 0)));
        when(sessionRepository.findMonitorSessionsInRange(eq(MONITOR), any(), any(), any()))
                .thenReturn(List.of(session(at(TUESDAY, 10, 0), at(TUESDAY, 11, 0))));
        when(timeOffRepository.findByMonitorIdAndStartTimeLessThanAndEndTimeGreaterThan(eq(MONITOR), any(), any()))
                .thenReturn(List.of());

        List<FreeSlotResponse> slots = service.computeFreeSlots(MONITOR, from, to, 60);

        // Lundi intact (09–12), mardi ampute le créneau 10–11 réservé. Aucun créneau hors 09–12.
        assertThat(slots).containsExactly(
                new FreeSlotResponse(at(MONDAY, 9, 0), at(MONDAY, 10, 0)),
                new FreeSlotResponse(at(MONDAY, 10, 0), at(MONDAY, 11, 0)),
                new FreeSlotResponse(at(MONDAY, 11, 0), at(MONDAY, 12, 0)),
                new FreeSlotResponse(at(TUESDAY, 9, 0), at(TUESDAY, 10, 0)),
                new FreeSlotResponse(at(TUESDAY, 11, 0), at(TUESDAY, 12, 0)));
    }

    @Test
    void computeFreeSlots_mergesOverlappingRules_noDuplicateSlots() {
        // Deux règles qui se chevauchent le même jour (données incohérentes) → fenêtre fusionnée 09–14,
        // sans créneaux en double.
        Instant from = at(MONDAY, 0, 0);
        Instant to = at(MONDAY.plusDays(1), 0, 0);
        when(availabilityRepository.findByMonitorIdOrderByDayOfWeekAscStartTimeAsc(MONITOR))
                .thenReturn(List.of(rule(DayOfWeek.MONDAY, 9, 0, 12, 0), rule(DayOfWeek.MONDAY, 10, 0, 14, 0)));
        when(sessionRepository.findMonitorSessionsInRange(eq(MONITOR), any(), any(), any()))
                .thenReturn(List.of());
        when(timeOffRepository.findByMonitorIdAndStartTimeLessThanAndEndTimeGreaterThan(eq(MONITOR), any(), any()))
                .thenReturn(List.of());

        List<FreeSlotResponse> slots = service.computeFreeSlots(MONITOR, from, to, 60);

        assertThat(slots).containsExactly(
                new FreeSlotResponse(at(MONDAY, 9, 0), at(MONDAY, 10, 0)),
                new FreeSlotResponse(at(MONDAY, 10, 0), at(MONDAY, 11, 0)),
                new FreeSlotResponse(at(MONDAY, 11, 0), at(MONDAY, 12, 0)),
                new FreeSlotResponse(at(MONDAY, 12, 0), at(MONDAY, 13, 0)),
                new FreeSlotResponse(at(MONDAY, 13, 0), at(MONDAY, 14, 0)));
    }

    @Test
    void computeFreeSlots_emptyWhenNoAvailabilityDeclared() {
        when(availabilityRepository.findByMonitorIdOrderByDayOfWeekAscStartTimeAsc(MONITOR))
                .thenReturn(List.of());

        List<FreeSlotResponse> slots =
                service.computeFreeSlots(MONITOR, at(MONDAY, 0, 0), at(MONDAY.plusDays(1), 0, 0), 60);

        assertThat(slots).isEmpty();
    }

    @Test
    void computeFreeSlots_rejectsInvalidRange() {
        Instant from = at(MONDAY, 0, 0);
        assertThatThrownBy(() -> service.computeFreeSlots(MONITOR, from, from, 60))
                .isInstanceOf(CrossServiceValidationException.class);
    }

    // ---- isMonitorAvailable ----

    @Test
    void isMonitorAvailable_trueWhenWithinHoursAndNoTimeOff() {
        when(timeOffRepository.findByMonitorIdAndStartTimeLessThanAndEndTimeGreaterThan(eq(MONITOR), any(), any()))
                .thenReturn(List.of());
        when(availabilityRepository.findByMonitorIdOrderByDayOfWeekAscStartTimeAsc(MONITOR))
                .thenReturn(List.of(rule(DayOfWeek.MONDAY, 9, 0, 12, 0)));

        assertThat(service.isMonitorAvailable(MONITOR, at(MONDAY, 10, 0), at(MONDAY, 11, 0))).isTrue();
    }

    @Test
    void isMonitorAvailable_falseWhenOutsideDeclaredHours() {
        when(timeOffRepository.findByMonitorIdAndStartTimeLessThanAndEndTimeGreaterThan(eq(MONITOR), any(), any()))
                .thenReturn(List.of());
        when(availabilityRepository.findByMonitorIdOrderByDayOfWeekAscStartTimeAsc(MONITOR))
                .thenReturn(List.of(rule(DayOfWeek.MONDAY, 9, 0, 12, 0)));

        assertThat(service.isMonitorAvailable(MONITOR, at(MONDAY, 13, 0), at(MONDAY, 14, 0))).isFalse();
    }

    @Test
    void isMonitorAvailable_falseWhenOverlappingTimeOff() {
        when(timeOffRepository.findByMonitorIdAndStartTimeLessThanAndEndTimeGreaterThan(eq(MONITOR), any(), any()))
                .thenReturn(List.of(timeOff(at(MONDAY, 9, 30), at(MONDAY, 10, 30))));

        assertThat(service.isMonitorAvailable(MONITOR, at(MONDAY, 10, 0), at(MONDAY, 11, 0))).isFalse();
    }

    @Test
    void isMonitorAvailable_trueWhenNoHoursDeclared_backwardCompatible() {
        when(timeOffRepository.findByMonitorIdAndStartTimeLessThanAndEndTimeGreaterThan(eq(MONITOR), any(), any()))
                .thenReturn(List.of());
        when(availabilityRepository.findByMonitorIdOrderByDayOfWeekAscStartTimeAsc(MONITOR))
                .thenReturn(List.of());

        assertThat(service.isMonitorAvailable(MONITOR, at(MONDAY, 10, 0), at(MONDAY, 11, 0))).isTrue();
    }

    // ---- replaceAvailability ----

    @Test
    void replaceAvailability_rejectsWindowWhereEndNotAfterStart() {
        when(userClient.getByKeycloakId(MONITOR)).thenReturn(Optional.of(monitorInfo(true)));
        AvailabilityRuleRequest bad =
                new AvailabilityRuleRequest(DayOfWeek.MONDAY, LocalTime.of(12, 0), LocalTime.of(9, 0));

        assertThatThrownBy(() -> service.replaceAvailability(
                MONITOR, List.of(bad), "owner", Set.of("OWNER")))
                .isInstanceOf(CrossServiceValidationException.class);
        verify(availabilityRepository, never()).deleteByMonitorId(anyString());
    }

    @Test
    void replaceAvailability_rejectsOverlappingRulesSameDay() {
        when(userClient.getByKeycloakId(MONITOR)).thenReturn(Optional.of(monitorInfo(true)));
        AvailabilityRuleRequest a =
                new AvailabilityRuleRequest(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0));
        AvailabilityRuleRequest b =
                new AvailabilityRuleRequest(DayOfWeek.MONDAY, LocalTime.of(11, 0), LocalTime.of(13, 0));

        assertThatThrownBy(() -> service.replaceAvailability(
                MONITOR, List.of(a, b), "owner", Set.of("OWNER")))
                .isInstanceOf(CrossServiceValidationException.class);
        verify(availabilityRepository, never()).deleteByMonitorId(anyString());
    }

    @Test
    void replaceAvailability_allowsAdjacentRulesSameDay() {
        when(userClient.getByKeycloakId(MONITOR)).thenReturn(Optional.of(monitorInfo(true)));
        when(availabilityRepository.saveAll(org.mockito.ArgumentMatchers.anyList()))
                .thenAnswer(inv -> inv.getArgument(0));
        AvailabilityRuleRequest morning =
                new AvailabilityRuleRequest(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0));
        AvailabilityRuleRequest afternoon =
                new AvailabilityRuleRequest(DayOfWeek.MONDAY, LocalTime.of(12, 0), LocalTime.of(14, 0));

        service.replaceAvailability(MONITOR, List.of(morning, afternoon), "owner", Set.of("OWNER"));

        verify(availabilityRepository).deleteByMonitorId(MONITOR);
    }

    @Test
    void replaceAvailability_deniesMonitorManagingAnotherMonitor() {
        assertThatThrownBy(() -> service.replaceAvailability(
                MONITOR, List.of(), "monitor-other", Set.of("MONITOR")))
                .isInstanceOf(AccessDeniedException.class);
        verify(userClient, never()).getByKeycloakId(anyString());
    }

    @Test
    void replaceAvailability_deniesClient() {
        assertThatThrownBy(() -> service.replaceAvailability(
                MONITOR, List.of(), "client-1", Set.of("CLIENT")))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---- createTimeOff / deleteTimeOff ----

    @Test
    void createTimeOff_rejectsEndNotAfterStart() {
        when(userClient.getByKeycloakId(MONITOR)).thenReturn(Optional.of(monitorInfo(true)));
        Instant when = at(MONDAY, 9, 0);
        assertThatThrownBy(() -> service.createTimeOff(
                MONITOR, new CreateTimeOffRequest(when, when, "Congé"), MONITOR, Set.of("MONITOR")))
                .isInstanceOf(CrossServiceValidationException.class);
        verify(timeOffRepository, never()).save(any());
    }

    @Test
    void deleteTimeOff_rejectsWhenBelongingToAnotherMonitor() {
        UUID id = UUID.randomUUID();
        MonitorTimeOff other = timeOff(at(MONDAY, 9, 0), at(MONDAY, 10, 0));
        other.setMonitorId("monitor-other");
        when(timeOffRepository.findById(id)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.deleteTimeOff(MONITOR, id, "owner", Set.of("OWNER")))
                .isInstanceOf(TimeOffNotFoundException.class);
        verify(timeOffRepository, never()).delete(any());
    }

    // ---- helpers ----

    private static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(hour, minute).atZone(ZONE).toInstant();
    }

    private static MonitorAvailability rule(DayOfWeek dow, int sh, int sm, int eh, int em) {
        MonitorAvailability rule = new MonitorAvailability();
        rule.setMonitorId(MONITOR);
        rule.setDayOfWeek(dow);
        rule.setStartTime(LocalTime.of(sh, sm));
        rule.setEndTime(LocalTime.of(eh, em));
        return rule;
    }

    private static Session session(Instant start, Instant end) {
        Session session = new Session();
        session.setMonitorId(MONITOR);
        session.setStatus(SessionStatus.CONFIRMED);
        session.setStartTime(start);
        session.setEndTime(end);
        return session;
    }

    private static MonitorTimeOff timeOff(Instant start, Instant end) {
        MonitorTimeOff off = new MonitorTimeOff();
        off.setMonitorId(MONITOR);
        off.setStartTime(start);
        off.setEndTime(end);
        return off;
    }

    private static UserInfo monitorInfo(boolean active) {
        return new UserInfo(MONITOR, "Moni", "Teur", Set.of("MONITOR"), active);
    }
}