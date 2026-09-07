package com.example.financeservice.repository;

import com.example.financeservice.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByClientIdOrderByPaidAtDesc(String clientId);

    List<Payment> findByEnrollmentIdOrderByPaidAtDesc(UUID enrollmentId);

    /** Nombre de paiements vivants (non annulés) d'une inscription — @SQLRestriction filtre les annulés. */
    long countByEnrollmentId(UUID enrollmentId);
}
