package com.example.aiservice.ops;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceHealthToolTest {

    @Test
    void describesTheRunningBuild() {
        assertThat(ServiceHealthTool.describeBuild(Map.of("build",
                Map.of("commit", "e6183148cae4527c", "time", "2026-07-21T10:00:00Z"))))
                .isEqualTo(", build commit=e618314 built=2026-07-21T10:00:00Z");
        assertThat(ServiceHealthTool.describeBuild(Map.of("build", Map.of("commit", "local", "time", "t"))))
                .isEqualTo(", build commit=local built=t");
        // Service sans build-info (image antérieure) : pas d'invention.
        assertThat(ServiceHealthTool.describeBuild(Map.of())).isEqualTo(", build unknown");
    }
}
