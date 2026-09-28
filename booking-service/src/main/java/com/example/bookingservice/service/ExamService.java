package com.example.bookingservice.service;

import com.example.bookingservice.client.CommunicationClient;
import com.example.bookingservice.client.UserClient;
import com.example.bookingservice.client.VehicleClient;
import com.example.bookingservice.client.dto.NotificationRequest;
import com.example.bookingservice.client.dto.UserInfo;
import com.example.bookingservice.client.dto.VehicleInfo;
import com.example.bookingservice.domain.Exam;
import com.example.bookingservice.domain.ExamStatus;
import com.example.bookingservice.domain.ExamType;
import com.example.bookingservice.exception.BookingConflictException;
import com.example.bookingservice.exception.CrossServiceValidationException;
import com.example.bookingservice.exception.ExamNotFoundException;
import com.example.bookingservice.exception.InvalidSessionStateException;
import com.example.bookingservice.mapper.ExamMapper;
import com.example.bookingservice.repository.ExamRepository;
import com.example.bookingservice.repository.ExamSpecifications;
import com.example.bookingservice.web.dto.CreateExamRequest;
import com.example.bookingservice.web.dto.ExamResponse;
import com.example.bookingservice.web.dto.ExamResultRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Logique métier des examens officiels (code / conduite). Contrairement aux séances, un examen est
 * planifié par le staff uniquement, puis résolu par un résultat (réussite / échec / absence). Le
 * contexte appelant est passé en paramètres ({@code callerSub}, {@code callerRoles} — rôles « nus »)
 * pour garder le service testable sans types de sécurité.
 */
@Service
public class ExamService {

    private static final Logger log = LoggerFactory.getLogger(ExamService.class);

    private static final String ROLE_OWNER = "OWNER";
    private static final String ROLE_SECRETARY = "SECRETARY";
    private static final String ROLE_CLIENT = "CLIENT";
    private static final String ROLE_MONITOR = "MONITOR";

    /** Fuseau d'affichage des dates dans le libellé des notifications (auto-école de Tunisie). */
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Africa/Tunis");
    private static final DateTimeFormatter WHEN_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH'h'mm", Locale.FRENCH).withZone(DISPLAY_ZONE);

    private final ExamRepository examRepository;
    private final ExamMapper examMapper;
    private final UserClient userClient;
    private final VehicleClient vehicleClient;
    private final CommunicationClient communicationClient;

    public ExamService(ExamRepository examRepository, ExamMapper examMapper, UserClient userClient,
                       VehicleClient vehicleClient, CommunicationClient communicationClient) {
        this.examRepository = examRepository;
        this.examMapper = examMapper;
        this.userClient = userClient;
        this.vehicleClient = vehicleClient;
        this.communicationClient = communicationClient;
    }

