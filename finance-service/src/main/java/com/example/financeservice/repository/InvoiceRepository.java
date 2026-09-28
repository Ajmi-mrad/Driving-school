package com.example.financeservice.repository;

import com.example.financeservice.domain.Invoice;
import com.example.financeservice.domain.InvoiceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    List<Invoice> findByClientIdOrderByIssuedAtDesc(String clientId);

    /** Tous les documents (vue staff sans filtre client), du plus récent au plus ancien. */
    List<Invoice> findAllByOrderByIssuedAtDesc();

    List<Invoice> findByEnrollmentIdOrderByIssuedAtDesc(UUID enrollmentId);

    /** Document le plus récent d'un type donné pour une inscription (p. ex. la facture à ajuster). */
    Optional<Invoice> findFirstByEnrollmentIdAndTypeOrderByIssuedAtDesc(UUID enrollmentId, InvoiceType type);

    /** Reçu vivant rattaché à un paiement (pour l'annuler quand le paiement est annulé). */
    Optional<Invoice> findByPaymentId(UUID paymentId);

    /** Prochain numéro de document via la séquence dédiée (unique, sans course concurrente). */
    @Query(value = "SELECT nextval('invoice_number_seq')", nativeQuery = true)
    long nextInvoiceNumber();
}
