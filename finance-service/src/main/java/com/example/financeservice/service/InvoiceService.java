package com.example.financeservice.service;

import com.example.financeservice.client.UserClient;
import com.example.financeservice.client.dto.UserInfo;
import com.example.financeservice.domain.Enrollment;
import com.example.financeservice.domain.Invoice;
import com.example.financeservice.domain.InvoiceType;
import com.example.financeservice.exception.InvoiceNotFoundException;
import com.example.financeservice.mapper.FinanceMapper;
import com.example.financeservice.repository.InvoiceRepository;
import com.example.financeservice.web.dto.EmailResult;
import com.example.financeservice.web.dto.InvoiceResponse;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Émission et consultation des documents financiers (factures à l'inscription, reçus à l'encaissement).
 * En v1, seul l'enregistrement est produit ; le PDF est prévu ultérieurement.
 */
@Service
public class InvoiceService {

    private static final String OWNER = "OWNER";
    private static final String SECRETARY = "SECRETARY";
    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    private final InvoiceRepository invoiceRepository;
    private final SoftDelete softDelete;
    private final AuditService auditService;
    private final FinanceMapper mapper;
    private final InvoicePdfService pdfService;
    private final UserClient userClient;
    private final JavaMailSender mailSender;
    private final String mailFrom;

    public InvoiceService(InvoiceRepository invoiceRepository, SoftDelete softDelete, AuditService auditService,
                          FinanceMapper mapper, InvoicePdfService pdfService, UserClient userClient,
                          JavaMailSender mailSender,
                          @Value("${finance.mail.from:no-reply@auto-ecole.local}") String mailFrom) {
        this.invoiceRepository = invoiceRepository;
        this.softDelete = softDelete;
        this.auditService = auditService;
        this.mapper = mapper;
        this.pdfService = pdfService;
        this.userClient = userClient;
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
    }

    /** Émet un document (facture ou reçu) rattaché à une inscription. Appelé dans une transaction ouverte. */
    @Transactional
    public Invoice issue(Enrollment enrollment, InvoiceType type, BigDecimal amount) {
        return issue(enrollment, type, amount, null);
    }

    /** Émet un document, en le rattachant éventuellement à un paiement (reçu). */
    @Transactional
    public Invoice issue(Enrollment enrollment, InvoiceType type, BigDecimal amount, UUID paymentId) {
        Invoice invoice = new Invoice();
        invoice.setEnrollmentId(enrollment.getId());
        invoice.setClientId(enrollment.getClientId());
        invoice.setType(type);
        invoice.setAmount(amount);
        invoice.setIssuedAt(Instant.now());
        invoice.setNumber(nextNumber(type));
        invoice.setPaymentId(paymentId);
        return invoiceRepository.save(invoice);
    }

    /** Met à jour le montant du reçu rattaché à un paiement (paiement modifié), s'il existe. */
    @Transactional
    public void updateReceiptAmount(UUID paymentId, BigDecimal amount) {
        invoiceRepository.findByPaymentId(paymentId).ifPresent(invoice -> invoice.setAmount(amount));
    }

    /** Met à jour le montant de la facture d'inscription (forfait changé), s'il existe. */
    @Transactional
    public void updateEnrollmentInvoiceAmount(UUID enrollmentId, BigDecimal amount) {
        invoiceRepository.findFirstByEnrollmentIdAndTypeOrderByIssuedAtDesc(enrollmentId, InvoiceType.INVOICE)
                .ifPresent(invoice -> invoice.setAmount(amount));
    }

    /** Annule (soft delete) le reçu rattaché à un paiement, s'il existe. */
    @Transactional
    public void voidByPayment(UUID paymentId) {
        invoiceRepository.findByPaymentId(paymentId).ifPresent(softDelete::mark);
    }

