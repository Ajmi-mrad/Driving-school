package com.example.communicationservice.mapper;

import com.example.communicationservice.domain.Conversation;
import com.example.communicationservice.domain.Message;
import com.example.communicationservice.domain.Notification;
import com.example.communicationservice.web.dto.ConversationResponse;
import com.example.communicationservice.web.dto.MessageResponse;
import com.example.communicationservice.web.dto.NotificationResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CommunicationMapper {

    ConversationResponse toConversationResponse(Conversation conversation);

    MessageResponse toMessageResponse(Message message);

    NotificationResponse toNotificationResponse(Notification notification);
}