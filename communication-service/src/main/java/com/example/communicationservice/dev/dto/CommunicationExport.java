package com.example.communicationservice.dev.dto;

import com.example.communicationservice.web.dto.ConversationResponse;
import com.example.communicationservice.web.dto.MessageResponse;
import com.example.communicationservice.web.dto.NotificationResponse;

import java.util.List;

/** Export JSON des données communication. */
public record CommunicationExport(
        List<ConversationResponse> conversations,
        List<MessageResponse> messages,
        List<NotificationResponse> notifications) {
}
