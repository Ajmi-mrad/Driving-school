package com.example.bookingservice.repository;

import com.example.bookingservice.domain.Exam;
import com.example.bookingservice.domain.ExamStatus;
import com.example.bookingservice.domain.ExamType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Prédicats de recherche d'examens. Chaque filtre nul est simplement omis (aucune comparaison
 * « :param is null »), ce qui évite l'erreur PostgreSQL « could not determine data type of
 * parameter » sur un paramètre lié nul. Voir {@link SessionSpecifications}.
 */
public final class ExamSpecifications {

    private ExamSpecifications() {
    }

    public static Specification<Exam> filter(ExamStatus status,
                                             ExamType type,
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
                predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("scheduledAt"), to));
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
