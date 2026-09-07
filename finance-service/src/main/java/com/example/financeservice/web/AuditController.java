package com.example.financeservice.web;

import com.example.financeservice.domain.AuditEvent;
import com.example.financeservice.repository.AuditEventRepository;
import com.example.financeservice.web.dto.AuditEventResponse;
import org.springframework.data.domain.Limit;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Journal d'audit finance : consultation des événements (création / modification / annulation)
 * réservée au propriétaire. En lecture seule ; les événements sont immuables.
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private static final int MAX_LIMIT = 500;

    private final AuditEventRepository repository;

    public AuditController(AuditEventRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @PreAuthorize("hasRole('OWNER')")
    public List<AuditEventResponse> list(@RequestParam(required = false) String action,
                                         @RequestParam(required = false) String entityType,
                                         @RequestParam(defaultValue = "200") int limit) {
        int capped = Math.min(Math.max(limit, 1), MAX_LIMIT);
        return repository.findRecent(action, entityType, Limit.of(capped)).stream()
                .map(this::toResponse)
                .toList();
    }

    private AuditEventResponse toResponse(AuditEvent e) {
        return new AuditEventResponse(e.getId(), e.getOccurredAt(), e.getActor(),
                e.getAction(), e.getEntityType(), e.getEntityId(), e.getSummary());
    }
}
