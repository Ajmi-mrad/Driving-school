package com.example.financeservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Forfait : pack d'heures de conduite et/ou de séances de code vendu à l'élève, à un prix donné
 * (cahier des charges, glossaire). Un forfait est un catalogue ; l'achat concret est une
 * {@link Enrollment}.
 */
@Entity
@Table(name = "forfaits")
public class Forfait extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "driving_hours", nullable = false)
    private int drivingHours;

    @Column(name = "code_sessions", nullable = false)
    private int codeSessions;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getDrivingHours() {
        return drivingHours;
    }

    public void setDrivingHours(int drivingHours) {
        this.drivingHours = drivingHours;
    }

    public int getCodeSessions() {
        return codeSessions;
    }

    public void setCodeSessions(int codeSessions) {
        this.codeSessions = codeSessions;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
