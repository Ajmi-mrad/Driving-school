package com.example.aiservice.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmRetryConfigTest {

    private static final String PER_MINUTE = "Rate limit reached on tokens per minute (TPM): Limit 8000";
    private static final String PER_DAY = "Rate limit reached on tokens per day (TPD): Limit 200000";

    @Test
    void onlyRateLimitsServerErrorsAndGenerationGlitchesAreRetried() {
        assertThat(LlmRetryConfig.isTransient(429, PER_MINUTE)).isTrue();
        assertThat(LlmRetryConfig.isTransient(503, "")).isTrue();
        assertThat(LlmRetryConfig.isTransient(400, "{\"code\":\"output_parse_failed\"}")).isTrue();
        assertThat(LlmRetryConfig.isTransient(400, "{\"code\":\"tool_use_failed\"}")).isTrue();
        assertThat(LlmRetryConfig.isTransient(400, "{\"code\":\"invalid_request\"}")).isFalse();
        assertThat(LlmRetryConfig.isTransient(401, "invalid api key")).isFalse();
    }

    @Test
    void dailyQuotaFailsFastInsteadOfBurningRetries() {
        assertThat(LlmRetryConfig.isTransient(429, PER_DAY)).isFalse();
        assertThat(LlmRetryConfig.isTransient(429,
                "{\"quotaId\": \"GenerateRequestsPerDayPerProjectPerModel-FreeTier\"}")).isFalse();
    }

    @Test
    void waitHonoursRetryAfterOtherwiseBacksOffExponentially() {
        assertThat(LlmRetryConfig.retryAfterMillis("7")).isEqualTo(7_000);
        assertThat(LlmRetryConfig.retryAfterMillis("0.2")).isEqualTo(1_000);        // borné en bas
        assertThat(LlmRetryConfig.retryAfterMillis("3600")).isEqualTo(LlmRetryConfig.MAX_WAIT_MS);
        assertThat(LlmRetryConfig.retryAfterMillis(null)).isEqualTo(5_000);
        assertThat(LlmRetryConfig.retryAfterMillis("soon")).isEqualTo(5_000);

        assertThat(LlmRetryConfig.retryDelayFromBody("{\"@type\": \"...RetryInfo\", \"retryDelay\": \"37s\"}"))
                .isEqualTo("37");
        assertThat(LlmRetryConfig.retryAfterMillis(LlmRetryConfig.retryDelayFromBody("\"retryDelay\": \"37s\"")))
                .isEqualTo(37_000);
        assertThat(LlmRetryConfig.retryDelayFromBody("{\"error\": \"quota\"}")).isNull();

        var rateLimited = new LlmRetryConfig.RateLimitedException("429", 7_000);
        assertThat(LlmRetryConfig.waitMillis(rateLimited, 3)).isEqualTo(7_000);
        assertThat(LlmRetryConfig.waitMillis(new RuntimeException(), 0)).isEqualTo(2_000);
        assertThat(LlmRetryConfig.waitMillis(new RuntimeException(), 2)).isEqualTo(8_000);
        assertThat(LlmRetryConfig.waitMillis(new RuntimeException(), 9)).isEqualTo(LlmRetryConfig.MAX_WAIT_MS);
    }
}
