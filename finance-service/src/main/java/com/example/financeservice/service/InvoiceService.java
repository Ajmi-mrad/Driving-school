package com.example.financeservice.service;

import com.example.financeservice.domain.Enrollment;
import com.example.financeservice.domain.Invoice;
import com.example.financeservice.domain.InvoiceType;
import com.example.financeservice.mapper.FinanceMapper;
import com.example.financeservice.repository.InvoiceRepository;
import com.example.financeservice.web.dto.InvoiceResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

/**
 * Émission et consultation des documents financiers (factures à l'inscription, reçus à l'encaissement).
 * En v1, seul l'enregistrement est produit ; le PDF est prévu ultérieurement.
 */
@Service
public class InvoiceService {

    private static final String OWNER = "OWNER";
    private static final String SECRETARY = "SECRETARY";

    private final InvoiceRepository invoiceRepository;
    private final FinanceMapper mapper;

    public InvoiceService(InvoiceRepository invoiceRepository, FinanceMapper mapper) {
        this.invoiceRepository = invoiceRepository;
        this.mapper = mapper;
    }

    /** Émet un document (facture ou reçu) rattaché à une inscription. Appelé dans une transaction ouverte. */
    @Transactional
    public Invoice issue(Enrollment enrollment, InvoiceType type, BigDecimal amount) {
        Invoice invoice = new Invoice();
        invoice.setEnrollmentId(enrollment.getId());
        invoice.setClientId(enrollment.getClientId());
        invoice.setType(type);
        invoice.setAmount(amount);
        invoice.setIssuedAt(Instant.now());
        invoice.setNumber(nextNumber(type));
        return invoiceRepository.save(invoice);
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> listForClient(String clientId, String callerSub, Set<String> callerRoles) {
        if (!isStaff(callerRoles) && !callerSub.equals(clientId)) {
            throw new AccessDeniedException("Documents d'un autre client");
        }
        return invoiceRepository.findByClientIdOrderByIssuedAtDesc(clientId).stream()
                .map(mapper::toInvoiceResponse)
                .toList();
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
