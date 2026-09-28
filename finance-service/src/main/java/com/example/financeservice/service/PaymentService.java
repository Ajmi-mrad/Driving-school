package com.example.financeservice.service;

import com.example.financeservice.client.NotificationClient;
import com.example.financeservice.domain.Enrollment;
import com.example.financeservice.domain.EnrollmentStatus;
import com.example.financeservice.domain.InvoiceType;
import com.example.financeservice.domain.Payment;
import com.example.financeservice.exception.EnrollmentNotFoundException;
import com.example.financeservice.exception.InvalidPaymentException;
import com.example.financeservice.exception.PaymentNotFoundException;
import com.example.financeservice.mapper.FinanceMapper;
import com.example.financeservice.repository.EnrollmentRepository;
import com.example.financeservice.repository.PaymentRepository;
import com.example.financeservice.web.dto.CreatePaymentRequest;
import com.example.financeservice.web.dto.PaymentResponse;
import com.example.financeservice.web.dto.UpdatePaymentRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
    private final SoftDelete softDelete;
    private final AuditService auditService;
    private final FinanceMapper mapper;

    public PaymentService(PaymentRepository paymentRepository,
                          EnrollmentRepository enrollmentRepository,
                          InvoiceService invoiceService,
                          NotificationClient notificationClient,
                          SoftDelete softDelete,
                          AuditService auditService,
                          FinanceMapper mapper) {
        this.paymentRepository = paymentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.invoiceService = invoiceService;
        this.notificationClient = notificationClient;
        this.softDelete = softDelete;
        this.auditService = auditService;
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
        if (req.amount().compareTo(enrollment.outstanding()) > 0) {
            throw new InvalidPaymentException(overpaidMessage(req.amount(), enrollment.outstanding()));
        }

        Payment payment = new Payment();
        payment.setEnrollmentId(enrollment.getId());
        payment.setClientId(enrollment.getClientId());
        payment.setAmount(req.amount());
        payment.setMethod(req.method());
        payment.setReference(req.reference());
        payment.setPaidAt(Instant.now());
        Payment saved = paymentRepository.save(payment);

        enrollment.applyPaymentDelta(req.amount());

        invoiceService.issue(enrollment, InvoiceType.RECEIPT, req.amount(), saved.getId());
        auditService.record("CREATED", "PAYMENT", saved.getId(), req.amount() + " (" + req.method() + ")");
        return mapper.toPaymentResponse(saved);
    }

    /**
     * Annule (soft delete) un paiement et rétablit le solde : le montant est retiré du payé de
     * l'inscription, une inscription auto-clôturée par ce paiement repasse en ACTIVE, et le reçu
     * correspondant est annulé. La ligne de paiement est conservée pour l'audit.
     */
    @Transactional
    public void delete(UUID id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
        Enrollment enrollment = enrollmentRepository.findById(payment.getEnrollmentId())
                .orElseThrow(() -> new EnrollmentNotFoundException(payment.getEnrollmentId()));

        enrollment.applyPaymentDelta(payment.getAmount().negate());

        invoiceService.voidByPayment(payment.getId());
        softDelete.mark(payment);
        auditService.record("VOIDED", "PAYMENT", payment.getId(), payment.getAmount().toString());
    }

    /**
     * Modifie un paiement : le solde de l'inscription est resynchronisé (ancien montant retiré, nouveau
     * appliqué), le statut ACTIVE/COMPLETED réévalué, et le reçu correspondant mis à jour. Le nouveau
     * montant ne peut pas dépasser le solde dû recalculé.
     */
    @Transactional
    public PaymentResponse update(UUID id, UpdatePaymentRequest req) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
        Enrollment enrollment = enrollmentRepository.findById(payment.getEnrollmentId())
                .orElseThrow(() -> new EnrollmentNotFoundException(payment.getEnrollmentId()));
        if (enrollment.getStatus() == EnrollmentStatus.CANCELLED) {
            throw new InvalidPaymentException("Inscription annulée : paiement non modifiable");
        }

        // Retrait de l'ancien montant, validation du nouveau contre le solde dû recalculé, puis application.
        enrollment.applyPaymentDelta(payment.getAmount().negate());
        if (req.amount().compareTo(enrollment.outstanding()) > 0) {
            throw new InvalidPaymentException(overpaidMessage(req.amount(), enrollment.outstanding()));
        }
        enrollment.applyPaymentDelta(req.amount());

        payment.setAmount(req.amount());
        payment.setMethod(req.method());
        payment.setReference(req.reference());
        invoiceService.updateReceiptAmount(payment.getId(), req.amount());
        auditService.record("UPDATED", "PAYMENT", payment.getId(), req.amount() + " (" + req.method() + ")");
        return mapper.toPaymentResponse(payment);
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

    private static String overpaidMessage(BigDecimal amount, BigDecimal outstanding) {
        return "Montant (" + amount + ") supérieur au solde dû (" + outstanding + ")";
    }
}
