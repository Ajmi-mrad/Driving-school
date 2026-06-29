package com.example.communicationservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * Conversation 1:1 entre un moniteur et un élève. Les deux participants sont référencés par leur
 * identifiant Keycloak ({@code sub}). Une seule conversation par paire (moniteur, élève) — d'où la
 * contrainte d'unicité. {@code lastMessageAt}/{@code lastMessagePreview} servent à trier et à
 * afficher l'« inbox » sans recharger les messages.
 */
@Entity
@Table(name = "conversations",
        uniqueConstraints = @UniqueConstraint(name = "uq_conversation_pair", columnNames = {"monitor_id", "client_id"}))
public class Conversation extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "monitor_id", nullable = false, length = 255)
    private String monitorId;

    @Column(name = "client_id", nullable = false, length = 255)
    private String clientId;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "last_message_preview", length = 500)
    private String lastMessagePreview;

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

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }

    public void setLastMessageAt(Instant lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }

    public String getLastMessagePreview() {
        return lastMessagePreview;
    }

    public void setLastMessagePreview(String lastMessagePreview) {
        this.lastMessagePreview = lastMessagePreview;
    }
}