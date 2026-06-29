package com.example.communicationservice.web.dto;

import java.time.Instant;
import java.util.UUID;

public record ConversationResponse(
        UUID id,
        String monitorId,
        String clientId,
        Instant lastMessageAt,
        String lastMessagePreview
) {
}