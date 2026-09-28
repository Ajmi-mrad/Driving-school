package com.example.aiservice.ops;

import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Bascule entre fournisseurs LLM : Groq en premier, Gemini (API compatible OpenAI) en secours si
 * sa clé est configurée. Chaque fournisseur garde ses propres retries (voir LlmRetryConfig) ; la
 * bascule n'a lieu qu'une fois ceux-ci épuisés (panne, quota journalier, clé invalide…).
 */
@Component
public class LlmFailover {

    private static final Logger log = LoggerFactory.getLogger(LlmFailover.class);

    record Provider(String name, ChatModel model) {
    }

    private final List<Provider> providers;

    @Autowired
    public LlmFailover(ChatModel groq,
                       @Value("${ai.gemini.api-key:}") String geminiApiKey,
                       @Value("${ai.gemini.base-url}") String geminiBaseUrl,
                       @Value("${ai.gemini.model}") String geminiModel,
                       RetryTemplate retryTemplate,
                       ResponseErrorHandler responseErrorHandler,
                       ToolCallingManager toolCallingManager,
                       ObservationRegistry observationRegistry,
                       RestClient.Builder restClientBuilder) {
        List<Provider> list = new ArrayList<>(List.of(new Provider("groq", groq)));
        if (!geminiApiKey.isBlank()) {
            OpenAiApi api = OpenAiApi.builder()
                    .baseUrl(geminiBaseUrl)
                    // Endpoint compatible OpenAI de Gemini : pas de préfixe /v1.
                    .completionsPath("/chat/completions")
                    .apiKey(geminiApiKey)
                    .responseErrorHandler(responseErrorHandler)
                    // Gemini 3 : thought signatures à renvoyer avec chaque appel d'outil.
                    .restClientBuilder(restClientBuilder.clone().requestInterceptor(new GeminiThoughtSignatures()))
                    .build();
            list.add(new Provider("gemini", OpenAiChatModel.builder()
                    .openAiApi(api)
                    // Température par défaut (1.0) : Google la recommande pour Gemini 3 (plus bas → boucles).
                    .defaultOptions(OpenAiChatOptions.builder().model(geminiModel).build())
                    .retryTemplate(retryTemplate)
                    .toolCallingManager(toolCallingManager)
                    .observationRegistry(observationRegistry)
                    .build()));
        }
        this.providers = List.copyOf(list);
        log.info("Fournisseurs LLM (ordre de bascule): {}", providers.stream().map(Provider::name).toList());
    }

    LlmFailover(List<Provider> providers) {
        this.providers = List.copyOf(providers);
    }

    /** Une session par investigation : après une bascule, on reste sur le secours jusqu'à la fin. */
    public Session session(BiConsumer<String, Object> events) {
        return new Session(events);
    }

    public final class Session {

        private final BiConsumer<String, Object> events;
        private final Map<String, String> thoughtSignatures = new HashMap<>();
        private int current;

        private Session(BiConsumer<String, Object> events) {
            this.events = events;
        }

        public ChatResponse call(Prompt prompt) {
            RuntimeException lastError = null;
            for (; current < providers.size(); current++) {
                Provider provider = providers.get(current);
                try {
                    return GeminiThoughtSignatures.withSignatures(thoughtSignatures,
                            () -> provider.model().call(prompt));
                } catch (RuntimeException e) {
                    lastError = e;
                    log.warn("LLM {} en échec: {}", provider.name(), e.getMessage());
                    if (current + 1 < providers.size()) {
                        events.accept("warning", Map.of("message", "LLM " + provider.name()
                                + " unavailable, switching to " + providers.get(current + 1).name()));
                    }
                }
            }
            throw lastError != null ? lastError : new IllegalStateException("No LLM provider left");
        }
    }
}
