package com.example.financeservice.dev;

import com.example.financeservice.dev.dto.FinanceExport;
import com.example.financeservice.dev.dto.FinanceSeedRequest;
import com.example.financeservice.dev.dto.SeedResult;
import com.example.financeservice.domain.Enrollment;
import com.example.financeservice.domain.EnrollmentStatus;
import com.example.financeservice.domain.Forfait;
import com.example.financeservice.domain.Invoice;
import com.example.financeservice.domain.InvoiceType;
import com.example.financeservice.domain.Payment;
import com.example.financeservice.domain.PaymentMethod;
import com.example.financeservice.mapper.FinanceMapper;
import com.example.financeservice.repository.EnrollmentRepository;
import com.example.financeservice.repository.ForfaitRepository;
import com.example.financeservice.repository.InvoiceRepository;
import com.example.financeservice.repository.PaymentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Outil de développement (activé par {@code app.dev-tools.enabled=true}) : peuplement et remise à
 * zéro des données financières. Jeu de données aligné sur le mock frontend (mock/db.ts). Les
 * {@code client_id} proviennent de la carte {@code seedKey -> sub} renvoyée par le seed auth.
 */
@Service
@ConditionalOnProperty(name = "app.dev-tools.enabled", havingValue = "true")
public class DevDataService {

    private static final Logger log = LoggerFactory.getLogger(DevDataService.class);

    private final ForfaitRepository forfaitRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final FinanceMapper financeMapper;

    @PersistenceContext
    private EntityManager entityManager;

    public DevDataService(ForfaitRepository forfaitRepository, EnrollmentRepository enrollmentRepository,
                          PaymentRepository paymentRepository, InvoiceRepository invoiceRepository,
                          FinanceMapper financeMapper) {
        this.forfaitRepository = forfaitRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.financeMapper = financeMapper;
    }

    @Transactional
    public SeedResult seed(FinanceSeedRequest request) {
        // Données interdépendantes : seed tout-ou-rien pour rester idempotent et cohérent.
        if (forfaitRepository.count() > 0) {
            return new SeedResult(0, (int) totalRows());
        }
        Map<String, String> users = request == null ? Map.of() : request.users();
        Instant now = Instant.now();

        Forfait f1 = forfait("Forfait Permis B - 20h",
                "Formation complete 20h de conduite + code illimite", 20, 30, "1290.00", true);
        Forfait f2 = forfait("Forfait Accelere - 30h",
                "Formation intensive 30h de conduite + code illimite", 30, 30, "1790.00", true);
        forfait("Forfait Code seul", "Acces au code de la route uniquement", 0, 20, "290.00", true);
        forfait("Pack Perfectionnement - 10h", "10h de conduite supplementaires", 10, 0, "650.00", false);

        Enrollment e1 = enrollment(now, sub(users, "u-cli-1"), f1.getId(), EnrollmentStatus.ACTIVE,
                "1290.00", "800.00", 12, 30, 60);
        Enrollment e2 = enrollment(now, sub(users, "u-cli-2"), f2.getId(), EnrollmentStatus.ACTIVE,
                "1790.00", "1790.00", 26, 28, 20);
        Enrollment e3 = enrollment(now, sub(users, "u-cli-3"), f1.getId(), EnrollmentStatus.ACTIVE,
                "1290.00", "300.00", 20, 25, 10);

        payment(now, e1.getId(), sub(users, "u-cli-1"), "500.00", PaymentMethod.CARD, "CB-8842", 60);
        payment(now, e1.getId(), sub(users, "u-cli-1"), "300.00", PaymentMethod.TRANSFER, "VIR-1123", 20);
        payment(now, e2.getId(), sub(users, "u-cli-2"), "1790.00", PaymentMethod.CARD, "CB-9910", 20);
        payment(now, e3.getId(), sub(users, "u-cli-3"), "300.00", PaymentMethod.CASH, null, 10);

        invoice(now, e1.getId(), sub(users, "u-cli-1"), "FAC-2026-0001", InvoiceType.INVOICE, "1290.00", 60);
        invoice(now, e1.getId(), sub(users, "u-cli-1"), "REC-2026-0001", InvoiceType.RECEIPT, "500.00", 60);
        invoice(now, e2.getId(), sub(users, "u-cli-2"), "FAC-2026-0002", InvoiceType.INVOICE, "1790.00", 20);
        invoice(now, e2.getId(), sub(users, "u-cli-2"), "REC-2026-0002", InvoiceType.RECEIPT, "1790.00", 20);

        int created = (int) totalRows();
        log.info("Seed finance: {} entités (forfaits, inscriptions, paiements, factures)", created);
        return new SeedResult(created, 0);
    }

