package com.example.financeservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Inscription d'un client à un {@link Forfait} : instantané du prix et des quotas au moment de
 * l'achat, suivi du solde payé et de la consommation restante (heures de conduite / séances de code).
 * Le client est référencé par son identifiant Keycloak ({@code sub}).
 */
@Entity
@Table(name = "enrollments")
public class Enrollment extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "client_id", nullable = false, length = 255)
    private String clientId;

    @Column(name = "forfait_id", nullable = false)
    private UUID forfaitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EnrollmentStatus status = EnrollmentStatus.ACTIVE;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "amount_paid", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(name = "remaining_driving_hours", nullable = false)
    private int remainingDrivingHours;

    @Column(name = "remaining_code_sessions", nullable = false)
    private int remainingCodeSessions;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;

    /** Reste à payer (jamais négatif). */
    public BigDecimal outstanding() {
        BigDecimal diff = totalPrice.subtract(amountPaid);
        return diff.signum() < 0 ? BigDecimal.ZERO : diff;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public UUID getForfaitId() {
        return forfaitId;
    }

    public void setForfaitId(UUID forfaitId) {
        this.forfaitId = forfaitId;
    }

    public EnrollmentStatus getStatus() {
        return status;
    }

    public void setStatus(EnrollmentStatus status) {
        this.status = status;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public void setAmountPaid(BigDecimal amountPaid) {
        this.amountPaid = amountPaid;
    }

    public int getRemainingDrivingHours() {
        return remainingDrivingHours;
    }

    public void setRemainingDrivingHours(int remainingDrivingHours) {
        this.remainingDrivingHours = remainingDrivingHours;
    }

    public int getRemainingCodeSessions() {
        return remainingCodeSessions;
    }

    public void setRemainingCodeSessions(int remainingCodeSessions) {
        this.remainingCodeSessions = remainingCodeSessions;
    }

    public Instant getEnrolledAt() {
        return enrolledAt;
    }

    public void setEnrolledAt(Instant enrolledAt) {
        this.enrolledAt = enrolledAt;
    }
}
