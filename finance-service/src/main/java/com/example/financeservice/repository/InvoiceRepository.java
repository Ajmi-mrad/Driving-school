package com.example.financeservice.repository;

import com.example.financeservice.domain.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    List<Invoice> findByClientIdOrderByIssuedAtDesc(String clientId);

    List<Invoice> findByEnrollmentIdOrderByIssuedAtDesc(UUID enrollmentId);

    /** Prochain numéro de document via la séquence dédiée (unique, sans course concurrente). */
    @Query(value = "SELECT nextval('invoice_number_seq')", nativeQuery = true)
    long nextInvoiceNumber();
}
