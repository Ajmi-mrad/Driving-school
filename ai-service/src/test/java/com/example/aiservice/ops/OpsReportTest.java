package com.example.aiservice.ops;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OpsReportTest {

    @Test
    void factsWithoutRealEvidenceAreDemotedToLowConfidenceHypotheses() {
        OpsReport raw = new OpsReport("summary",
                List.of(new OpsReport.Fact("booking is DOWN", List.of("t1", "t9")),
                        new OpsReport.Fact("DB pool exhausted", List.of("t7")),
                        new OpsReport.Fact("no evidence at all", null)),
                List.of(new OpsReport.Hypothesis("recent deploy", "medium")),
                null);

        OpsReport report = raw.verified(Set.of("t1", "t2"));

        assertThat(report.facts()).containsExactly(new OpsReport.Fact("booking is DOWN", List.of("t1")));
        assertThat(report.hypotheses()).containsExactly(
                new OpsReport.Hypothesis("recent deploy", "medium"),
                new OpsReport.Hypothesis("DB pool exhausted", "low"),
                new OpsReport.Hypothesis("no evidence at all", "low"));
        assertThat(report.actions()).isEmpty();
    }
}
