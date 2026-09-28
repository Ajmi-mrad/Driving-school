package com.example.bookingservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Examen officiel (code ou conduite) passé par un élève. Les acteurs (élève, moniteur
 * accompagnateur) sont référencés par leur identifiant Keycloak ({@code sub}) ; le véhicule par son
 * UUID dans le vehicle-service. Contrairement à une {@link Session}, un examen est planifié par le
 * staff uniquement et se résout par un résultat (réussite / échec / absence).
 */
@Entity
@Table(name = "exams")
public class Exam extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ExamType type;

    @Column(name = "client_id", nullable = false, length = 255)
    private String clientId;

    /** Moniteur accompagnateur/examinateur (facultatif, surtout pour la conduite). */
    @Column(name = "monitor_id", length = 255)
    private String monitorId;

    @Column(name = "vehicle_id")
    private UUID vehicleId;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    /** Centre / lieu de l'examen (facultatif). */
    @Column(name = "location", length = 255)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExamStatus status = ExamStatus.SCHEDULED;

    /** Numéro de tentative de l'élève pour ce type d'examen (1 pour la première). */
    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    /** Remarque de l'examinateur saisie à l'enregistrement du résultat. */
    @Column(name = "result_note", length = 1000)
    private String resultNote;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ExamType getType() {
        return type;
    }

    public void setType(ExamType type) {
        this.type = type;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getMonitorId() {
        return monitorId;
    }

    public void setMonitorId(String monitorId) {
        this.monitorId = monitorId;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(UUID vehicleId) {
        this.vehicleId = vehicleId;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(Instant scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public ExamStatus getStatus() {
        return status;
    }

    public void setStatus(ExamStatus status) {
        this.status = status;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(int attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public String getResultNote() {
        return resultNote;
    }

    public void setResultNote(String resultNote) {
        this.resultNote = resultNote;
    }
}
