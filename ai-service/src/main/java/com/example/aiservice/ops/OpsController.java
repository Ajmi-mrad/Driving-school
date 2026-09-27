package com.example.aiservice.ops;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Point d'entrée de l'assistant Ops. Réponse en Server-Sent Events :
 * {@code warning}, {@code tool_call}, {@code tool_result} pendant l'investigation, puis
 * {@code final} (le {@link OpsReport}) ou {@code error}. Réservé au propriétaire.
 */
@RestController
@RequestMapping("/api/ai/ops")
public class OpsController {

    private static final Logger log = LoggerFactory.getLogger(OpsController.class);
    // Large : les retries sur 429 (quota de tokens Groq) peuvent étirer une investigation.
    private static final long TIMEOUT_MS = Duration.ofMinutes(5).toMillis();

    private final OpsAgent agent;
    private final TaskExecutor executor;

    public OpsController(OpsAgent agent, TaskExecutor executor) {
        this.agent = agent;
        this.executor = executor;
    }

    public record AskRequest(@NotBlank @Size(max = 1000) String question) {
    }

    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasRole('OWNER')")
    public SseEmitter ask(@Valid @RequestBody AskRequest request) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        // Timeout SSE, onglet fermé ou erreur réseau : l'investigation doit s'arrêter au prochain
        // événement au lieu de continuer à consommer des tokens pour personne.
        AtomicBoolean closed = new AtomicBoolean();
        emitter.onTimeout(() -> closed.set(true));
        emitter.onError(e -> closed.set(true));
        emitter.onCompletion(() -> closed.set(true));

        executor.execute(() -> {
            try {
                OpsReport report = agent.investigate(request.question(),
                        (name, data) -> send(emitter, closed, name, data));
                send(emitter, closed, "final", report);
            } catch (CancellationException | UncheckedIOException e) {
                // Cas normal (client parti, timeout) : pas un ERROR, sinon l'agent lirait ces
                // traces dans Loki et les signalerait comme des pannes de l'ai-service.
                log.debug("Investigation abandonnée: {}", e.getMessage());
            } catch (RuntimeException e) {
                log.error("Investigation échouée", e);
                trySend(emitter, closed, "error", Map.of("message", "Investigation failed: " + e.getMessage()));
            } finally {
                emitter.complete();
            }
        });
        return emitter;
    }

    /** Un envoi impossible (flux fermé, client déconnecté) interrompt l'investigation. */
    private static void send(SseEmitter emitter, AtomicBoolean closed, String name, Object data) {
        if (closed.get()) {
            throw new CancellationException("SSE stream closed (client gone or timeout)");
        }
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            closed.set(true);
            throw new UncheckedIOException(e);
        } catch (IllegalStateException e) {
            // Émetteur déjà complété (course avec le timeout).
            closed.set(true);
            throw new CancellationException(e.getMessage());
        }
    }

    private static void trySend(SseEmitter emitter, AtomicBoolean closed, String name, Object data) {
        try {
            send(emitter, closed, name, data);
        } catch (RuntimeException ignored) {
            // Client déjà parti : rien à notifier.
        }
    }
}
