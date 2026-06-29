package com.example.communicationservice.web.dto;

import com.example.communicationservice.domain.MessageType;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        UUID conversationId,
        String senderId,
        MessageType type,
        String content,
        Instant sentAt,
        Instant readAt
) {
}