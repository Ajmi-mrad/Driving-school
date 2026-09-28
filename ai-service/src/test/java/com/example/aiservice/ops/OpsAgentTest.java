package com.example.aiservice.ops;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.client.RestClient;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Boucle d'outils de l'agent, avec un LLM scripté (aucun appel réseau). */
class OpsAgentTest {

    private static final String REPORT = """
            {"summary":"booking down","facts":[%s],"hypotheses":[],"actions":["check logs"]}""";

    // Eureka vide : service_health répond « NOT_REGISTERED » sans sonder le réseau.
    private final ServiceHealthTool serviceHealth = new ServiceHealthTool(new DiscoveryClient() {
        @Override
        public String description() {
            return "empty";
        }

        @Override
        public List<ServiceInstance> getInstances(String serviceId) {
            return List.of();
        }

        @Override
        public List<String> getServices() {
            return List.of();
        }
    }, RestClient.builder());

    private final List<String> events = new ArrayList<>();
    /** Réponses d'outils reçues par le modèle à chaque tour (dernier message de l'historique). */
    private final List<List<ToolResponseMessage.ToolResponse>> seen = new ArrayList<>();
    private int llmCalls;

    @AfterEach
    void shutdown() {
        serviceHealth.shutdown();
    }

    private static AssistantMessage.ToolCall call(String id, String name, String arguments) {
        return new AssistantMessage.ToolCall(id, "function", name, arguments);
    }

    private static AssistantMessage calls(AssistantMessage.ToolCall... toolCalls) {
        return AssistantMessage.builder().content("").toolCalls(List.of(toolCalls)).build();
    }

    /** LLM qui rejoue {@code script} ; une fois épuisé, répète la dernière réponse. */
    private OpsAgent agent(AssistantMessage... script) {
        Deque<AssistantMessage> answers = new ArrayDeque<>(List.of(script));
        ChatModel model = prompt -> {
            llmCalls++;
            List<Message> history = prompt.getInstructions();
            if (history.get(history.size() - 1) instanceof ToolResponseMessage responses) {
                seen.add(List.copyOf(responses.getResponses()));
            }
            AssistantMessage answer = answers.size() > 1 ? answers.poll() : answers.peek();
            return new ChatResponse(List.of(new Generation(answer)));
        };
        LlmFailover llm = new LlmFailover(List.of(new LlmFailover.Provider("scripted", model)));
        return new OpsAgent(llm, List.of(), serviceHealth, Set.of(), "owner/repo");
    }

    private OpsReport investigate(OpsAgent agent) {
        return agent.investigate("why is booking down?", (name, data) -> events.add(name + " " + data));
    }

    @Test
    void runsToolsRejectsUnknownOnesAndVerifiesEvidence() {
        OpsReport report = investigate(agent(
                calls(call("c1", "service_health", "{}"), call("c2", "restart_service", "{}")),
                calls(call("c3", "submit_report", REPORT.formatted("""
                        {"statement":"booking not registered","evidence":["t1","t9"]},\
                        {"statement":"made up","evidence":["t9"]}""")))));

        // L'outil inconnu n'est pas exécuté : pas d'identifiant t2, message d'erreur rendu au modèle.
        assertThat(seen.get(0)).extracting(ToolResponseMessage.ToolResponse::responseData)
                .satisfiesExactly(
                        ok -> assertThat(ok).startsWith("[t1] ").contains("NOT_REGISTERED"),
                        unknown -> assertThat(unknown).startsWith("ERROR: unknown tool 'restart_service'"));
        assertThat(events).filteredOn(e -> e.startsWith("tool_call")).hasSize(1);

        // Preuve inventée (t9) retirée ; fait sans preuve réelle rétrogradé en hypothèse.
        assertThat(report.summary()).isEqualTo("booking down");
        assertThat(report.facts()).containsExactly(new OpsReport.Fact("booking not registered", List.of("t1")));
        assertThat(report.hypotheses()).containsExactly(new OpsReport.Hypothesis("made up", "low"));
        assertThat(report.actions()).containsExactly("check logs");
        assertThat(llmCalls).isEqualTo(2);
    }

