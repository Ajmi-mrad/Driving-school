package com.example.aiservice.config;

import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.retry.RetryContext;
import org.springframework.retry.backoff.BackOffContext;
import org.springframework.retry.backoff.BackOffInterruptedException;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.ResponseErrorHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Politique d'erreurs et de retry des appels LLM (Groq). Remplace les beans par défaut de Spring AI
 * (les propriétés {@code spring.ai.retry.*} ne s'appliquent donc plus) :
 * <ul>
 *   <li>429 quota par minute : on attend le délai annoncé (en-tête {@code Retry-After} chez Groq,
 *       {@code retryDelay} dans le corps chez Gemini), puis on réessaie ;</li>
 *   <li>429 quota journalier : échec immédiat (inutile d'attendre des heures) ;</li>
 *   <li>5xx et échecs ponctuels de génération de gpt-oss (400 {@code output_parse_failed},
 *       {@code tool_use_failed}) : retry avec backoff exponentiel ;</li>
 *   <li>erreurs réseau (timeout, connexion coupée) : retry, comme la politique par défaut de Spring AI ;</li>
 *   <li>tout le reste (clé invalide, requête mal formée) : échec immédiat.</li>
 * </ul>
 */
@Configuration
public class LlmRetryConfig {

    // 4 essais (et non plus) : avec un fournisseur de secours, mieux vaut basculer vite.
    static final int MAX_ATTEMPTS = 4;
    // Une fenêtre de quota « par minute » dure au plus 60s.
    static final long MAX_WAIT_MS = 60_000;
    private static final Pattern RETRY_DELAY = Pattern.compile("\"retryDelay\"\\s*:\\s*\"([0-9.]+)s\"");
    private static final long DEFAULT_RETRY_AFTER_MS = 5_000;

    @Bean
    ResponseErrorHandler responseErrorHandler() {
        return new ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) throws IOException {
                return response.getStatusCode().isError();
            }

            @Override
            public void handleError(ClientHttpResponse response) throws IOException {
                int status = response.getStatusCode().value();
                String body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
                String message = "HTTP " + status + " - " + body;
                if (status == 429 && !isDailyQuota(body)) {
                    String retryAfter = response.getHeaders().getFirst("retry-after");
                    throw new RateLimitedException(message,
                            retryAfterMillis(retryAfter != null ? retryAfter : retryDelayFromBody(body)));
                }
                if (isTransient(status, body)) {
                    throw new TransientAiException(message);
                }
                throw new NonTransientAiException(message);
            }
        };
    }

    @Bean
    RetryTemplate retryTemplate() {
        return RetryTemplate.builder()
                .maxAttempts(MAX_ATTEMPTS)
                .retryOn(List.of(TransientAiException.class, ResourceAccessException.class))
                .customBackoff(new RetryAfterBackOffPolicy())
                .build();
    }

    static boolean isTransient(int status, String body) {
        return status >= 500
                || (status == 429 && !isDailyQuota(body))
                || (status == 400 && (body.contains("output_parse_failed") || body.contains("tool_use_failed")));
    }

    /**
     * Quota journalier : Groq « … tokens per day (TPD) » / « requests per day (RPD) » ;
     * Gemini « GenerateRequestsPerDayPerProjectPerModel-FreeTier ».
     */
    static boolean isDailyQuota(String body) {
        return body.contains("per day") || body.contains("PerDay");
    }

    /** Gemini : {@code "retryDelay": "37s"} (RetryInfo) dans le corps du 429 ; null si absent. */
    static String retryDelayFromBody(String body) {
        Matcher matcher = RETRY_DELAY.matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** {@code Retry-After} en secondes (décimales tolérées), borné ; absent ou illisible → défaut. */
    static long retryAfterMillis(String header) {
        if (header == null || header.isBlank()) {
            return DEFAULT_RETRY_AFTER_MS;
        }
        try {
            long millis = (long) (Double.parseDouble(header.trim()) * 1000);
            return Math.max(1_000, Math.min(millis, MAX_WAIT_MS));
        } catch (NumberFormatException e) {
            return DEFAULT_RETRY_AFTER_MS;
        }
    }

    /** Attente avant le prochain essai : le Retry-After du 429, sinon backoff exponentiel (2s, 4s, 8s…). */
    static long waitMillis(Throwable lastError, int attempt) {
        if (lastError instanceof RateLimitedException rateLimited) {
            return rateLimited.retryAfterMillis;
        }
        return Math.min(2_000L << Math.min(attempt, 10), MAX_WAIT_MS);
    }

    /** 429 « par minute » : transitoire, avec le délai annoncé par le serveur. */
    static final class RateLimitedException extends TransientAiException {

        final long retryAfterMillis;

        RateLimitedException(String message, long retryAfterMillis) {
            super(message);
            this.retryAfterMillis = retryAfterMillis;
        }
    }

    private static final class RetryAfterBackOffPolicy implements BackOffPolicy {

        private record Context(RetryContext retry, AtomicInteger attempt) implements BackOffContext {
        }

        @Override
        public BackOffContext start(RetryContext context) {
            return new Context(context, new AtomicInteger());
        }

        @Override
        public void backOff(BackOffContext backOffContext) throws BackOffInterruptedException {
            Context context = (Context) backOffContext;
            try {
                Thread.sleep(waitMillis(context.retry().getLastThrowable(), context.attempt().getAndIncrement()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BackOffInterruptedException("Retry LLM interrompu", e);
            }
        }
    }
}