    /** Annule (soft delete) tous les documents vivants d'une inscription. */
    @Transactional
    public void voidForEnrollment(UUID enrollmentId) {
        invoiceRepository.findByEnrollmentIdOrderByIssuedAtDesc(enrollmentId).forEach(softDelete::mark);
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> listForClient(String clientId, String callerSub, Set<String> callerRoles) {
        // Staff sans clientId => tous les documents ; staff avec clientId => ceux de ce client.
        // Un client (non-staff) est toujours restreint à ses propres documents (le clientId fourni est ignoré).
        String target = isStaff(callerRoles) ? clientId : callerSub;
        List<Invoice> invoices = (target == null)
                ? invoiceRepository.findAllByOrderByIssuedAtDesc()
                : invoiceRepository.findByClientIdOrderByIssuedAtDesc(target);
        return invoices.stream()
                .map(mapper::toInvoiceResponse)
                .toList();
    }

    /**
     * Charge un document en appliquant le contrôle d'accès : le staff accède à tout, un client
     * uniquement à ses propres documents.
     */
    @Transactional(readOnly = true)
    public Invoice load(UUID id, String callerSub, Set<String> callerRoles) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new InvoiceNotFoundException(id));
        if (!isStaff(callerRoles) && !invoice.getClientId().equals(callerSub)) {
            throw new AccessDeniedException("Document d'un autre client");
        }
        return invoice;
    }

    /** PDF d'un document (contrôle d'accès staff/propriétaire). */
    @Transactional(readOnly = true)
    public byte[] pdf(UUID id, String callerSub, Set<String> callerRoles) {
        return pdfService.render(load(id, callerSub, callerRoles));
    }

    /** Archive ZIP des PDF des documents demandés (réservé au staff). Ignore les ids introuvables. */
    @Transactional(readOnly = true)
    public byte[] zip(List<UUID> ids) {
        List<Invoice> invoices = invoiceRepository.findAllById(ids);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Invoice invoice : invoices) {
                zip.putNextEntry(new ZipEntry(pdfService.filenameFor(invoice)));
                zip.write(pdfService.render(invoice));
                zip.closeEntry();
            }
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Échec de la génération de l'archive", ex);
        }
        return out.toByteArray();
    }

    /**
     * Envoie les documents demandés par email, groupés par client (un message par client, avec ses
     * PDF en pièces jointes). Best-effort : un client sans email ou un envoi en échec est ignoré et
     * comptabilisé dans {@code skipped}. Réservé au staff.
     */
    @Transactional(readOnly = true)
    public EmailResult email(List<UUID> ids) {
        Map<String, List<Invoice>> byClient = new LinkedHashMap<>();
        for (Invoice invoice : invoiceRepository.findAllById(ids)) {
            byClient.computeIfAbsent(invoice.getClientId(), k -> new java.util.ArrayList<>()).add(invoice);
        }
        int sent = 0;
        int skipped = 0;
        for (Map.Entry<String, List<Invoice>> entry : byClient.entrySet()) {
            List<Invoice> docs = entry.getValue();
            String address = userClient.getByKeycloakId(entry.getKey())
                    .map(UserInfo::email)
                    .filter(e -> e != null && !e.isBlank())
                    .orElse(null);
            if (address == null || sendToClient(address, docs) == false) {
                skipped += docs.size();
            } else {
                sent++;
            }
        }
        return new EmailResult(sent, skipped);
    }

    private boolean sendToClient(String address, List<Invoice> docs) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(mailFrom);
            helper.setTo(address);
            helper.setSubject("Vos documents — Auto-École");
            StringBuilder body = new StringBuilder("Bonjour,\n\nVeuillez trouver ci-joint vos documents :\n");
            for (Invoice invoice : docs) {
                body.append("  • ").append(invoice.getNumber()).append('\n');
                helper.addAttachment(pdfService.filenameFor(invoice),
                        new ByteArrayResource(pdfService.render(invoice)));
            }
            body.append("\nCordialement,\nAuto-École");
            helper.setText(body.toString());
            mailSender.send(message);
            return true;
        } catch (MailException | jakarta.mail.MessagingException ex) {
            log.warn("Échec de l'envoi des documents à {} : {}", address, ex.getMessage());
            return false;
        }
    }

    /** Annulation (soft delete) groupée de documents (réservé au staff). Ignore les ids introuvables. */
    @Transactional
    public void voidInvoices(List<UUID> ids) {
        invoiceRepository.findAllById(ids).forEach(invoice -> {
            softDelete.mark(invoice);
            auditService.record("VOIDED", "INVOICE", invoice.getId(), invoice.getNumber());
        });
    }

    /** Annulation (soft delete) d'un document (réservé au staff). */
    @Transactional
    public void voidInvoice(UUID id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new InvoiceNotFoundException(id));
        softDelete.mark(invoice);
        auditService.record("VOIDED", "INVOICE", invoice.getId(), invoice.getNumber());
    }

    private String nextNumber(InvoiceType type) {
        String prefix = type == InvoiceType.RECEIPT ? "RCP" : "INV";
        int year = Instant.now().atZone(ZoneOffset.UTC).getYear();
        long sequence = invoiceRepository.nextInvoiceNumber();
        return "%s-%d-%06d".formatted(prefix, year, sequence);
    }

    private boolean isStaff(Set<String> roles) {
        return roles.contains(OWNER) || roles.contains(SECRETARY);
    }
}
