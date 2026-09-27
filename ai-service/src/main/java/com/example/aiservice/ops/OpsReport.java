package com.example.aiservice.ops;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Rapport final d'une investigation. Sépare strictement les faits observés (chacun appuyé par au
 * moins un appel d'outil), les hypothèses de l'IA et les actions recommandées à un humain.
 */
public record OpsReport(String summary, List<Fact> facts, List<Hypothesis> hypotheses, List<String> actions) {

    /** Fait observé ; {@code evidence} = identifiants des appels d'outils qui le prouvent (ex. "t2"). */
    public record Fact(String statement, List<String> evidence) {
    }

    /** Hypothèse de l'IA ; {@code confidence} = low | medium | high. */
    public record Hypothesis(String statement, String confidence) {
    }

    /**
     * Garde-fou anti-hallucination : ne conserve que les preuves correspondant à un appel d'outil
     * réellement exécuté. Un « fait » sans preuve valide est rétrogradé en hypothèse de confiance low.
     */
    public OpsReport verified(Set<String> toolCallIds) {
        List<Fact> keptFacts = new ArrayList<>();
        List<Hypothesis> allHypotheses = new ArrayList<>(orEmpty(hypotheses));
        for (Fact fact : orEmpty(facts)) {
            List<String> realEvidence = orEmpty(fact.evidence()).stream().filter(toolCallIds::contains).toList();
            if (realEvidence.isEmpty()) {
                allHypotheses.add(new Hypothesis(fact.statement(), "low"));
            } else {
                keptFacts.add(new Fact(fact.statement(), realEvidence));
            }
        }
        return new OpsReport(Objects.requireNonNullElse(summary, ""), keptFacts, allHypotheses, orEmpty(actions));
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
