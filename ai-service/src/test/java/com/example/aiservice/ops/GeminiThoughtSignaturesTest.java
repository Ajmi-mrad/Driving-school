package com.example.aiservice.ops;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiThoughtSignaturesTest {

    private static final String RESPONSE = """
            {'choices':[{'message':{'tool_calls':[
              {'id':'g1','function':{'name':'service_health'},'extra_content':{'google':{'thought_signature':'SIG-1'}}}
            ]}}]}""";

    private static final String REQUEST = """
            {'model':'gemini','messages':[
              {'role':'user','content':'check'},
              {'role':'assistant','tool_calls':[{'id':'g1','function':{'name':'service_health'}}]},
              {'role':'assistant','tool_calls':[{'id':'call_groq','function':{'name':'query_prometheus'}}]}
            ]}""";

    private final GeminiThoughtSignatures interceptor = new GeminiThoughtSignatures();

    private static byte[] bytes(String json) {
        return json.replace('\'', '"').getBytes(StandardCharsets.UTF_8);
    }

    private static String signed(String id, String name, String signature) {
        return "\"id\":\"" + id + "\",\"function\":{\"name\":\"" + name + "\"},"
                + "\"extra_content\":{\"google\":{\"thought_signature\":\"" + signature + "\"}}";
    }

    private String attach(Map<String, String> investigation) {
        return GeminiThoughtSignatures.withSignatures(investigation,
                () -> new String(interceptor.attachSignatures(bytes(REQUEST)), StandardCharsets.UTF_8));
    }

    @Test
    void reattachesRememberedSignatureAndPlaceholdersUnknownCalls() {
        Map<String, String> investigation = new HashMap<>();
        GeminiThoughtSignatures.withSignatures(investigation, () -> {
            interceptor.rememberSignatures(bytes(RESPONSE));
            return null;
        });

        assertThat(attach(investigation))
                .contains(signed("g1", "service_health", "SIG-1"))
                .contains(signed("call_groq", "query_prometheus", GeminiThoughtSignatures.PLACEHOLDER));
    }

    @Test
    void signaturesDoNotLeakBetweenInvestigations() {
        Map<String, String> first = new HashMap<>();
        GeminiThoughtSignatures.withSignatures(first, () -> {
            interceptor.rememberSignatures(bytes(RESPONSE));
            return null;
        });

        // Une autre investigation qui aurait le même id d'appel ne reçoit pas la signature de la première.
        assertThat(attach(new HashMap<>())).contains(signed("g1", "service_health", GeminiThoughtSignatures.PLACEHOLDER));
        // Hors investigation (aucun scope), rien n'est mémorisé.
        interceptor.rememberSignatures(bytes(RESPONSE));
        assertThat(first).containsOnlyKeys("g1");
    }

    @Test
    void leavesRequestsWithoutToolCallsUntouched() {
        byte[] body = bytes("{'model':'gemini','messages':[{'role':'user','content':'hi'}]}");
        assertThat(interceptor.attachSignatures(body)).isSameAs(body);
    }
}
