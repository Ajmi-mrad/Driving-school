package com.example.bookingservice.dev;

import com.example.bookingservice.dev.dto.BookingExport;
import com.example.bookingservice.dev.dto.BookingSeedRequest;
import com.example.bookingservice.dev.dto.SeedResult;
import com.example.bookingservice.domain.BookingSettings;
import com.example.bookingservice.domain.Exam;
import com.example.bookingservice.domain.ExamStatus;
import com.example.bookingservice.domain.ExamType;
import com.example.bookingservice.domain.Session;
import com.example.bookingservice.domain.SessionStatus;
import com.example.bookingservice.domain.SessionType;
import com.example.bookingservice.mapper.ExamMapper;
import com.example.bookingservice.mapper.SessionMapper;
import com.example.bookingservice.repository.BookingSettingsRepository;
import com.example.bookingservice.repository.ExamRepository;
import com.example.bookingservice.repository.SessionRepository;
import com.example.bookingservice.web.dto.BookingSettingsResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Outil de développement (activé par {@code app.dev-tools.enabled=true}) : peuplement et remise à
 * zéro des séances. Jeu de données aligné sur le mock frontend (mock/db.ts). Les acteurs et le
 * véhicule proviennent des cartes renvoyées par les seeds auth et vehicle.
 */
@Service
@ConditionalOnProperty(name = "app.dev-tools.enabled", havingValue = "true")
public class DevDataService {

    private static final Logger log = LoggerFactory.getLogger(DevDataService.class);

    private record SeedSession(SessionType type, String clientKey, String monitorKey, String vehicleKey,
                               long startHours, long endHours, SessionStatus status, String notes) {
    }

    private record SeedExam(ExamType type, String clientKey, String monitorKey, String vehicleKey,
                            long scheduledHours, String location, ExamStatus status, int attemptNumber,
                            String resultNote) {
    }

    private static final List<SeedExam> SEED_EXAMS = List.of(
            new SeedExam(ExamType.CODE, "u-cli-1", null, null, -120,
                    "Centre d'examen Tunis", ExamStatus.PASSED, 1, "Belle prestation, 38/40"),
            new SeedExam(ExamType.DRIVING, "u-cli-2", "u-mon-1", "v-1", 72,
                    "Centre d'examen Ariana", ExamStatus.SCHEDULED, 1, null));

    private static final List<SeedSession> SEED_SESSIONS = List.of(
            new SeedSession(SessionType.DRIVING, "u-cli-1", "u-mon-1", "v-1", 3, 4,
                    SessionStatus.CONFIRMED, "Travail sur les creneaux"),
            new SeedSession(SessionType.DRIVING, "u-cli-2", "u-mon-1", "v-2", 26, 27,
                    SessionStatus.PENDING, null),
            new SeedSession(SessionType.CODE, "u-cli-3", null, null, 48, 49,
                    SessionStatus.CONFIRMED, "Seance de code en salle"),
            new SeedSession(SessionType.DRIVING, "u-cli-1", "u-mon-2", "v-4", -48, -47,
                    SessionStatus.COMPLETED, "Conduite sur autoroute"),
            new SeedSession(SessionType.DRIVING, "u-cli-3", "u-mon-2", "v-1", -19, -18,
                    SessionStatus.CANCELLED, null),
            new SeedSession(SessionType.DRIVING, "u-cli-2", "u-mon-2", "v-2", 6, 7,
                    SessionStatus.PENDING, "Premiere lecon"));

    private final SessionRepository sessionRepository;
    private final ExamRepository examRepository;
    private final BookingSettingsRepository settingsRepository;
    private final SessionMapper sessionMapper;
    private final ExamMapper examMapper;

    @PersistenceContext
    private EntityManager entityManager;

    public DevDataService(SessionRepository sessionRepository, ExamRepository examRepository,
                          BookingSettingsRepository settingsRepository, SessionMapper sessionMapper,
                          ExamMapper examMapper) {
        this.sessionRepository = sessionRepository;
        this.examRepository = examRepository;
        this.settingsRepository = settingsRepository;
        this.sessionMapper = sessionMapper;
        this.examMapper = examMapper;
    }

