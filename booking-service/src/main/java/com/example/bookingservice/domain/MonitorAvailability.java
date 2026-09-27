package com.example.bookingservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Créneau de travail hebdomadaire récurrent d'un moniteur (référencé par son identifiant Keycloak
 * {@code sub}). Les heures sont en horloge locale de l'auto-école (fuseau {@code Africa/Tunis}) —
 * ex. « lundi 09:00–12:00 ». Plusieurs lignes par jour permettent des plages coupées (matin / après-midi).
 *
 * <p>C'est la disponibilité <em>déclarée</em> du moniteur ; les créneaux réellement réservables sont
 * calculés en retranchant les séances réservées et les absences (voir MonitorAvailabilityService).
 */
@Entity
@Table(name = "monitor_availability")
public class MonitorAvailability extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "monitor_id", nullable = false, length = 255)
    private String monitorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getMonitorId() {
        return monitorId;
    }

    public void setMonitorId(String monitorId) {
        this.monitorId = monitorId;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(DayOfWeek dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }
}
