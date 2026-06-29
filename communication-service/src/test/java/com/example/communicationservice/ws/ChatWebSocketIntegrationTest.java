package com.example.communicationservice.ws;

import com.example.communicationservice.TestcontainersConfiguration;
import com.example.communicationservice.domain.Conversation;
import com.example.communicationservice.repository.ConversationRepository;
import com.example.communicationservice.web.dto.MessageResponse;
import com.example.communicationservice.web.dto.SendMessageRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Test d'intégration WebSocket/STOMP de bout en bout : l'application tourne sur un port réel, deux
 * sessions STOMP se connectent (élève + moniteur), l'élève publie sur {@code /app/chat.send} et le
 * moniteur reçoit le message sur sa file privée {@code /user/queue/messages}.
 *
 * <p>On simule le {@link JwtDecoder} pour que l'intercepteur d'authentification accepte des jetons
 * fabriqués (sub + rôles), ce qui évite d'avoir un Keycloak réel pendant le test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ChatWebSocketIntegrationTest {

    private static final String MONITOR = "monitor-1";
    private static final String CLIENT = "client-1";

    @LocalServerPort
    private int port;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private ConversationRepository conversationRepository;

    @BeforeEach
    void stubTokens() {
        when(jwtDecoder.decode("monitor-token")).thenReturn(jwtFor(MONITOR, "MONITOR"));
        when(jwtDecoder.decode("client-token")).thenReturn(jwtFor(CLIENT, "CLIENT"));
    }

    @Test
    void messageSentByClient_isDeliveredToMonitorUserQueue() throws Exception {
        UUID conversationId = persistConversation();
        BlockingQueue<MessageResponse> monitorInbox = new LinkedBlockingQueue<>();

        StompSession monitorSession = connect("monitor-token");
        monitorSession.subscribe("/user/queue/messages", collectInto(monitorInbox));

        StompSession clientSession = connect("client-token");
        // Laisse le temps à la frame SUBSCRIBE du moniteur d'être enregistrée par le broker.
        TimeUnit.MILLISECONDS.sleep(300);

        clientSession.send("/app/chat.send", new SendMessageRequest(conversationId, "Bonjour"));

        MessageResponse received = monitorInbox.poll(5, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        assertThat(received.content()).isEqualTo("Bonjour");
        assertThat(received.senderId()).isEqualTo(CLIENT);
        assertThat(received.conversationId()).isEqualTo(conversationId);

        clientSession.disconnect();
        monitorSession.disconnect();
    }

    private UUID persistConversation() {
        Conversation conversation = new Conversation();
        conversation.setMonitorId(MONITOR);
        conversation.setClientId(CLIENT);
        conversation.setLastMessageAt(Instant.now());
        return conversationRepository.save(conversation).getId();
    }

    private StompSession connect(String token) throws Exception {
        List<Transport> transports = List.of(new WebSocketTransport(new StandardWebSocketClient()));
        WebSocketStompClient stompClient = new WebSocketStompClient(new SockJsClient(transports));
        stompClient.setMessageConverter(jacksonConverter());

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token); // lu par StompAuthChannelInterceptor au CONNECT

        return stompClient.connectAsync(
                        "http://localhost:" + port + "/ws",
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        new StompSessionHandlerAdapter() {
                        })
                .get(5, TimeUnit.SECONDS);
    }

    private static StompFrameHandler collectInto(BlockingQueue<MessageResponse> queue) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return MessageResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add((MessageResponse) payload);
            }
        };
    }

    private static MappingJackson2MessageConverter jacksonConverter() {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        // JavaTimeModule : indispensable pour (dé)sérialiser les Instant des MessageResponse.
        converter.setObjectMapper(new ObjectMapper().registerModule(new JavaTimeModule()));
        return converter;
    }

    private static Jwt jwtFor(String subject, String realmRole) {
        return Jwt.withTokenValue("token-" + subject)
                .header("alg", "none")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("realm_access", Map.of("roles", List.of(realmRole)))
                .build();
    }
}