    @Transactional
    public SeedResult seed(BookingSeedRequest request) {
        // Idempotent : si des séances existent déjà, ne rien toucher (y compris les paramètres).
        if (sessionRepository.count() > 0) {
            return new SeedResult(0, SEED_SESSIONS.size());
        }
        restoreDefaultSettings();
        Map<String, String> users = request == null ? Map.of() : request.users();
        Map<String, String> vehicles = request == null ? Map.of() : request.vehicles();
        Instant now = Instant.now();

        int created = 0;
        for (SeedSession ss : SEED_SESSIONS) {
            Session session = new Session();
            session.setType(ss.type());
            session.setClientId(requireSub(users, ss.clientKey()));
            session.setMonitorId(ss.monitorKey() == null ? null : requireSub(users, ss.monitorKey()));
            session.setVehicleId(ss.vehicleKey() == null ? null : vehicleId(vehicles, ss.vehicleKey()));
            session.setStartTime(now.plus(Duration.ofHours(ss.startHours())));
            session.setEndTime(now.plus(Duration.ofHours(ss.endHours())));
            session.setStatus(ss.status());
            session.setNotes(ss.notes());
            sessionRepository.save(session);
            created++;
        }
        for (SeedExam se : SEED_EXAMS) {
            Exam exam = new Exam();
            exam.setType(se.type());
            exam.setClientId(requireSub(users, se.clientKey()));
            exam.setMonitorId(se.monitorKey() == null ? null : requireSub(users, se.monitorKey()));
            exam.setVehicleId(se.vehicleKey() == null ? null : vehicleId(vehicles, se.vehicleKey()));
            exam.setScheduledAt(now.plus(Duration.ofHours(se.scheduledHours())));
            exam.setLocation(se.location());
            exam.setStatus(se.status());
            exam.setAttemptNumber(se.attemptNumber());
            exam.setResultNote(se.resultNote());
            examRepository.save(exam);
            created++;
        }
        log.info("Seed booking: {} séances/examens créés", created);
        return new SeedResult(created, 0);
    }

    @Transactional
    public void reset() {
        entityManager.createNativeQuery("TRUNCATE TABLE sessions RESTART IDENTITY CASCADE").executeUpdate();
        entityManager.createNativeQuery("TRUNCATE TABLE exams RESTART IDENTITY CASCADE").executeUpdate();
        restoreDefaultSettings();
        log.info("Reset booking: séances et examens vidés, paramètres réinitialisés");
    }

    @Transactional(readOnly = true)
    public BookingExport export() {
        BookingSettings s = settingsRepository.findById(BookingSettings.SINGLETON_ID).orElse(null);
        BookingSettingsResponse settings = s == null
                ? new BookingSettingsResponse(false, 24)
                : new BookingSettingsResponse(s.isAutoValidationEnabled(), s.getCancellationNoticeHours());
        return new BookingExport(
                sessionRepository.findAll().stream().map(sessionMapper::toResponse).toList(),
                examRepository.findAll().stream().map(examMapper::toResponse).toList(),
                settings);
    }

    /** Ramène la ligne unique de configuration à ses valeurs par défaut (jamais supprimée). */
    private void restoreDefaultSettings() {
        BookingSettings settings = settingsRepository.findById(BookingSettings.SINGLETON_ID)
                .orElseGet(() -> {
                    BookingSettings created = new BookingSettings();
                    created.setId(BookingSettings.SINGLETON_ID);
                    return created;
                });
        settings.setAutoValidationEnabled(false);
        settings.setCancellationNoticeHours(24);
        settingsRepository.save(settings);
    }

    private static String requireSub(Map<String, String> users, String key) {
        String sub = users.get(key);
        if (sub == null || sub.isBlank()) {
            throw new IllegalArgumentException(
                    "Seed booking : identifiant Keycloak manquant pour " + key
                            + " (le seed auth doit être exécuté en premier)");
        }
        return sub;
    }

    private static UUID vehicleId(Map<String, String> vehicles, String key) {
        String id = vehicles.get(key);
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Seed booking : identifiant véhicule manquant pour " + key
                            + " (le seed vehicle doit être exécuté en premier)");
        }
        return UUID.fromString(id);
    }
}
