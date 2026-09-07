package com.example.financeservice.service;

import com.example.financeservice.domain.AuditEvent;
import com.example.financeservice.repository.AuditEventRepository;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Enregistre les événements d'audit métier. Appelé depuis les services (dans leur transaction) à
 * chaque création / modification / suppression. L'auteur provient du même {@link AuditorAware} que
 * les colonnes created_by / updated_by.
 */
@Service
public class AuditService {

    private final AuditEventRepository repository;
    private final AuditorAware<String> auditorAware;

    public AuditService(AuditEventRepository repository, AuditorAware<String> auditorAware) {
        this.repository = repository;
        this.auditorAware = auditorAware;
    }

    @Transactional
    public void record(String action, String entityType, UUID entityId, String summary) {
        AuditEvent event = new AuditEvent();
        event.setOccurredAt(Instant.now());
        event.setActor(auditorAware.getCurrentAuditor().orElse("system"));
        event.setAction(action);
        event.setEntityType(entityType);
        event.setEntityId(entityId == null ? null : entityId.toString());
        event.setSummary(summary);
        repository.save(event);
    }
}
