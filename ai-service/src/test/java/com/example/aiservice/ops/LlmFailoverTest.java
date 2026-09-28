package com.example.aiservice.ops;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmFailoverTest {

    private static ChatModel answering(String text, AtomicInteger calls) {
        return prompt -> {
            calls.incrementAndGet();
            return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
        };
    }

    private static ChatModel failing(AtomicInteger calls) {
        return prompt -> {
            calls.incrementAndGet();
            throw new IllegalStateException("groq down");
        };
    }

    private static String text(ChatResponse response) {
        return response.getResult().getOutput().getText();
    }

    @Test
    void switchesToFallbackOnFailureAndStaysThereForTheRestOfTheInvestigation() {
        AtomicInteger groqCalls = new AtomicInteger();
        AtomicInteger geminiCalls = new AtomicInteger();
        List<Object> warnings = new ArrayList<>();
        LlmFailover failover = new LlmFailover(List.of(
                new LlmFailover.Provider("groq", failing(groqCalls)),
                new LlmFailover.Provider("gemini", answering("from gemini", geminiCalls))));

        LlmFailover.Session session = failover.session((name, data) -> warnings.add(data));
        assertThat(text(session.call(new Prompt("round 1")))).isEqualTo("from gemini");
        assertThat(text(session.call(new Prompt("round 2")))).isEqualTo("from gemini");

        assertThat(groqCalls).hasValue(1);   // pas de retour vers le fournisseur en panne
        assertThat(geminiCalls).hasValue(2);
        assertThat(warnings).hasSize(1);

        // Nouvelle investigation : on retente le fournisseur principal.
        failover.session((name, data) -> { }).call(new Prompt("next"));
        assertThat(groqCalls).hasValue(2);
    }

    @Test
    void primaryIsUsedWhenHealthyAndTheLastErrorSurfacesWhenAllFail() {
        AtomicInteger groqCalls = new AtomicInteger();
        LlmFailover healthy = new LlmFailover(List.of(new LlmFailover.Provider("groq", answering("ok", groqCalls))));
        assertThat(text(healthy.session((n, d) -> { }).call(new Prompt("q")))).isEqualTo("ok");

        LlmFailover allDown = new LlmFailover(List.of(
                new LlmFailover.Provider("groq", failing(new AtomicInteger())),
                new LlmFailover.Provider("gemini", failing(new AtomicInteger()))));
        assertThatThrownBy(() -> allDown.session((n, d) -> { }).call(new Prompt("q")))
                .hasMessage("groq down");
    }
}
