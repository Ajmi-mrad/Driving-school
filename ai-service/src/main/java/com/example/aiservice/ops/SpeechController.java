package com.example.aiservice.ops;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

/**
 * Lecture à voix haute du rapport Ops via Murf Falcon (TTS). La clé Murf reste côté serveur ;
 * réservé au propriétaire, comme l'assistant. Clé vide = fonctionnalité désactivée (503).
 */
@RestController
@RequestMapping("/api/ai/ops")
public class SpeechController {

    private static final Logger log = LoggerFactory.getLogger(SpeechController.class);
    private static final MediaType AUDIO_MPEG = MediaType.parseMediaType("audio/mpeg");
    /** Langue de l'UI → locale Murf. Langue absente = locale par défaut de la voix. */
    private static final Map<String, String> LOCALES = Map.of("fr", "fr-FR", "en", "en-US");

    private final RestClient http;
    private final String apiKey;
    private final String url;
    private final String voiceId;

    @Autowired
    public SpeechController(RestClient.Builder builder,
                            @Value("${ai.murf.api-key:}") String apiKey,
                            @Value("${ai.murf.url}") String url,
                            @Value("${ai.murf.voice-id}") String voiceId) {
        this(builder.requestFactory(timeouts()).build(), apiKey, url, voiceId);
    }

    SpeechController(RestClient http, String apiKey, String url, String voiceId) {
        this.http = http;
        this.apiKey = apiKey;
        this.url = url;
        this.voiceId = voiceId;
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(30000);
        return requestFactory;
    }

    /** {@code language} = code de langue de l'UI (fr, en, ar…) ; la taille max borne le coût. */
    public record SpeakRequest(@NotBlank @Size(max = 3000) String text, String language) {
    }

    // ponytail: renvoie le MP3 entier (quelques secondes pour résumé + actions) ;
    // passer à StreamingResponseBody si des rapports longs tardent à démarrer.
    @PostMapping("/speak")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<byte[]> speak(@Valid @RequestBody SpeakRequest request) {
        if (apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "TTS not configured (MURF_API_KEY)");
        }
        Map<String, Object> body = new HashMap<>(Map.of(
                "text", request.text(), "voiceId", voiceId, "model", "FALCON", "format", "MP3"));
        String locale = request.language() == null ? null : LOCALES.get(request.language().split("-")[0]);
        if (locale != null) {
            body.put("locale", locale);
        }
        try {
            byte[] audio = http.post().uri(url)
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(byte[].class);
            return ResponseEntity.ok().contentType(AUDIO_MPEG).body(audio);
        } catch (RuntimeException e) {
            // WARN, pas ERROR : l'agent lit Loki et signalerait une panne de l'ai-service.
            log.warn("Murf TTS failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TTS failed", e);
        }
    }
}
