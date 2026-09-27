package com.example.bookingservice.service;

import com.example.bookingservice.client.CommunicationClient;
import com.example.bookingservice.client.FinanceClient;
import com.example.bookingservice.client.UserClient;
import com.example.bookingservice.client.VehicleClient;
import com.example.bookingservice.client.dto.NotificationRequest;
import com.example.bookingservice.client.dto.UserInfo;
import com.example.bookingservice.client.dto.VehicleInfo;
import com.example.bookingservice.domain.BookingSettings;
import com.example.bookingservice.domain.Session;
import com.example.bookingservice.domain.SessionStatus;
import com.example.bookingservice.domain.SessionType;
import com.example.bookingservice.exception.BookingConflictException;
import com.example.bookingservice.exception.CancellationTooLateException;
import com.example.bookingservice.exception.CrossServiceValidationException;
import com.example.bookingservice.exception.InvalidSessionStateException;
import com.example.bookingservice.exception.MonitorUnavailableException;
import com.example.bookingservice.exception.NoVehicleAvailableException;
import com.example.bookingservice.exception.SessionNotFoundException;
import com.example.bookingservice.mapper.SessionMapper;
import com.example.bookingservice.repository.SessionRepository;
import com.example.bookingservice.repository.SessionSpecifications;
import com.example.bookingservice.web.dto.CreateSessionRequest;
import com.example.bookingservice.web.dto.RescheduleRequest;
import com.example.bookingservice.web.dto.SessionResponse;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Logique métier de planification : validation des références inter-services, détection des
 * chevauchements, validation auto/manuelle, affectation automatique de véhicule, annulation avec
 * préavis et report.
 *
 * <p>Le contexte appelant est passé en paramètres ({@code callerSub}, {@code callerRoles} — rôles
 * « nus » sans préfixe ROLE_) pour garder le service testable sans types de sécurité.
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    /** Statuts qui « occupent » un créneau pour la détection de conflit. */
    private static final List<SessionStatus> ACTIVE = List.of(SessionStatus.PENDING, SessionStatus.CONFIRMED);

    private static final String ROLE_OWNER = "OWNER";
    private static final String ROLE_SECRETARY = "SECRETARY";
    private static final String ROLE_CLIENT = "CLIENT";
    private static final String ROLE_MONITOR = "MONITOR";

    /** Fuseau d'affichage des dates dans le libellé des notifications (auto-école de Tunisie). */
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Africa/Tunis");
    private static final DateTimeFormatter WHEN_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH'h'mm", Locale.FRENCH).withZone(DISPLAY_ZONE);

    private final SessionRepository sessionRepository;
    private final SessionMapper sessionMapper;
    private final BookingSettingsService settingsService;
    private final MonitorAvailabilityService availabilityService;
    private final UserClient userClient;
    private final VehicleClient vehicleClient;
    private final FinanceClient financeClient;
    private final CommunicationClient communicationClient;

    public SessionService(SessionRepository sessionRepository, SessionMapper sessionMapper,
                          BookingSettingsService settingsService,
                          MonitorAvailabilityService availabilityService, UserClient userClient,
                          VehicleClient vehicleClient, FinanceClient financeClient,
                          CommunicationClient communicationClient) {
        this.sessionRepository = sessionRepository;
        this.sessionMapper = sessionMapper;
        this.settingsService = settingsService;
        this.availabilityService = availabilityService;
        this.userClient = userClient;
        this.vehicleClient = vehicleClient;
        this.financeClient = financeClient;
        this.communicationClient = communicationClient;
    }

    @Transactional
    public SessionResponse create(CreateSessionRequest req, String callerSub, Set<String> callerRoles) {
        validateInterval(req.startTime(), req.endTime());

        boolean staff = hasAny(callerRoles, ROLE_OWNER, ROLE_SECRETARY);

        // 1) Résolution + validation de l'élève. Un CLIENT (non-staff) ne réserve que pour lui-même.
        String clientId = staff ? req.clientId() : callerSub;
        if (clientId == null || clientId.isBlank()) {
            throw new CrossServiceValidationException("clientId requis");
        }
        UserInfo client = userClient.getByKeycloakId(clientId)
                .orElseThrow(() -> new CrossServiceValidationException("Élève introuvable: " + clientId));
        if (!client.active() || !client.hasRole(ROLE_CLIENT)) {
            throw new CrossServiceValidationException("Élève inactif ou rôle invalide: " + clientId);
        }

        Session session = new Session();
        session.setType(req.type());
        session.setClientId(clientId);
        session.setStartTime(req.startTime());
        session.setEndTime(req.endTime());
        session.setNotes(req.notes());

        // 2) Moniteur : requis pour la conduite, optionnel (mais validé si fourni) pour le code.
        String monitorId = req.monitorId();
        if (req.type() == SessionType.DRIVING && (monitorId == null || monitorId.isBlank())) {
            throw new CrossServiceValidationException("monitorId requis pour une séance de conduite");
        }
        if (monitorId != null && !monitorId.isBlank()) {
            UserInfo monitor = userClient.getByKeycloakId(monitorId)
                    .orElseThrow(() -> new CrossServiceValidationException("Moniteur introuvable: " + monitorId));
            if (!monitor.active() || !monitor.hasRole(ROLE_MONITOR)) {
                throw new CrossServiceValidationException("Moniteur inactif ou rôle invalide: " + monitorId);
            }
            session.setMonitorId(monitorId);
        }

        // 3) Conflits élève / moniteur (le véhicule est traité à l'affectation ci-dessous).
        if (sessionRepository.clientHasOverlap(clientId, ACTIVE, req.startTime(), req.endTime(), null)) {
            throw new BookingConflictException("L'élève a déjà une séance sur ce créneau");
        }
        if (session.getMonitorId() != null
                && sessionRepository.monitorHasOverlap(session.getMonitorId(), ACTIVE, req.startTime(), req.endTime(), null)) {
            throw new BookingConflictException("Le moniteur a déjà une séance sur ce créneau");
        }
        ensureMonitorAvailable(session.getMonitorId(), req.startTime(), req.endTime());

        // 4) Véhicule : interdit pour le code ; explicite ou auto-affecté pour la conduite.
        if (req.type() == SessionType.CODE) {
            if (req.vehicleId() != null) {
                throw new CrossServiceValidationException("Un véhicule ne peut être affecté à une séance de code");
            }
        } else {
            session.setVehicleId(resolveVehicle(req.vehicleId(), req.startTime(), req.endTime()));
        }

        // 5) Validation automatique selon paramétrage (sinon en attente).
        BookingSettings settings = settingsService.getSettingsEntity();
        session.setStatus(settings.isAutoValidationEnabled() ? SessionStatus.CONFIRMED : SessionStatus.PENDING);

        Session saved = sessionRepository.save(session);
        log.info("Séance créée id={} type={} status={} client={} monitor={} vehicle={}",
                saved.getId(), saved.getType(), saved.getStatus(), saved.getClientId(),
                saved.getMonitorId(), saved.getVehicleId());

        // Notifications (best-effort, hors transaction) : auto-validée → confirmation à l'élève et
        // au moniteur ; sinon → demande à valider adressée au moniteur et au staff.
        String clientName = client.fullName();
        boolean autoValidated = saved.getStatus() == SessionStatus.CONFIRMED;
        runAfterCommit(() -> {
            if (autoValidated) {
                notifyClientConfirmed(saved, null);
                notifyMonitorNewSession(saved, clientName);
            } else {
                notifyRequested(saved, clientName);
            }
        });
        return sessionMapper.toResponse(saved);
    }

    /** Affecte un véhicule : valide celui fourni, sinon choisit un disponible sans chevauchement. */
    private UUID resolveVehicle(UUID requestedVehicleId, Instant start, Instant end) {
        if (requestedVehicleId != null) {
            VehicleInfo vehicle = vehicleClient.get(requestedVehicleId)
                    .orElseThrow(() -> new CrossServiceValidationException("Véhicule introuvable: " + requestedVehicleId));
            if (!vehicle.isAvailable()) {
                throw new CrossServiceValidationException("Véhicule indisponible: " + requestedVehicleId);
            }
            if (sessionRepository.vehicleHasOverlap(requestedVehicleId, ACTIVE, start, end, null)) {
                throw new BookingConflictException("Le véhicule a déjà une séance sur ce créneau");
            }
            return requestedVehicleId;
        }
        // Auto-affectation : premier véhicule AVAILABLE libre sur le créneau.
        return vehicleClient.listAvailable().stream()
                .map(VehicleInfo::id)
                .filter(id -> !sessionRepository.vehicleHasOverlap(id, ACTIVE, start, end, null))
                .findFirst()
                .orElseThrow(NoVehicleAvailableException::new);
    }

    @Transactional(readOnly = true)
    public SessionResponse get(UUID id, String callerSub, Set<String> callerRoles) {
        Session session = findOrThrow(id);
        if (!canView(session, callerSub, callerRoles)) {
            throw new AccessDeniedException("Accès refusé à cette séance");
        }
        return sessionMapper.toResponse(session);
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> list(SessionStatus status, SessionType type, Instant from, Instant to,
                                      String monitorId, String clientId, String callerSub, Set<String> callerRoles) {
        // Le staff voit tout ; un moniteur ne voit que ses séances ; un élève que les siennes.
        if (!hasAny(callerRoles, ROLE_OWNER, ROLE_SECRETARY)) {
            if (callerRoles.contains(ROLE_MONITOR)) {
                monitorId = callerSub;
                clientId = null;
            } else if (callerRoles.contains(ROLE_CLIENT)) {
                clientId = callerSub;
                monitorId = null;
            }
        }
        return sessionRepository.findAll(
                        SessionSpecifications.filter(status, type, from, to, monitorId, clientId),
                        Sort.by(Sort.Direction.ASC, "startTime")).stream()
                .map(sessionMapper::toResponse)
                .toList();
    }

    @Transactional
    public SessionResponse confirm(UUID id, String comment) {
        Session session = findOrThrow(id);
        requireStatus(session, SessionStatus.PENDING, "Seule une séance en attente peut être confirmée");
        ensureNoConflict(session);
        // Refuser la confirmation si la disponibilité du moniteur a changé depuis la demande (absence
        // ajoutée, horaires restreints) — sinon on figerait une séance CONFIRMED hors disponibilité.
        ensureMonitorAvailable(session.getMonitorId(), session.getStartTime(), session.getEndTime());
        session.setStatus(SessionStatus.CONFIRMED);
        session.setDecisionNote(normalizeComment(comment));
        Session saved = sessionRepository.save(session);
        runAfterCommit(() -> notifyClientConfirmed(saved, saved.getDecisionNote()));
        return sessionMapper.toResponse(saved);
    }

    @Transactional
    public SessionResponse refuse(UUID id, String comment) {
        Session session = findOrThrow(id);
        requireStatus(session, SessionStatus.PENDING, "Seule une séance en attente peut être refusée");
        session.setStatus(SessionStatus.REFUSED);
        session.setDecisionNote(normalizeComment(comment));
        Session saved = sessionRepository.save(session);
        runAfterCommit(() -> notifyClientRefused(saved, saved.getDecisionNote()));
        return sessionMapper.toResponse(saved);
    }

    /** Normalise une note de décision : {@code null} si vide/blanche. */
    private String normalizeComment(String comment) {
        return (comment == null || comment.isBlank()) ? null : comment.trim();
    }

    @Transactional
    public SessionResponse cancel(UUID id, String callerSub, Set<String> callerRoles) {
        Session session = findOrThrow(id);
        boolean staff = hasAny(callerRoles, ROLE_OWNER, ROLE_SECRETARY);
        if (!staff && !session.getClientId().equals(callerSub)) {
            throw new AccessDeniedException("Seul l'élève concerné ou le staff peut annuler");
        }
        if (session.getStatus() != SessionStatus.PENDING && session.getStatus() != SessionStatus.CONFIRMED) {
            throw new InvalidSessionStateException("Séance non annulable (statut " + session.getStatus() + ")");
        }
        // Préavis : un élève ne peut plus annuler passé le délai ; le staff peut outrepasser.
        int noticeHours = settingsService.getSettingsEntity().getCancellationNoticeHours();
        Instant deadline = session.getStartTime().minus(noticeHours, ChronoUnit.HOURS);
        if (!staff && Instant.now().isAfter(deadline)) {
            throw new CancellationTooLateException(
                    "Annulation impossible : le préavis de " + noticeHours + "h est dépassé");
        }
        session.setStatus(SessionStatus.CANCELLED);
        Session saved = sessionRepository.save(session);
        // Prévenir l'élève uniquement lorsque l'annulation vient de l'auto-école (staff),
        // pas lorsqu'il annule lui-même.
        if (staff) {
            runAfterCommit(() -> notifyClientCancelled(saved));
        }
        return sessionMapper.toResponse(saved);
    }

    @Transactional
    public SessionResponse reschedule(UUID id, RescheduleRequest req, String callerSub, Set<String> callerRoles) {
        Session session = findOrThrow(id);
        boolean staff = hasAny(callerRoles, ROLE_OWNER, ROLE_SECRETARY);
        if (!staff && !session.getClientId().equals(callerSub)) {
            throw new AccessDeniedException("Seul l'élève concerné ou le staff peut reporter");
        }
        if (session.getStatus() != SessionStatus.PENDING && session.getStatus() != SessionStatus.CONFIRMED) {
            throw new InvalidSessionStateException("Séance non reportable (statut " + session.getStatus() + ")");
        }
        validateInterval(req.startTime(), req.endTime());
        session.setStartTime(req.startTime());
        session.setEndTime(req.endTime());
        ensureNoConflict(session);
        ensureMonitorAvailable(session.getMonitorId(), req.startTime(), req.endTime());
        Session saved = sessionRepository.save(session);
        // Prévenir l'élève uniquement lorsque le report vient de l'auto-école (staff).
        if (staff) {
            runAfterCommit(() -> notifyClientRescheduled(saved));
        }
        return sessionMapper.toResponse(saved);
    }

    /**
     * Clôture une séance confirmée (statut {@code COMPLETED}) et signale la consommation
     * correspondante au finance-service (heures de conduite dérivées de la durée, ou une séance de
     * code). Le décompte financier est « best-effort » et n'empêche pas la clôture.
     */
    @Transactional
    public SessionResponse complete(UUID id) {
        Session session = findOrThrow(id);
        requireStatus(session, SessionStatus.CONFIRMED, "Seule une séance confirmée peut être clôturée");
        // Une séance ne peut être clôturée (et sa consommation décomptée) qu'une fois réellement terminée.
        if (session.getEndTime().isAfter(Instant.now())) {
            throw new InvalidSessionStateException(
                    "Une séance ne peut être clôturée qu'après sa fin (fin prévue: " + session.getEndTime() + ")");
        }
        session.setStatus(SessionStatus.COMPLETED);
        SessionResponse response = sessionMapper.toResponse(sessionRepository.save(session));

        String clientId = session.getClientId();
        int drivingHours = session.getType() == SessionType.DRIVING ? durationHours(session) : 0;
        int codeSessions = session.getType() == SessionType.CODE ? 1 : 0;
        // Décompte financier APRÈS le commit : hors transaction (aucune connexion DB retenue pendant
        // l'appel réseau) et sans jamais faire échouer la clôture déjà validée.
        runAfterCommit(() -> financeClient.reportConsumption(clientId, drivingHours, codeSessions));
        return response;
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

    /** Demande de séance en attente : adressée au moniteur concerné et à tout le staff. */
    private void notifyRequested(Session session, String clientName) {
        String title = "Nouvelle demande de séance";
        String body = "Demande de séance de " + typeLabel(session.getType()) + " le " + when(session)
                + (clientName == null ? "" : " par " + clientName) + ".";
        notify(session.getMonitorId(), "SESSION_REQUESTED", title, body, session);
        for (String staffId : userClient.listStaffIds()) {
            // Éviter un doublon si un membre du staff est aussi le moniteur assigné.
            if (!staffId.equals(session.getMonitorId())) {
                notify(staffId, "SESSION_REQUESTED", title, body, session);
            }
        }
    }

    /** Séance confirmée (validation manuelle ou auto-validation) : adressée à l'élève. */
    private void notifyClientConfirmed(Session session, String note) {
        String body = "Votre séance de " + typeLabel(session.getType()) + " du " + when(session)
                + " a été confirmée." + noteSuffix(note);
        notify(session.getClientId(), "SESSION_CONFIRMED", "Séance confirmée", body, session);
    }

    /** Nouvelle séance auto-validée : informe le moniteur assigné qu'une séance lui est affectée. */
    private void notifyMonitorNewSession(Session session, String clientName) {
        String body = "Nouvelle séance de " + typeLabel(session.getType()) + " le " + when(session)
                + (clientName == null ? "" : " avec " + clientName) + ".";
        notify(session.getMonitorId(), "SESSION_CONFIRMED", "Nouvelle séance", body, session);
    }

    /** Séance refusée : adressée à l'élève, avec le motif éventuel. */
    private void notifyClientRefused(Session session, String note) {
        String body = "Votre séance de " + typeLabel(session.getType()) + " du " + when(session)
                + " a été refusée." + noteSuffix(note);
        notify(session.getClientId(), "SESSION_REFUSED", "Séance refusée", body, session);
    }

    /** Séance reportée par le staff : adressée à l'élève. */
    private void notifyClientRescheduled(Session session) {
        String body = "Votre séance de " + typeLabel(session.getType()) + " a été reportée au "
                + when(session) + ".";
        notify(session.getClientId(), "SESSION_RESCHEDULED", "Séance reportée", body, session);
    }

    /** Séance annulée par le staff : adressée à l'élève. */
    private void notifyClientCancelled(Session session) {
        String body = "Votre séance de " + typeLabel(session.getType()) + " du " + when(session)
                + " a été annulée par l'auto-école.";
        notify(session.getClientId(), "SESSION_CANCELLED", "Séance annulée", body, session);
    }

    private void notify(String recipientId, String type, String title, String body, Session session) {
        if (recipientId == null || recipientId.isBlank()) {
            return;
        }
        communicationClient.notify(
                new NotificationRequest(recipientId, type, title, body, session.getId().toString()));
    }

    private String typeLabel(SessionType type) {
        return type == SessionType.DRIVING ? "conduite" : "code";
    }

    private String when(Session session) {
        return WHEN_FMT.format(session.getStartTime());
    }

    private String noteSuffix(String note) {
        return (note == null || note.isBlank()) ? "" : " Motif : " + note.trim();
    }

    // ---- helpers ----

    /** Durée de la séance arrondie à l'heure supérieure (au moins 1h). */
    private int durationHours(Session session) {
        long minutes = ChronoUnit.MINUTES.between(session.getStartTime(), session.getEndTime());
        return (int) Math.max(1, Math.ceil(minutes / 60.0));
    }

    /**
     * Refuse la séance si le moniteur n'est pas disponible sur le créneau (hors horaires déclarés ou
     * absence). Sans moniteur, ou pour un moniteur sans horaires déclarés, aucune contrainte.
     */
    private void ensureMonitorAvailable(String monitorId, Instant start, Instant end) {
        if (monitorId != null && !availabilityService.isMonitorAvailable(monitorId, start, end)) {
            throw new MonitorUnavailableException(
                    "Le moniteur n'est pas disponible sur ce créneau (hors horaires de travail ou absence)");
        }
    }

    private void ensureNoConflict(Session session) {
        UUID self = session.getId();
        if (sessionRepository.clientHasOverlap(session.getClientId(), ACTIVE,
                session.getStartTime(), session.getEndTime(), self)) {
            throw new BookingConflictException("L'élève a déjà une séance sur ce créneau");
        }
        if (session.getMonitorId() != null && sessionRepository.monitorHasOverlap(session.getMonitorId(), ACTIVE,
                session.getStartTime(), session.getEndTime(), self)) {
            throw new BookingConflictException("Le moniteur a déjà une séance sur ce créneau");
        }
        if (session.getVehicleId() != null && sessionRepository.vehicleHasOverlap(session.getVehicleId(), ACTIVE,
                session.getStartTime(), session.getEndTime(), self)) {
            throw new BookingConflictException("Le véhicule a déjà une séance sur ce créneau");
        }
    }

    private boolean canView(Session session, String callerSub, Set<String> callerRoles) {
        if (hasAny(callerRoles, ROLE_OWNER, ROLE_SECRETARY)) {
            return true;
        }
        return callerSub.equals(session.getClientId()) || callerSub.equals(session.getMonitorId());
    }

    private void requireStatus(Session session, SessionStatus expected, String message) {
        if (session.getStatus() != expected) {
            throw new InvalidSessionStateException(message + " (statut actuel: " + session.getStatus() + ")");
        }
    }

    private void validateInterval(Instant start, Instant end) {
        if (!end.isAfter(start)) {
            throw new CrossServiceValidationException("endTime doit être postérieur à startTime");
        }
    }

    private Session findOrThrow(UUID id) {
        return sessionRepository.findById(id).orElseThrow(() -> new SessionNotFoundException(id));
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