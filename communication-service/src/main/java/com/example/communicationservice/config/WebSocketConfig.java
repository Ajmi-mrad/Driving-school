package com.example.communicationservice.config;

import com.example.communicationservice.ws.StompAuthChannelInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configuration de la messagerie STOMP sur WebSocket.
 *
 * <ul>
 *   <li>{@code /ws} : point d'entrée du handshake (avec repli SockJS pour les navigateurs/proxys
 *       qui bloquent WebSocket).</li>
 *   <li>Broker <b>simple</b> en mémoire sur {@code /topic} (diffusion) et {@code /queue} (point à
 *       point via {@code /user}). NB : in-memory = mono-instance ; une montée en charge multi-replica
 *       nécessiterait un relais STOMP externe (RabbitMQ) — swap isolé à {@code configureMessageBroker}.</li>
 *   <li>{@code /app} : préfixe des destinations applicatives routées vers les {@code @MessageMapping}.</li>
 *   <li>{@code /user} : préfixe des destinations « privées » résolues par session utilisateur.</li>
 *   <li>L'intercepteur {@link StompAuthChannelInterceptor} authentifie la frame CONNECT (JWT).</li>
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final String frontendOrigin;

    public WebSocketConfig(StompAuthChannelInterceptor stompAuthChannelInterceptor,
                           @Value("${communication.frontend-origin:http://localhost:4200}") String frontendOrigin) {
        this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
        this.frontendOrigin = frontendOrigin;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(frontendOrigin)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}