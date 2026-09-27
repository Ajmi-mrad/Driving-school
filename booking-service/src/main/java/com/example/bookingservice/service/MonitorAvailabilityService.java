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
import com.example.bookingservice.web.dto.AvailabilityRuleResponse;
import com.example.bookingservice.web.dto.CreateTimeOffRequest;
import com.example.bookingservice.web.dto.FreeSlotResponse;
import com.example.bookingservice.web.dto.TimeOffResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Disponibilité des moniteurs : heures de travail hebdomadaires, absences ponctuelles, et calcul des
 * créneaux réservables (heures de travail − séances réservées − absences). Ce dernier alimente la
 * réservation manuelle et le futur assistant de planification par IA.
 *
 * <p>Comme {@code SessionService}/{@code ExamService}, le contexte appelant est passé en paramètres
 * ({@code callerSub}, {@code callerRoles} — rôles « nus ») pour rester testable sans types de sécurité.
 * Les heures des règles hebdomadaires sont en horloge locale de l'auto-école ({@code Africa/Tunis}).
 */
@Service
public class MonitorAvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(MonitorAvailabilityService.class);

    private static final String ROLE_OWNER = "OWNER";
    private static final String ROLE_SECRETARY = "SECRETARY";
    private static final String ROLE_MONITOR = "MONITOR";

    /** Statuts qui « occupent » un créneau (identique à la détection de conflit de SessionService). */
    private static final List<SessionStatus> ACTIVE = List.of(SessionStatus.PENDING, SessionStatus.CONFIRMED);

    /** Fuseau de l'auto-école : les heures de travail locales y sont ancrées pour produire des instants. */
    private static final ZoneId ZONE = ZoneId.of("Africa/Tunis");

    /** Garde-fou : borne le nombre de jours parcourus par le calcul des créneaux. */
    private static final long MAX_RANGE_DAYS = 62;

    private final MonitorAvailabilityRepository availabilityRepository;
    private final MonitorTimeOffRepository timeOffRepository;
    private final SessionRepository sessionRepository;
    private final MonitorMapper monitorMapper;
    private final UserClient userClient;

    public MonitorAvailabilityService(MonitorAvailabilityRepository availabilityRepository,
                                      MonitorTimeOffRepository timeOffRepository,
                                      SessionRepository sessionRepository, MonitorMapper monitorMapper,
                                      UserClient userClient) {
        this.availabilityRepository = availabilityRepository;
        this.timeOffRepository = timeOffRepository;
        this.sessionRepository = sessionRepository;
        this.monitorMapper = monitorMapper;
        this.userClient = userClient;
    }

    // ---- heures de travail hebdomadaires ----

    @Transactional(readOnly = true)
    public List<AvailabilityRuleResponse> getAvailability(String monitorId, String callerSub,
                                                          Set<String> callerRoles) {
        requireCanManage(monitorId, callerSub, callerRoles);
        return availabilityRepository.findByMonitorIdOrderByDayOfWeekAscStartTimeAsc(monitorId).stream()
                .map(monitorMapper::toResponse)
                .toList();
    }

    /** Remplace en bloc les heures de travail d'un moniteur (staff, ou le moniteur pour lui-même). */
    @Transactional
    public List<AvailabilityRuleResponse> replaceAvailability(String monitorId,
                                                              List<AvailabilityRuleRequest> rules,
                                                              String callerSub, Set<String> callerRoles) {
        requireCanManage(monitorId, callerSub, callerRoles);
        validateMonitor(monitorId);
        for (AvailabilityRuleRequest rule : rules) {
            if (!rule.endTime().isAfter(rule.startTime())) {
                throw new CrossServiceValidationException(
                        "endTime doit être postérieur à startTime (" + rule.dayOfWeek() + ")");
            }
        }
        // Rejeter des créneaux qui se chevauchent le même jour (l'adjacence, fin == début, reste permise).
        List<AvailabilityRuleRequest> sorted = rules.stream()
                .sorted(Comparator.comparing(AvailabilityRuleRequest::dayOfWeek)
                        .thenComparing(AvailabilityRuleRequest::startTime))
                .toList();
        for (int i = 1; i < sorted.size(); i++) {
            AvailabilityRuleRequest prev = sorted.get(i - 1);
            AvailabilityRuleRequest cur = sorted.get(i);
            if (prev.dayOfWeek() == cur.dayOfWeek() && cur.startTime().isBefore(prev.endTime())) {
                throw new CrossServiceValidationException(
                        "Créneaux qui se chevauchent pour " + cur.dayOfWeek());
            }
        }
        availabilityRepository.deleteByMonitorId(monitorId);
        List<MonitorAvailability> saved = availabilityRepository.saveAll(rules.stream().map(rule -> {
            MonitorAvailability entity = new MonitorAvailability();
            entity.setMonitorId(monitorId);
            entity.setDayOfWeek(rule.dayOfWeek());
            entity.setStartTime(rule.startTime());
            entity.setEndTime(rule.endTime());
            return entity;
        }).toList());
        log.info("Disponibilité du moniteur {} remplacée : {} règle(s)", monitorId, saved.size());
        return saved.stream()
                .sorted(Comparator.comparing(MonitorAvailability::getDayOfWeek)
                        .thenComparing(MonitorAvailability::getStartTime))
                .map(monitorMapper::toResponse)
                .toList();
    }

    // ---- absences ponctuelles ----

    @Transactional(readOnly = true)
    public List<TimeOffResponse> listTimeOff(String monitorId, String callerSub, Set<String> callerRoles) {
        // Les absences (motif en clair, potentiellement sensible) ne sont visibles que du staff et du
        // moniteur concerné ; un élève passe par les créneaux calculés (free-slots), qui n'exposent rien.
        requireCanManage(monitorId, callerSub, callerRoles);
        return timeOffRepository.findByMonitorIdOrderByStartTimeAsc(monitorId).stream()
                .map(monitorMapper::toResponse)
                .toList();
    }

    @Transactional
    public TimeOffResponse createTimeOff(String monitorId, CreateTimeOffRequest req,
                                         String callerSub, Set<String> callerRoles) {
        requireCanManage(monitorId, callerSub, callerRoles);
        validateMonitor(monitorId);
        if (!req.endTime().isAfter(req.startTime())) {
            throw new CrossServiceValidationException("endTime doit être postérieur à startTime");
        }
        MonitorTimeOff timeOff = new MonitorTimeOff();
        timeOff.setMonitorId(monitorId);
        timeOff.setStartTime(req.startTime());
        timeOff.setEndTime(req.endTime());
        timeOff.setReason(normalize(req.reason()));
        MonitorTimeOff saved = timeOffRepository.save(timeOff);
        log.info("Absence enregistrée pour le moniteur {} du {} au {}",
                monitorId, saved.getStartTime(), saved.getEndTime());
        return monitorMapper.toResponse(saved);
    }

    @Transactional
    public void deleteTimeOff(String monitorId, UUID timeOffId, String callerSub, Set<String> callerRoles) {
        requireCanManage(monitorId, callerSub, callerRoles);
        MonitorTimeOff timeOff = timeOffRepository.findById(timeOffId)
                .orElseThrow(() -> new TimeOffNotFoundException(timeOffId));
        if (!timeOff.getMonitorId().equals(monitorId)) {
            throw new TimeOffNotFoundException(timeOffId);
        }
        timeOffRepository.delete(timeOff);
    }

    // ---- créneaux réservables ----

    /**
     * Calcule les créneaux réservables d'un moniteur sur {@code [from, to)} : pour chaque jour, les
     * fenêtres de travail (converties du fuseau local en instants), moins les séances actives et les
     * absences, découpées en tranches de {@code slotMinutes} qui tiennent entièrement.
     */
    @Transactional(readOnly = true)
    public List<FreeSlotResponse> computeFreeSlots(String monitorId, Instant from, Instant to, int slotMinutes) {
        if (from == null || to == null || !to.isAfter(from)) {
            throw new CrossServiceValidationException("Intervalle invalide : 'to' doit être postérieur à 'from'");
        }
        if (slotMinutes <= 0) {
            throw new CrossServiceValidationException("slotMinutes doit être strictement positif");
        }
        LocalDate firstDay = LocalDate.ofInstant(from, ZONE);
        LocalDate lastDay = LocalDate.ofInstant(to, ZONE);
        if (firstDay.plusDays(MAX_RANGE_DAYS).isBefore(lastDay)) {
            throw new CrossServiceValidationException(
                    "Intervalle trop large : maximum " + MAX_RANGE_DAYS + " jours");
        }

        List<MonitorAvailability> rules =
                availabilityRepository.findByMonitorIdOrderByDayOfWeekAscStartTimeAsc(monitorId);
        if (rules.isEmpty()) {
            return List.of();
        }

        // Occupation = séances actives + absences chevauchant l'intervalle, fusionnées et triées.
        List<Interval> busy = new ArrayList<>();
        for (Session s : sessionRepository.findMonitorSessionsInRange(monitorId, ACTIVE, from, to)) {
            busy.add(new Interval(s.getStartTime(), s.getEndTime()));
        }
        for (MonitorTimeOff off : timeOffRepository
                .findByMonitorIdAndStartTimeLessThanAndEndTimeGreaterThan(monitorId, to, from)) {
            busy.add(new Interval(off.getStartTime(), off.getEndTime()));
        }
        busy.sort(Comparator.comparing(Interval::start));

        Duration slot = Duration.ofMinutes(slotMinutes);
        List<FreeSlotResponse> slots = new ArrayList<>();
        for (Interval window : availabilityWindows(rules, from, to)) {
            for (Interval free : subtract(window, busy)) {
                sliceInto(free, slot, slots);
            }
        }
        slots.sort(Comparator.comparing(FreeSlotResponse::startTime));
        return slots;
    }

    /**
     * Indique si un moniteur peut prendre une séance sur {@code [start, end)} : l'intervalle ne doit
     * chevaucher aucune absence et, <em>si</em> le moniteur a déclaré des horaires, doit être entièrement
     * couvert par ceux-ci. Un moniteur sans horaires déclarés est réputé sans contrainte (rétro-compatible).
     * Consommé par la réservation ({@code SessionService}) pour refuser une séance hors disponibilité.
     */
    @Transactional(readOnly = true)
    public boolean isMonitorAvailable(String monitorId, Instant start, Instant end) {
        if (!timeOffRepository
                .findByMonitorIdAndStartTimeLessThanAndEndTimeGreaterThan(monitorId, end, start).isEmpty()) {
            return false;
        }
        List<MonitorAvailability> rules =
                availabilityRepository.findByMonitorIdOrderByDayOfWeekAscStartTimeAsc(monitorId);
        if (rules.isEmpty()) {
            return true;
        }
        // Couvert ssi il ne reste aucune portion de [start, end) hors des fenêtres de travail.
        return subtract(new Interval(start, end), availabilityWindows(rules, start, end)).isEmpty();
    }

    /**
     * Matérialise les fenêtres de travail hebdomadaires en intervalles absolus sur {@code [from, to)}
     * (heures locales ancrées dans le fuseau de l'auto-école, bornées à l'intervalle), triées par début et
     * <em>fusionnées</em> : des règles qui se chevauchent ou se touchent ne produisent qu'une seule fenêtre,
     * pour que le calcul des créneaux n'émette jamais de doublons même si des données incohérentes existent.
     */
    private List<Interval> availabilityWindows(List<MonitorAvailability> rules, Instant from, Instant to) {
        List<Interval> windows = new ArrayList<>();
        LocalDate firstDay = LocalDate.ofInstant(from, ZONE);
        LocalDate lastDay = LocalDate.ofInstant(to, ZONE);
        for (LocalDate day = firstDay; !day.isAfter(lastDay); day = day.plusDays(1)) {
            for (MonitorAvailability rule : rules) {
                if (rule.getDayOfWeek() != day.getDayOfWeek()) {
                    continue;
                }
                Instant winStart = max(day.atTime(rule.getStartTime()).atZone(ZONE).toInstant(), from);
                Instant winEnd = min(day.atTime(rule.getEndTime()).atZone(ZONE).toInstant(), to);
                if (winStart.isBefore(winEnd)) {
                    windows.add(new Interval(winStart, winEnd));
                }
            }
        }
        windows.sort(Comparator.comparing(Interval::start));
        // Fusion des fenêtres qui se chevauchent ou sont contiguës (last.end >= next.start).
        List<Interval> merged = new ArrayList<>();
        for (Interval w : windows) {
            if (merged.isEmpty() || w.start().isAfter(merged.get(merged.size() - 1).end())) {
                merged.add(w);
            } else {
                Interval last = merged.remove(merged.size() - 1);
                merged.add(new Interval(last.start(), max(last.end(), w.end())));
            }
        }
        return merged;
    }

    /** Découpe une plage libre en tranches consécutives de durée {@code slot} qui tiennent entièrement. */
    private void sliceInto(Interval free, Duration slot, List<FreeSlotResponse> out) {
        Instant cursor = free.start();
        while (!cursor.plus(slot).isAfter(free.end())) {
            Instant next = cursor.plus(slot);
            out.add(new FreeSlotResponse(cursor, next));
            cursor = next;
        }
    }

    /** Retranche les intervalles occupés (triés par début) d'une fenêtre, renvoyant les plages libres. */
    private List<Interval> subtract(Interval window, List<Interval> busy) {
        List<Interval> free = new ArrayList<>();
        Instant cursor = window.start();
        for (Interval b : busy) {
            // Borne l'occupation à la fenêtre : {@code busy} couvre tout [from, to] alors que {@code window}
            // n'est qu'une journée — un intervalle situé après la fenêtre ne doit pas étirer la plage libre.
            Instant bStart = min(max(b.start(), window.start()), window.end());
            Instant bEnd = min(b.end(), window.end());
            if (!bEnd.isAfter(cursor)) {
                continue; // pas de chevauchement au-delà du curseur
            }
            if (bStart.isAfter(cursor)) {
                free.add(new Interval(cursor, bStart));
            }
            cursor = bEnd;
            if (!cursor.isBefore(window.end())) {
                break;
            }
        }
        if (cursor.isBefore(window.end())) {
            free.add(new Interval(cursor, window.end()));
        }
        return free;
    }

    // ---- helpers ----

    private void validateMonitor(String monitorId) {
        UserInfo monitor = userClient.getByKeycloakId(monitorId)
                .orElseThrow(() -> new CrossServiceValidationException("Moniteur introuvable: " + monitorId));
        if (!monitor.active() || !monitor.hasRole(ROLE_MONITOR)) {
            throw new CrossServiceValidationException("Moniteur inactif ou rôle invalide: " + monitorId);
        }
    }

    /** Staff (OWNER/SECRETARY) gère n'importe quel moniteur ; un moniteur ne gère que lui-même. */
    private void requireCanManage(String monitorId, String callerSub, Set<String> callerRoles) {
        boolean staff = callerRoles.contains(ROLE_OWNER) || callerRoles.contains(ROLE_SECRETARY);
        boolean self = callerRoles.contains(ROLE_MONITOR) && monitorId.equals(callerSub);
        if (!staff && !self) {
            throw new AccessDeniedException("Gestion de la disponibilité réservée au staff ou au moniteur concerné");
        }
    }

    private String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    /** Intervalle temporel absolu {@code [start, end)} interne au calcul des créneaux. */
    private record Interval(Instant start, Instant end) {
    }
}