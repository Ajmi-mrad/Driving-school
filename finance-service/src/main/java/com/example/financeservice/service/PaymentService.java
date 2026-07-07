package com.example.financeservice.service;

import com.example.financeservice.client.NotificationClient;
import com.example.financeservice.domain.Enrollment;
import com.example.financeservice.domain.EnrollmentStatus;
import com.example.financeservice.domain.InvoiceType;
import com.example.financeservice.domain.Payment;
import com.example.financeservice.exception.EnrollmentNotFoundException;
import com.example.financeservice.exception.InvalidPaymentException;
import com.example.financeservice.mapper.FinanceMapper;
import com.example.financeservice.repository.EnrollmentRepository;
import com.example.financeservice.repository.PaymentRepository;
import com.example.financeservice.web.dto.CreatePaymentRequest;
import com.example.financeservice.web.dto.PaymentResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Encaissements : enregistrement d'un paiement (met à jour le solde de l'inscription, émet un reçu,
 * clôt l'inscription une fois soldée), consultation filtrée par rôle et relances (rappels de paiement).
 */
@Service
public class PaymentService {

    private static final String OWNER = "OWNER";
    private static final String SECRETARY = "SECRETARY";
    private static final String REMINDER_MESSAGE =
            "Vous avez un solde restant dû pour votre forfait d'auto-école.";

    private final PaymentRepository paymentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final InvoiceService invoiceService;
    private final NotificationClient notificationClient;
    private final FinanceMapper mapper;

    public PaymentService(PaymentRepository paymentRepository,
                          EnrollmentRepository enrollmentRepository,
                          InvoiceService invoiceService,
                          NotificationClient notificationClient,
                          FinanceMapper mapper) {
        this.paymentRepository = paymentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.invoiceService = invoiceService;
        this.notificationClient = notificationClient;
        this.mapper = mapper;
    }

    @Transactional
    public PaymentResponse record(CreatePaymentRequest req) {
        Enrollment enrollment = enrollmentRepository.findById(req.enrollmentId())
                .orElseThrow(() -> new EnrollmentNotFoundException(req.enrollmentId()));
        if (enrollment.getStatus() != EnrollmentStatus.ACTIVE) {
            throw new InvalidPaymentException(
                    "Inscription non active : paiement impossible (statut " + enrollment.getStatus() + ")");
        }
        BigDecimal outstanding = enrollment.outstanding();
        if (req.amount().compareTo(outstanding) > 0) {
            throw new InvalidPaymentException(
                    "Montant (" + req.amount() + ") supérieur au solde dû (" + outstanding + ")");
        }

        Payment payment = new Payment();
        payment.setEnrollmentId(enrollment.getId());
        payment.setClientId(enrollment.getClientId());
        payment.setAmount(req.amount());
        payment.setMethod(req.method());
        payment.setReference(req.reference());
        payment.setPaidAt(Instant.now());
        Payment saved = paymentRepository.save(payment);

        enrollment.setAmountPaid(enrollment.getAmountPaid().add(req.amount()));
        if (enrollment.getAmountPaid().compareTo(enrollment.getTotalPrice()) >= 0
                && enrollment.getStatus() == EnrollmentStatus.ACTIVE) {
            enrollment.setStatus(EnrollmentStatus.COMPLETED);
        }

        invoiceService.issue(enrollment, InvoiceType.RECEIPT, req.amount());
        return mapper.toPaymentResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> list(String clientId, String callerSub, Set<String> callerRoles) {
        String target = isStaff(callerRoles) ? clientId : callerSub;
        List<Payment> payments = (target == null)
                ? paymentRepository.findAll()
                : paymentRepository.findByClientIdOrderByPaidAtDesc(target);
        return payments.stream().map(mapper::toPaymentResponse).toList();
    }

    /**
     * Relance : notifie le client du solde restant dû via le communication-service (best-effort).
     * Volontairement NON transactionnel : la lecture ci-dessous s'exécute dans sa propre transaction
     * (Spring Data), puis l'appel réseau a lieu sans retenir de connexion DB.
     */
    public boolean remind(java.util.UUID enrollmentId, String message) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new EnrollmentNotFoundException(enrollmentId));
        BigDecimal outstanding = enrollment.outstanding();
        if (outstanding.signum() <= 0) {
            throw new InvalidPaymentException("Aucun solde dû : relance inutile");
        }
        String body = (message == null || message.isBlank()) ? REMINDER_MESSAGE : message;
        return notificationClient.sendPaymentReminder(enrollment.getClientId(), outstanding, body);
    }

    private boolean isStaff(Set<String> roles) {
        return roles.contains(OWNER) || roles.contains(SECRETARY);
    }
}
