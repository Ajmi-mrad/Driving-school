package com.example.bookingservice.repository;

import com.example.bookingservice.domain.Session;
import com.example.bookingservice.domain.SessionStatus;
import com.example.bookingservice.domain.SessionType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Prédicats de recherche de séances. Chaque filtre nul est simplement omis
 * (aucune comparaison « :param is null »), ce qui évite l'erreur PostgreSQL
 * « could not determine data type of parameter » sur un paramètre lié nul.
 */
public final class SessionSpecifications {

    private SessionSpecifications() {
    }

    public static Specification<Session> filter(SessionStatus status,
                                                SessionType type,
                                                Instant from,
                                                Instant to,
                                                String monitorId,
                                                String clientId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startTime"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("startTime"), to));
            }
            if (monitorId != null) {
                predicates.add(cb.equal(root.get("monitorId"), monitorId));
            }
            if (clientId != null) {
                predicates.add(cb.equal(root.get("clientId"), clientId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}