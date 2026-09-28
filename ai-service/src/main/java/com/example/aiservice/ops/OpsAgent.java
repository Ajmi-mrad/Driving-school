package com.example.aiservice.ops;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.client.McpSyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * Agent d'investigation (lecture seule) : une boucle LLM + appels d'outils. Les outils viennent des
 * serveurs MCP (Grafana → Prometheus/Loki, GitHub) et de {@link ServiceHealthTool}. Chaque appel
 * d'outil est tracé (événements {@code tool_call}/{@code tool_result}) et reçoit un identifiant
 * ({@code t1}, {@code t2}…) que le rapport final doit citer comme preuve.
 */
@Service
public class OpsAgent {

    private static final Logger log = LoggerFactory.getLogger(OpsAgent.class);

    // Groq free tier = 8k tokens/minute : sorties d'outils tronquées et nombre d'appels plafonné.
    private static final int MAX_TOOL_CALLS = 6;
    private static final int MAX_TOOL_OUTPUT_CHARS = 1500;
    // Tours LLM max : les appels d'outils + un rappel « budget épuisé » + un essai si le rapport est invalide.
    private static final int MAX_TURNS = MAX_TOOL_CALLS + 2;
    private static final String SUBMIT_REPORT = "submit_report";
    // Listes d'outils MCP mises en cache : un serveur sain n'est pas réinterrogé à chaque question,
    // un serveur en panne n'impose pas son timeout à chaque question.
    private static final Duration MCP_TOOLS_TTL = Duration.ofMinutes(5);
    private static final Duration MCP_FAILURE_TTL = Duration.ofMinutes(1);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            You are the read-only operations assistant of the Auto-Ecole platform: Spring Boot \
            microservices (api-gateway, auth-service, vehicle-service, booking-service, finance-service, \
            communication-service) registered in Eureka (discovery-service) and running in Docker.
            Get evidence with tools, never invent infrastructure state:
            - service_health: Eureka registration + /actuator/health + running build of every service. \
            That build is what is deployed: commit=local means built on a developer machine, not by CI/CD, \
            so it may contain unpushed code. Never assume the latest GitHub commit is running: compare it \
            with these builds (commit sha, build time) before linking a problem to a deployment.
            - query_prometheus (datasourceUid "prometheus", queryType "instant" unless you need a range): \
            up{job="spring-actuator"}, ALERTS{alertstate="firing"}, \
            sum by (application,status)(rate(http_server_requests_seconds_count[5m])), jvm_memory_used_bytes.
            - query_loki_logs (datasourceUid "loki"): container logs, label service_name = container name \
            (booking-service, keycloak…). Count ERROR-level lines first (queryType "instant"; \
            not "Exception": stack traces of WARN logs contain it too): \
            sum by (service_name)(count_over_time({service_namespace="driving-school"} |~ `\\bERROR\\b` [1h])); \
            then read one service's lines: {service_name="booking-service"} |~ `\\bERROR\\b`.
            Never repeat a tool call with the same arguments: reuse its result and id.
            - list_commits, actions_list on GitHub repo %s: recent changes and CI/CD runs.
            Every tool result starts with its id, e.g. [t2].
            When done, call submit_report exactly once (never answer in plain text). Report rules: facts = only what a tool result shows, each citing its ids in evidence; \
            anything inferred goes to hypotheses with confidence low|medium|high; actions = next steps \
            for a human. You cannot restart, modify or deploy anything: if asked, say so in the summary. \
            Use at most %d tool calls. Write every report field in the language of the user's question.""";

    private record CachedTools(List<ToolCallback> tools, String error, Instant expiresAt) {
    }

    private final LlmFailover llm;
    private final List<McpSyncClient> mcpClients;
    private final ServiceHealthTool serviceHealth;
    private final Set<String> allowedMcpTools;
    private final String systemPrompt;
    private final Map<McpSyncClient, CachedTools> mcpTools = new ConcurrentHashMap<>();

    public OpsAgent(LlmFailover llm,
                    List<McpSyncClient> mcpClients,
                    ServiceHealthTool serviceHealth,
                    @Value("${ai.ops.allowed-mcp-tools}") Set<String> allowedMcpTools,
                    @Value("${ai.ops.github.repo}") String githubRepo) {
        this.llm = llm;
        this.mcpClients = mcpClients;
        this.serviceHealth = serviceHealth;
        this.allowedMcpTools = allowedMcpTools;
        this.systemPrompt = SYSTEM_PROMPT.formatted(githubRepo, MAX_TOOL_CALLS);
    }

