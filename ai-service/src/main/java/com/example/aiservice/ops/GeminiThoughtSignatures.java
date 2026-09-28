package com.example.aiservice.ops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Gemini 3 (API compatible OpenAI) exige que chaque appel d'outil renvoyé dans l'historique porte sa
 * « thought signature » ({@code tool_calls[].extra_content.google.thought_signature}), champ que le
 * client OpenAI de Spring AI ignore. Cet intercepteur la mémorise à la réponse et la ré-attache à la
 * requête suivante. Un appel d'outil sans signature connue (fait par Groq avant une bascule) reçoit
 * la valeur de contournement documentée par Google.
 * <p>Les signatures appartiennent à une investigation ({@link #withSignatures}) : l'appel HTTP se fait
 * sur le thread de l'investigation, d'où un ThreadLocal, sans partage entre utilisateurs.
 */
final class GeminiThoughtSignatures implements ClientHttpRequestInterceptor {

    static final String PLACEHOLDER = "skip_thought_signature_validator";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ThreadLocal<Map<String, String>> CURRENT = new ThreadLocal<>();

    /** Exécute {@code call} avec les signatures (id d'appel d'outil → signature) de l'investigation. */
    static <T> T withSignatures(Map<String, String> signatures, Supplier<T> call) {
        CURRENT.set(signatures);
        try {
            return call.get();
        } finally {
            CURRENT.remove();
        }
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        ClientHttpResponse response = execution.execute(request, attachSignatures(body));
        if (!response.getStatusCode().is2xxSuccessful()) {
            return response;
        }
        byte[] responseBody = response.getBody().readAllBytes();
        rememberSignatures(responseBody);
        return new BufferedResponse(response, responseBody);
    }

    byte[] attachSignatures(byte[] body) {
        Map<String, String> signatures = CURRENT.get();
        try {
            JsonNode root = JSON.readTree(body);
            boolean changed = false;
            for (JsonNode message : root.path("messages")) {
                for (JsonNode call : message.path("tool_calls")) {
                    if (call instanceof ObjectNode toolCall && !toolCall.has("extra_content")) {
                        String signature = signatures == null ? PLACEHOLDER
                                : signatures.getOrDefault(toolCall.path("id").asText(), PLACEHOLDER);
                        toolCall.putObject("extra_content").putObject("google").put("thought_signature", signature);
                        changed = true;
                    }
                }
            }
            return changed ? JSON.writeValueAsBytes(root) : body;
        } catch (IOException e) {
            return body;                            // pas du JSON : requête envoyée telle quelle
        }
    }

    void rememberSignatures(byte[] responseBody) {
        Map<String, String> signatures = CURRENT.get();
        if (signatures == null) {
            return;
        }
        try {
            for (JsonNode choice : JSON.readTree(responseBody).path("choices")) {
                for (JsonNode call : choice.path("message").path("tool_calls")) {
                    JsonNode signature = call.path("extra_content").path("google").path("thought_signature");
                    if (signature.isTextual()) {
                        signatures.put(call.path("id").asText(), signature.asText());
                    }
                }
            }
        } catch (IOException e) {
            // Réponse non JSON : rien à mémoriser.
        }
    }

    /** Réponse dont le corps, déjà lu pour y chercher les signatures, reste lisible par Spring AI. */
    private record BufferedResponse(ClientHttpResponse delegate, byte[] body) implements ClientHttpResponse {

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
