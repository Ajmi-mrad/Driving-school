package com.example.financeservice.service;

import com.example.financeservice.client.UserClient;
import com.example.financeservice.domain.Enrollment;
import com.example.financeservice.domain.EnrollmentStatus;
import com.example.financeservice.domain.Forfait;
import com.example.financeservice.domain.InvoiceType;
import com.example.financeservice.exception.EnrollmentNotFoundException;
import com.example.financeservice.exception.ForfaitNotFoundException;
import com.example.financeservice.mapper.FinanceMapper;
import com.example.financeservice.repository.EnrollmentRepository;
import com.example.financeservice.repository.ForfaitRepository;
import com.example.financeservice.web.dto.ConsumeRequest;
import com.example.financeservice.web.dto.CreateEnrollmentRequest;
import com.example.financeservice.web.dto.EnrollmentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Inscriptions d'un client à un forfait : achat (avec émission de facture), consultation filtrée par
 * rôle, et décompte de la consommation (heures de conduite / séances de code) déclenché par le
 * booking-service lorsqu'une séance est réalisée.
 */
@Service
public class EnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentService.class);

    private static final String OWNER = "OWNER";
    private static final String SECRETARY = "SECRETARY";

    private final EnrollmentRepository enrollmentRepository;
    private final ForfaitRepository forfaitRepository;
    private final InvoiceService invoiceService;
    private final UserClient userClient;
    private final FinanceMapper mapper;

    public EnrollmentService(EnrollmentRepository enrollmentRepository,
                             ForfaitRepository forfaitRepository,
                             InvoiceService invoiceService,
                             UserClient userClient,
                             FinanceMapper mapper) {
        this.enrollmentRepository = enrollmentRepository;
        this.forfaitRepository = forfaitRepository;
        this.invoiceService = invoiceService;
        this.userClient = userClient;
        this.mapper = mapper;
    }

    @Transactional
    public EnrollmentResponse create(CreateEnrollmentRequest req) {
        validateClientBestEffort(req.clientId());

        Forfait forfait = forfaitRepository.findById(req.forfaitId())
                .orElseThrow(() -> new ForfaitNotFoundException(req.forfaitId()));

        Enrollment enrollment = new Enrollment();
        enrollment.setClientId(req.clientId());
        enrollment.setForfaitId(forfait.getId());
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        enrollment.setTotalPrice(forfait.getPrice());
        enrollment.setAmountPaid(BigDecimal.ZERO);
        enrollment.setRemainingDrivingHours(forfait.getDrivingHours());
        enrollment.setRemainingCodeSessions(forfait.getCodeSessions());
        enrollment.setEnrolledAt(Instant.now());
        Enrollment saved = enrollmentRepository.save(enrollment);

        invoiceService.issue(saved, InvoiceType.INVOICE, saved.getTotalPrice());
        return mapper.toEnrollmentResponse(saved);
    }

    @Transactional(readOnly = true)
    public EnrollmentResponse get(UUID id, String callerSub, Set<String> callerRoles) {
        Enrollment enrollment = enrollmentRepository.findById(id)
                .orElseThrow(() -> new EnrollmentNotFoundException(id));
        if (!isStaff(callerRoles) && !enrollment.getClientId().equals(callerSub)) {
            throw new AccessDeniedException("Inscription d'un autre client");
        }
        return mapper.toEnrollmentResponse(enrollment);
    }

    /** Staff : liste toutes les inscriptions (ou celles d'un client via {@code clientId}). Client : les siennes. */
    @Transactional(readOnly = true)
    public List<EnrollmentResponse> list(String clientId, String callerSub, Set<String> callerRoles) {
        String target = isStaff(callerRoles) ? clientId : callerSub;
        List<Enrollment> enrollments = (target == null)
                ? enrollmentRepository.findAll()
                : enrollmentRepository.findByClientIdOrderByEnrolledAtDesc(target);
        return enrollments.stream().map(mapper::toEnrollmentResponse).toList();
    }

    /**
     * Déduit la consommation d'une séance réalisée de l'inscription active du client (best-effort :
     * s'il n'y a pas d'inscription active, on journalise et on ignore).
     */
    @Transactional
    public void consume(ConsumeRequest req) {
        enrollmentRepository
                .findFirstByClientIdAndStatusOrderByEnrolledAtAsc(req.clientId(), EnrollmentStatus.ACTIVE)
                .ifPresentOrElse(enrollment -> {
                    enrollment.setRemainingDrivingHours(
                            Math.max(0, enrollment.getRemainingDrivingHours() - req.drivingHours()));
                    enrollment.setRemainingCodeSessions(
                            Math.max(0, enrollment.getRemainingCodeSessions() - req.codeSessions()));
                }, () -> log.warn("Consommation ignorée : aucune inscription active pour le client {}",
                        req.clientId()));
    }

    /**
     * Validation « best-effort » de l'existence du client auprès de l'auth-service. Tant que
     * l'endpoint de résolution par identifiant Keycloak n'est pas exposé aux comptes de service,
     * une réponse indisponible ne bloque pas l'inscription : on journalise et on poursuit.
     */
    private void validateClientBestEffort(String clientId) {
        try {
            userClient.getByKeycloakId(clientId).ifPresentOrElse(
                    user -> { },
                    () -> log.warn("Client {} non confirmé par l'auth-service — inscription poursuivie", clientId));
        } catch (RuntimeException ex) {
            log.warn("Validation du client {} indisponible ({}) — inscription poursuivie", clientId, ex.getMessage());
        }
    }

    private boolean isStaff(Set<String> roles) {
        return roles.contains(OWNER) || roles.contains(SECRETARY);
    }
}
