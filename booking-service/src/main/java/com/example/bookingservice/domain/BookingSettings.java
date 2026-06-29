package com.example.bookingservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Paramètres de planification de l'auto-école (ligne unique). Permet au propriétaire de configurer
 * la validation automatique des réservations et le préavis d'annulation, conformément à la règle
 * « validation manuelle ou automatique selon paramétrage » du cahier des charges.
 */
@Entity
@Table(name = "booking_settings")
public class BookingSettings extends Auditable {

    /** Identifiant fixe de la ligne unique de configuration. */
    public static final UUID SINGLETON_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "auto_validation_enabled", nullable = false)
    private boolean autoValidationEnabled;

    @Column(name = "cancellation_notice_hours", nullable = false)
    private int cancellationNoticeHours;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public boolean isAutoValidationEnabled() {
        return autoValidationEnabled;
    }

    public void setAutoValidationEnabled(boolean autoValidationEnabled) {
        this.autoValidationEnabled = autoValidationEnabled;
    }

    public int getCancellationNoticeHours() {
        return cancellationNoticeHours;
    }

    public void setCancellationNoticeHours(int cancellationNoticeHours) {
        this.cancellationNoticeHours = cancellationNoticeHours;
    }
}