    /**
     * Mène l'investigation et renvoie le rapport vérifié. {@code events} reçoit la chronologie
     * (warning, tool_call, tool_result) au fil de l'eau.
     */
    public OpsReport investigate(String question, BiConsumer<String, Object> events) {
        AtomicInteger sequence = new AtomicInteger();
        Set<String> evidenceIds = ConcurrentHashMap.newKeySet();
        List<String> unavailable = new ArrayList<>();

        List<ToolCallback> sources = new ArrayList<>(List.of(ToolCallbacks.from(serviceHealth)));
        sources.addAll(mcpTools(unavailable, events));
        Map<String, ToolCallback> tools = new LinkedHashMap<>();
        sources.forEach(tool -> tools.put(tool.getToolDefinition().name(),
                new TracedTool(tool, sequence, evidenceIds, events)));

        // Rapport structuré soumis via un outil : gpt-oss tente sinon d'« appeler » son JSON comme
        // un outil inexistant.
        AtomicReference<OpsReport> submitted = new AtomicReference<>();
        ToolCallback submitReport = FunctionToolCallback.<OpsReport, String>builder(SUBMIT_REPORT, report -> {
                    submitted.set(report);
                    return "submitted";
                })
                .description("Submit the final investigation report. Call exactly once, at the end.")
                .inputType(OpsReport.class)
                .build();

        List<ToolCallback> allTools = new ArrayList<>(tools.values());
        allTools.add(submitReport);
        // Tous les outils restent déclarés à chaque tour, même budget épuisé : Groq rejette côté
        // serveur (400 tool_use_failed) un appel à un outil non déclaré, alors que notre boucle sait
        // y répondre par un rappel « appelle submit_report ».
        ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                .toolCallbacks(allTools)
                .internalToolExecutionEnabled(false)
                .build();

        String userMessage = unavailable.isEmpty()
                ? question
                : question + "\n\n(Unavailable tool sources: " + String.join(", ", unavailable) + ")";
        List<Message> history = new ArrayList<>(List.of(new SystemMessage(systemPrompt), new UserMessage(userMessage)));
        LlmFailover.Session session = llm.session(events);
        int toolCalls = 0;
        // Un appel identique (même outil, mêmes arguments) rejoue son résultat au lieu de consommer le budget.
        Map<String, String> results = new HashMap<>();
        String text = null;

        // Boucle d'outils pilotée ici : chaque tour LLM peut basculer de fournisseur sans relancer les
        // outils déjà exécutés, et les appels invalides deviennent des messages que le modèle corrige.
        for (int turn = 0; turn < MAX_TURNS && submitted.get() == null; turn++) {
            AssistantMessage answer = session.call(new Prompt(history, options)).getResult().getOutput();
            if (!answer.hasToolCalls()) {
                text = answer.getText();
                break;
            }
            List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
            // submit_report d'abord : s'il est valide, les autres outils du même tour sont inutiles.
            List<AssistantMessage.ToolCall> calls = answer.getToolCalls().stream()
                    .sorted(Comparator.comparing(call -> !SUBMIT_REPORT.equals(call.name())))
                    .toList();
            for (AssistantMessage.ToolCall call : calls) {
                String result;
                if (SUBMIT_REPORT.equals(call.name())) {
                    result = submit(submitReport, call.arguments());
                    if (submitted.get() != null) {
                        break;
                    }
                } else if (!tools.containsKey(call.name())) {
                    result = "ERROR: unknown tool '" + call.name() + "'. Available tools: " + tools.keySet()
                            + ", " + SUBMIT_REPORT + ".";
                } else if (results.containsKey(call.name() + call.arguments())) {
                    result = "Same call already made, reuse this result and its id: "
                            + results.get(call.name() + call.arguments());
                } else if (toolCalls >= MAX_TOOL_CALLS) {
                    result = "Tool budget exhausted (" + MAX_TOOL_CALLS
                            + " calls): call submit_report now with the evidence you have.";
                } else {
                    toolCalls++;
                    result = tools.get(call.name()).call(call.arguments());
                    results.put(call.name() + call.arguments(), result);
                }
                responses.add(new ToolResponseMessage.ToolResponse(call.id(), call.name(), result));
            }
            history.add(answer);
            history.add(ToolResponseMessage.builder().responses(responses).build());
        }

        OpsReport report = submitted.get();
        if (report == null) {
            // Pas de rapport (texte libre ou tours épuisés) : on rend ce qu'on a au lieu d'une erreur,
            // les preuves restent visibles dans la chronologie.
            events.accept("warning", Map.of("message", "The model did not submit a structured report"));
            report = new OpsReport(text != null && !text.isBlank() ? text
                    : "Investigation stopped before a final report; see the collected evidence in the timeline.",
                    List.of(), List.of(), List.of());
        }
        return report.verified(evidenceIds);
    }

    /** Arguments invalides (mauvais type, JSON cassé) : message d'erreur renvoyé au modèle pour qu'il corrige. */
    private static String submit(ToolCallback submitReport, String arguments) {
        try {
            return submitReport.call(arguments);
        } catch (RuntimeException e) {
            return "ERROR: invalid submit_report arguments (" + e.getMessage()
                    + "). Call submit_report again with arguments matching its schema.";
        }
    }

