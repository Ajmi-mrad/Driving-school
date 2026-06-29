package com.example.communicationservice.ws;

import com.example.communicationservice.service.ConversationService;
import com.example.communicationservice.web.dto.SendMessageRequest;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Point d'entrée temps réel STOMP. Le client publie sur {@code /app/chat.send} ; l'expéditeur est lu
 * du {@code Principal} de la session (posé par {@code StompAuthChannelInterceptor} au CONNECT), jamais
 * de la charge utile. Le service persiste puis pousse le message au destinataire via
 * {@code /user/{sub}/queue/messages}.
 */
@Controller
public class ChatController {

    private final ConversationService conversationService;

    public ChatController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @MessageMapping("/chat.send")
    public void send(@Valid @Payload SendMessageRequest request, Principal principal) {
        conversationService.handleIncomingMessage(principal.getName(), request);
    }
}