    @Transactional
    public ExamResponse create(CreateExamRequest req) {
        validateFuture(req.scheduledAt());
        // 1) Résolution + validation de l'élève.
        String clientId = req.clientId();
        if (clientId == null || clientId.isBlank()) {
            throw new CrossServiceValidationException("clientId requis");
        }
        UserInfo client = userClient.getByKeycloakId(clientId)
                .orElseThrow(() -> new CrossServiceValidationException("Élève introuvable: " + clientId));
        if (!client.active() || !client.hasRole(ROLE_CLIENT)) {
            throw new CrossServiceValidationException("Élève inactif ou rôle invalide: " + clientId);
        }

        // 2) Un seul examen non résolu (planifié) par type et par élève.
        if (examRepository.existsByClientIdAndTypeAndStatus(clientId, req.type(), ExamStatus.SCHEDULED)) {
            throw new BookingConflictException(
                    "L'élève a déjà un examen de " + typeLabel(req.type()) + " planifié");
        }

        Exam exam = new Exam();
        exam.setType(req.type());
        exam.setClientId(clientId);
        exam.setScheduledAt(req.scheduledAt());
        exam.setLocation(normalize(req.location()));
        exam.setStatus(ExamStatus.SCHEDULED);

        // 3) Moniteur accompagnateur (facultatif, validé si fourni).
        String monitorId = req.monitorId();
        if (monitorId != null && !monitorId.isBlank()) {
            UserInfo monitor = userClient.getByKeycloakId(monitorId)
                    .orElseThrow(() -> new CrossServiceValidationException("Moniteur introuvable: " + monitorId));
            if (!monitor.active() || !monitor.hasRole(ROLE_MONITOR)) {
                throw new CrossServiceValidationException("Moniteur inactif ou rôle invalide: " + monitorId);
            }
            exam.setMonitorId(monitorId);
        }

        // 4) Véhicule d'examen (facultatif, validé si fourni ; interdit pour le code).
        if (req.vehicleId() != null) {
            if (req.type() == ExamType.CODE) {
                throw new CrossServiceValidationException("Un véhicule ne peut être affecté à un examen de code");
            }
            VehicleInfo vehicle = vehicleClient.get(req.vehicleId())
                    .orElseThrow(() -> new CrossServiceValidationException("Véhicule introuvable: " + req.vehicleId()));
            exam.setVehicleId(vehicle.id());
        }

        // 5) Numéro de tentative = tentatives antérieures réelles (hors annulées) du même type + 1.
        exam.setAttemptNumber(
                (int) examRepository.countByClientIdAndTypeAndStatusNot(clientId, req.type(), ExamStatus.CANCELLED) + 1);

        Exam saved = examRepository.save(exam);
        log.info("Examen planifié id={} type={} client={} tentative={}",
                saved.getId(), saved.getType(), saved.getClientId(), saved.getAttemptNumber());

        String clientName = client.fullName();
        runAfterCommit(() -> notifyScheduled(saved, clientName));
        return examMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ExamResponse get(UUID id, String callerSub, Set<String> callerRoles) {
        Exam exam = findOrThrow(id);
        if (!canView(exam, callerSub, callerRoles)) {
            throw new AccessDeniedException("Accès refusé à cet examen");
        }
        return examMapper.toResponse(exam);
    }

    @Transactional(readOnly = true)
    public List<ExamResponse> list(ExamStatus status, ExamType type, Instant from, Instant to,
                                   String monitorId, String clientId, String callerSub, Set<String> callerRoles) {
        // Le staff voit tout ; un moniteur ne voit que ses examens ; un élève que les siens. Tout autre
        // appelant (ex. jeton de service sans rôle métier) n'a aucun périmètre : liste vide.
        if (!hasAny(callerRoles, ROLE_OWNER, ROLE_SECRETARY)) {
            if (callerRoles.contains(ROLE_MONITOR)) {
                monitorId = callerSub;
                clientId = null;
            } else if (callerRoles.contains(ROLE_CLIENT)) {
                clientId = callerSub;
                monitorId = null;
            } else {
                return List.of();
            }
        }
        return examRepository.findAll(
                        ExamSpecifications.filter(status, type, from, to, monitorId, clientId),
                        Sort.by(Sort.Direction.ASC, "scheduledAt")).stream()
                .map(examMapper::toResponse)
                .toList();
    }

    /** Enregistre le résultat d'un examen planifié (réussite / échec / absence). */
    @Transactional
    public ExamResponse recordResult(UUID id, ExamResultRequest req) {
        ExamStatus outcome = req.outcome();
        if (outcome != ExamStatus.PASSED && outcome != ExamStatus.FAILED && outcome != ExamStatus.NO_SHOW) {
            throw new InvalidSessionStateException(
                    "Résultat invalide : attendu PASSED, FAILED ou NO_SHOW (reçu " + outcome + ")");
        }
        Exam exam = findOrThrow(id);
        requireScheduled(exam, "Seul un examen planifié peut recevoir un résultat");
        exam.setStatus(outcome);
        exam.setResultNote(normalize(req.note()));
        Exam saved = examRepository.save(exam);
        runAfterCommit(() -> notifyResult(saved));
        return examMapper.toResponse(saved);
    }

    @Transactional
    public ExamResponse reschedule(UUID id, Instant scheduledAt) {
        validateFuture(scheduledAt);
        Exam exam = findOrThrow(id);
        requireScheduled(exam, "Seul un examen planifié peut être reporté");
        exam.setScheduledAt(scheduledAt);
        Exam saved = examRepository.save(exam);
        runAfterCommit(() -> notify(saved.getClientId(), "EXAM_RESCHEDULED", "Examen reporté",
                "Votre examen de " + typeLabel(saved.getType()) + " a été reporté au " + when(saved) + ".", saved));
        return examMapper.toResponse(saved);
    }

    @Transactional
    public ExamResponse cancel(UUID id) {
        Exam exam = findOrThrow(id);
        requireScheduled(exam, "Seul un examen planifié peut être annulé");
        exam.setStatus(ExamStatus.CANCELLED);
        Exam saved = examRepository.save(exam);
        runAfterCommit(() -> notify(saved.getClientId(), "EXAM_CANCELLED", "Examen annulé",
                "Votre examen de " + typeLabel(saved.getType()) + " du " + when(saved)
                        + " a été annulé par l'auto-école.", saved));
        return examMapper.toResponse(saved);
    }

    /** Exécute l'action après le commit de la transaction courante, ou immédiatement s'il n'y en a pas. */
    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    // ---- notifications (best-effort, invoquées après commit) ----

    private void notifyScheduled(Exam exam, String clientName) {
        String body = "Votre examen de " + typeLabel(exam.getType()) + " (tentative n°"
                + exam.getAttemptNumber() + ") est planifié le " + when(exam)
                + locationSuffix(exam.getLocation()) + ".";
        notify(exam.getClientId(), "EXAM_SCHEDULED", "Examen planifié", body, exam);
    }

    private void notifyResult(Exam exam) {
        switch (exam.getStatus()) {
            case PASSED -> notify(exam.getClientId(), "EXAM_PASSED", "Examen réussi",
                    "Félicitations ! Vous avez réussi votre examen de " + typeLabel(exam.getType()) + "."
                            + noteSuffix(exam.getResultNote()), exam);
            case FAILED -> notify(exam.getClientId(), "EXAM_FAILED", "Examen échoué",
                    "Votre examen de " + typeLabel(exam.getType()) + " est un échec."
                            + noteSuffix(exam.getResultNote()), exam);
            case NO_SHOW -> notify(exam.getClientId(), "EXAM_FAILED", "Absence à l'examen",
                    "Vous étiez absent à votre examen de " + typeLabel(exam.getType()) + " du " + when(exam) + "."
                            + noteSuffix(exam.getResultNote()), exam);
            default -> { /* aucun autre statut n'émet de notification de résultat */ }
        }
    }

    private void notify(String recipientId, String type, String title, String body, Exam exam) {
        if (recipientId == null || recipientId.isBlank()) {
            return;
        }
        communicationClient.notify(
                new NotificationRequest(recipientId, type, title, body, exam.getId().toString()));
    }

    // ---- helpers ----

    private String typeLabel(ExamType type) {
        return type == ExamType.DRIVING ? "conduite" : "code";
    }

    private String when(Exam exam) {
        return WHEN_FMT.format(exam.getScheduledAt());
    }

    private String noteSuffix(String note) {
        return (note == null || note.isBlank()) ? "" : " Remarque : " + note.trim();
    }

    private String locationSuffix(String location) {
        return (location == null || location.isBlank()) ? "" : " à " + location.trim();
    }

    private String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private boolean canView(Exam exam, String callerSub, Set<String> callerRoles) {
        if (hasAny(callerRoles, ROLE_OWNER, ROLE_SECRETARY)) {
            return true;
        }
        return callerSub.equals(exam.getClientId()) || callerSub.equals(exam.getMonitorId());
    }

    private void requireScheduled(Exam exam, String message) {
        if (exam.getStatus() != ExamStatus.SCHEDULED) {
            throw new InvalidSessionStateException(message + " (statut actuel: " + exam.getStatus() + ")");
        }
    }

    private void validateFuture(Instant when) {
        if (when == null || !when.isAfter(Instant.now())) {
            throw new CrossServiceValidationException("La date de l'examen doit être dans le futur");
        }
    }

    private Exam findOrThrow(UUID id) {
        return examRepository.findById(id).orElseThrow(() -> new ExamNotFoundException(id));
    }

    private boolean hasAny(Set<String> roles, String... wanted) {
        for (String w : wanted) {
            if (roles.contains(w)) {
                return true;
            }
        }
        return false;
    }
}