    /**
     * Outils MCP autorisés, serveur par serveur, en parallèle et depuis le cache si possible. Un
     * serveur en panne ne bloque pas l'investigation : c'est justement quand l'infra va mal qu'on a
     * besoin de l'assistant.
     */
    private List<ToolCallback> mcpTools(List<String> unavailable, BiConsumer<String, Object> events) {
        Instant now = Instant.now();
        Map<McpSyncClient, CompletableFuture<CachedTools>> pending = new LinkedHashMap<>();
        for (McpSyncClient client : mcpClients) {
            CachedTools cached = mcpTools.get(client);
            // ponytail: listTools bloquant sur le pool commun ; acceptable pour 2 serveurs.
            pending.put(client, cached != null && cached.expiresAt().isAfter(now)
                    ? CompletableFuture.completedFuture(cached)
                    : CompletableFuture.supplyAsync(() -> listTools(client)));
        }
        List<ToolCallback> result = new ArrayList<>();
        pending.forEach((client, future) -> {
            CachedTools tools = future.join();
            mcpTools.put(client, tools);
            if (tools.error() == null) {
                result.addAll(tools.tools());
            } else {
                String source = client.getClientInfo().name();
                unavailable.add(source);
                events.accept("warning", Map.of("message", "Tool source unavailable: " + source));
            }
        });
        return result;
    }

    private CachedTools listTools(McpSyncClient client) {
        try {
            List<ToolCallback> tools = List.of(SyncMcpToolCallbackProvider.builder()
                    .addMcpClient(client)
                    .toolFilter((connection, tool) -> allowedMcpTools.contains(tool.name()))
                    .build()
                    .getToolCallbacks());
            return new CachedTools(tools, null, Instant.now().plus(MCP_TOOLS_TTL));
        } catch (RuntimeException e) {
            log.warn("Serveur MCP indisponible: {}", client.getClientInfo().name(), e);
            return new CachedTools(List.of(), String.valueOf(e.getMessage()), Instant.now().plus(MCP_FAILURE_TTL));
        }
    }

    /**
     * Loki joint à chaque ligne ~700 caractères de métadonnées (container_id, chemins de fichiers) :
     * sans ce nettoyage, la troncature ne laisse voir qu'une ligne au modèle, et le {@code "data":[]}
     * de tête (résultat métrique vide) lui fait croire à une réponse vide.
     */
    static String withoutLokiNoise(String text) {
        try {
            JsonNode node = JSON.readTree(text);
            if (node == null || !node.isContainerNode()) {
                return text;
            }
            if (node instanceof ObjectNode root && root.has("streams") && root.path("data").isEmpty()) {
                root.remove("data");
            }
            dropStructuredMetadata(node);
            return JSON.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return text;
        }
    }

    private static void dropStructuredMetadata(JsonNode node) {
        if (node instanceof ObjectNode object) {
            object.remove("structuredMetadata");
        }
        node.forEach(OpsAgent::dropStructuredMetadata);
    }

    /** Décore un outil : identifiant de preuve, événements de chronologie, erreurs, troncature. */
    private record TracedTool(ToolCallback delegate, AtomicInteger sequence, Set<String> ids,
                              BiConsumer<String, Object> events) implements ToolCallback {

        @Override
        public ToolDefinition getToolDefinition() {
            return delegate.getToolDefinition();
        }

        @Override
        public String call(String input) {
            String id = "t" + sequence.incrementAndGet();
            ids.add(id);
            events.accept("tool_call", Map.of("id", id, "tool", getToolDefinition().name(), "input", input));
            String output;
            boolean ok = true;
            try {
                output = truncate(withoutLokiNoise(unwrapMcpText(delegate.call(input))));
            } catch (RuntimeException e) {
                ok = false;
                output = "ERROR: " + e.getMessage();
            }
            events.accept("tool_result", Map.of("id", id, "ok", ok, "output", output));
            return "[" + id + "] " + output;
        }

        /**
         * Les résultats MCP arrivent sous la forme [{"text":"<json échappé>"}] : on ne garde que le
         * texte, pour que le budget de caractères serve aux données et non à l'échappement.
         */
        private static String unwrapMcpText(String output) {
            try {
                JsonNode node = JSON.readTree(output);
                if (node != null && node.isArray() && !node.isEmpty() && node.get(0).has("text")) {
                    StringBuilder text = new StringBuilder();
                    node.forEach(part -> text.append(part.path("text").asText()));
                    return text.toString();
                }
            } catch (JsonProcessingException e) {
                // Pas du JSON : sortie laissée telle quelle.
            }
            return output;
        }

        private static String truncate(String text) {
            if (text == null) {
                return "";
            }
            return text.length() <= MAX_TOOL_OUTPUT_CHARS
                    ? text
                    : text.substring(0, MAX_TOOL_OUTPUT_CHARS) + "…[truncated]";
        }
    }
}
