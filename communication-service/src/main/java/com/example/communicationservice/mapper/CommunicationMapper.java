package com.example.communicationservice.mapper;

import com.example.communicationservice.domain.Conversation;
import com.example.communicationservice.domain.Message;
import com.example.communicationservice.web.dto.ConversationResponse;
import com.example.communicationservice.web.dto.MessageResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CommunicationMapper {

    ConversationResponse toConversationResponse(Conversation conversation);

    MessageResponse toMessageResponse(Message message);
}