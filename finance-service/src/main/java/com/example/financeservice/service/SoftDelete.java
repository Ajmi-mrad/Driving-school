package com.example.financeservice.service;

import com.example.financeservice.domain.Auditable;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Marque une entité comme supprimée logiquement, en traçant qui et quand.
 * L'auteur provient du même {@link AuditorAware} que created_by / updated_by.
 */
@Component
public class SoftDelete {

    private final AuditorAware<String> auditorAware;

    public SoftDelete(AuditorAware<String> auditorAware) {
        this.auditorAware = auditorAware;
    }

    public void mark(Auditable entity) {
        entity.setDeleted(true);
        entity.setDeletedAt(Instant.now());
        entity.setDeletedBy(auditorAware.getCurrentAuditor().orElse("system"));
    }
}