    @Test
    void submitReportWinsOverOtherCallsOfTheSameTurn() {
        OpsReport report = investigate(agent(
                calls(call("c1", "service_health", "{}"), call("c2", "submit_report", REPORT.formatted("")))));

        assertThat(report.summary()).isEqualTo("booking down");
        assertThat(events).noneMatch(e -> e.startsWith("tool_call"));
        assertThat(llmCalls).isEqualTo(1);
    }

    @Test
    void invalidReportArgumentsAreSentBackForCorrection() {
        OpsReport report = investigate(agent(
                calls(call("c1", "submit_report", "{not json")),
                calls(call("c2", "submit_report", REPORT.formatted("")))));

        assertThat(seen.get(0)).singleElement().extracting(ToolResponseMessage.ToolResponse::responseData)
                .asString().startsWith("ERROR: invalid submit_report arguments");
        assertThat(report.summary()).isEqualTo("booking down");
        assertThat(llmCalls).isEqualTo(2);
    }

    @Test
    void lokiMetadataIsStrippedSoLinesFitTheOutputBudget() {
        String loki = """
                {"data":[],"streams":[{"labels":{"service_name":"booking-service"},\
                "structuredMetadata":{"stream":"stdout"},\
                "lines":[{"timestamp":"1","line":"ERROR boom","structuredMetadata":{"container_id":"abc"}}]}]}""";

        assertThat(OpsAgent.withoutLokiNoise(loki)).isEqualTo("""
                {"streams":[{"labels":{"service_name":"booking-service"},"lines":[{"timestamp":"1","line":"ERROR boom"}]}]}""");
        // Résultat métrique : "data" non vide conservé ; sortie non JSON laissée telle quelle.
        assertThat(OpsAgent.withoutLokiNoise("{\"data\":[{\"value\":1048}]}")).isEqualTo("{\"data\":[{\"value\":1048}]}");
        assertThat(OpsAgent.withoutLokiNoise("plain text")).isEqualTo("plain text");
    }

    @Test
    void repeatedCallReplaysItsResultWithoutUsingTheBudget() {
        OpsReport report = investigate(agent(
                calls(call("c1", "service_health", "{}")),
                calls(call("c2", "service_health", "{}")),
                calls(call("c3", "submit_report", REPORT.formatted("")))));

        assertThat(events).filteredOn(e -> e.startsWith("tool_call")).hasSize(1);
        assertThat(seen.get(1)).singleElement().extracting(ToolResponseMessage.ToolResponse::responseData)
                .asString().startsWith("Same call already made").contains("[t1] ");
        assertThat(report.summary()).isEqualTo("booking down");
    }

    @Test
    void toolBudgetAndTurnLimitStopARunawayModel() {
        // Arguments distincts à chaque tour : un appel répété serait rejoué, pas compté.
        AssistantMessage[] script = new AssistantMessage[8];
        for (int i = 0; i < script.length; i++) {
            script[i] = calls(call("c" + i, "service_health", "{\"attempt\":" + i + "}"));
        }
        OpsReport report = investigate(agent(script));

        // 6 outils exécutés, puis rappel « budget épuisé » ; 8 tours LLM au total, jamais de rapport.
        assertThat(events).filteredOn(e -> e.startsWith("tool_call")).hasSize(6);
        assertThat(seen.get(6)).singleElement().extracting(ToolResponseMessage.ToolResponse::responseData)
                .asString().startsWith("Tool budget exhausted");
        assertThat(llmCalls).isEqualTo(8);
        assertThat(events).contains("warning " + Map.of("message", "The model did not submit a structured report"));
        assertThat(report.summary()).startsWith("Investigation stopped before a final report");
        assertThat(report.facts()).isEmpty();
    }
}