    /** Nombre total de lignes financières vivantes (forfaits + inscriptions + paiements + factures). */
    private long totalRows() {
        return forfaitRepository.count() + enrollmentRepository.count()
                + paymentRepository.count() + invoiceRepository.count();
    }

    @Transactional
    public void reset() {
        // TRUNCATE physique (natif) : contourne @SQLRestriction (les DELETE JPA laisseraient les
        // lignes en soft-delete). CASCADE couvre les FK invoices/payments/enrollments/forfaits.
        entityManager.createNativeQuery(
                "TRUNCATE TABLE invoices, payments, enrollments, forfaits, audit_event "
                        + "RESTART IDENTITY CASCADE").executeUpdate();
        // Remet la numérotation des factures à zéro : le prochain nextval() vaut 1.
        entityManager.createNativeQuery("SELECT setval('invoice_number_seq', 1, false)").getSingleResult();
        log.info("Reset finance: forfaits/inscriptions/paiements/factures/audit vidés, séquence réinitialisée");
    }

    @Transactional(readOnly = true)
    public FinanceExport export() {
        return new FinanceExport(
                forfaitRepository.findAll().stream().map(financeMapper::toForfaitResponse).toList(),
                enrollmentRepository.findAll().stream().map(financeMapper::toEnrollmentResponse).toList(),
                paymentRepository.findAll().stream().map(financeMapper::toPaymentResponse).toList(),
                invoiceRepository.findAll().stream().map(financeMapper::toInvoiceResponse).toList());
    }

    // -- helpers ------------------------------------------------------------

    private static String sub(Map<String, String> users, String key) {
        String sub = users.get(key);
        if (sub == null || sub.isBlank()) {
            throw new IllegalArgumentException(
                    "Seed finance : identifiant Keycloak manquant pour " + key
                            + " (le seed auth doit être exécuté en premier)");
        }
        return sub;
    }

    private Forfait forfait(String name, String description, int drivingHours, int codeSessions,
                            String price, boolean active) {
        Forfait f = new Forfait();
        f.setName(name);
        f.setDescription(description);
        f.setDrivingHours(drivingHours);
        f.setCodeSessions(codeSessions);
        f.setPrice(new BigDecimal(price));
        f.setActive(active);
        return forfaitRepository.save(f);
    }

    private Enrollment enrollment(Instant now, String clientId, java.util.UUID forfaitId, EnrollmentStatus status,
                                  String totalPrice, String amountPaid, int remainingDriving,
                                  int remainingCode, long enrolledDaysAgo) {
        Enrollment e = new Enrollment();
        e.setClientId(clientId);
        e.setForfaitId(forfaitId);
        e.setStatus(status);
        e.setTotalPrice(new BigDecimal(totalPrice));
        e.setAmountPaid(new BigDecimal(amountPaid));
        e.setRemainingDrivingHours(remainingDriving);
        e.setRemainingCodeSessions(remainingCode);
        e.setEnrolledAt(now.minus(Duration.ofDays(enrolledDaysAgo)));
        return enrollmentRepository.save(e);
    }

    private void payment(Instant now, java.util.UUID enrollmentId, String clientId, String amount,
                         PaymentMethod method, String reference, long paidDaysAgo) {
        Payment p = new Payment();
        p.setEnrollmentId(enrollmentId);
        p.setClientId(clientId);
        p.setAmount(new BigDecimal(amount));
        p.setMethod(method);
        p.setReference(reference);
        p.setPaidAt(now.minus(Duration.ofDays(paidDaysAgo)));
        paymentRepository.save(p);
    }

    private void invoice(Instant now, java.util.UUID enrollmentId, String clientId, String number, InvoiceType type,
                         String amount, long issuedDaysAgo) {
        Invoice i = new Invoice();
        i.setEnrollmentId(enrollmentId);
        i.setClientId(clientId);
        i.setNumber(number);
        i.setType(type);
        i.setAmount(new BigDecimal(amount));
        i.setIssuedAt(now.minus(Duration.ofDays(issuedDaysAgo)));
        invoiceRepository.save(i);
    }
}
