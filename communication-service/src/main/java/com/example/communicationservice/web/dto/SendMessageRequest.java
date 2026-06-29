package com.example.communicationservice.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Charge utile d'un message envoyé via STOMP ({@code /app/chat.send}). L'expéditeur n'est jamais lu
 * du client : il est imposé côté serveur à partir du {@code Principal} de la session WebSocket.
 */
public record SendMessageRequest(
        @NotNull UUID conversationId,
        @NotBlank @Size(max = 4000) String content
) {
}