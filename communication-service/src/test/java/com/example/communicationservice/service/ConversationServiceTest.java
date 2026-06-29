package com.example.communicationservice.service;

import com.example.communicationservice.domain.Conversation;
import com.example.communicationservice.domain.Message;
import com.example.communicationservice.domain.MessageType;
import com.example.communicationservice.exception.ConversationNotFoundException;
import com.example.communicationservice.exception.NotAParticipantException;
import com.example.communicationservice.mapper.CommunicationMapper;
import com.example.communicationservice.repository.ConversationRepository;
import com.example.communicationservice.repository.MessageRepository;
import com.example.communicationservice.web.dto.ConversationResponse;
import com.example.communicationservice.web.dto.MessageResponse;
import com.example.communicationservice.web.dto.SendMessageRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires (Mockito) de la logique métier : aucun contexte Spring, tous les collaborateurs
 * sont simulés. On vérifie l'attribution des rôles, l'idempotence du get-or-create, les contrôles
 * de participation, et la persistance + diffusion d'un message.
 */
@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private CommunicationMapper mapper;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ConversationService service;

    @Test
    void getOrCreate_returnsExisting_withoutSaving() {
        // Appelant = élève "c", interlocuteur = moniteur "m" → paire (m, c).
        Conversation existing = conversation(UUID.randomUUID(), "m", "c");
        ConversationResponse expected = response(existing.getId());
        when(conversationRepository.findByMonitorIdAndClientId("m", "c")).thenReturn(Optional.of(existing));
        when(mapper.toConversationResponse(existing)).thenReturn(expected);

        ConversationResponse result = service.getOrCreate("c", Set.of("CLIENT"), "m");

        assertThat(result).isEqualTo(expected);
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void getOrCreate_whenCallerIsClient_assignsCounterpartAsMonitor() {
        when(conversationRepository.findByMonitorIdAndClientId("m", "c")).thenReturn(Optional.empty());
        when(conversationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.getOrCreate("c", Set.of("CLIENT"), "m");

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(captor.capture());
        Conversation saved = captor.getValue();
        assertThat(saved.getMonitorId()).isEqualTo("m");
        assertThat(saved.getClientId()).isEqualTo("c");
        assertThat(saved.getLastMessageAt()).isNotNull();
    }

    @Test
    void getOrCreate_whenCallerIsMonitor_assignsSelfAsMonitor() {
        when(conversationRepository.findByMonitorIdAndClientId("monitor-1", "client-9")).thenReturn(Optional.empty());
        when(conversationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.getOrCreate("monitor-1", Set.of("MONITOR"), "client-9");

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(captor.capture());
        assertThat(captor.getValue().getMonitorId()).isEqualTo("monitor-1");
        assertThat(captor.getValue().getClientId()).isEqualTo("client-9");
    }

    @Test
    void getMessages_whenConversationMissing_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(conversationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMessages(id, "c", null))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void getMessages_whenCallerNotParticipant_throwsForbidden() {
        UUID id = UUID.randomUUID();
        when(conversationRepository.findById(id)).thenReturn(Optional.of(conversation(id, "m", "c")));

        assertThatThrownBy(() -> service.getMessages(id, "intrus", null))
                .isInstanceOf(NotAParticipantException.class);
    }

    @Test
    void markRead_delegatesToRepositoryForCaller() {
        UUID id = UUID.randomUUID();
        when(conversationRepository.findById(id)).thenReturn(Optional.of(conversation(id, "m", "c")));

        service.markRead(id, "c");

        verify(messageRepository).markRead(eq(id), eq("c"), any(Instant.class));
    }

    @Test
    void unreadCount_delegatesToRepository() {
        when(messageRepository.countUnreadForUser("c")).thenReturn(7L);

        assertThat(service.unreadCount("c")).isEqualTo(7L);
    }

    @Test
    void handleIncomingMessage_persistsAndDispatchesToBothParticipants() {
        UUID id = UUID.randomUUID();
        Conversation conversation = conversation(id, "m", "c");
        when(conversationRepository.findById(id)).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        MessageResponse mapped = new MessageResponse(UUID.randomUUID(), id, "c", MessageType.TEXT, "Bonjour", Instant.now(), null);
        when(mapper.toMessageResponse(any())).thenReturn(mapped);

        MessageResponse result = service.handleIncomingMessage("c", new SendMessageRequest(id, "Bonjour"));

        assertThat(result).isEqualTo(mapped);
        // Le message persisté porte l'expéditeur imposé et le type TEXT.
        ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(msgCaptor.capture());
        Message persisted = msgCaptor.getValue();
        assertThat(persisted.getSenderId()).isEqualTo("c");
        assertThat(persisted.getContent()).isEqualTo("Bonjour");
        assertThat(persisted.getType()).isEqualTo(MessageType.TEXT);
        assertThat(persisted.getSentAt()).isNotNull();
        // L'aperçu de la conversation est mis à jour.
        assertThat(conversation.getLastMessagePreview()).isEqualTo("Bonjour");
        assertThat(conversation.getLastMessageAt()).isNotNull();
        // Diffusion au destinataire (le moniteur) ET echo à l'expéditeur.
        verify(messagingTemplate).convertAndSendToUser("m", "/queue/messages", mapped);
        verify(messagingTemplate).convertAndSendToUser("c", "/queue/messages", mapped);
    }

    @Test
    void handleIncomingMessage_whenSenderNotParticipant_throwsAndDoesNotPersist() {
        UUID id = UUID.randomUUID();
        when(conversationRepository.findById(id)).thenReturn(Optional.of(conversation(id, "m", "c")));

        assertThatThrownBy(() -> service.handleIncomingMessage("intrus", new SendMessageRequest(id, "hi")))
                .isInstanceOf(NotAParticipantException.class);

        verify(messageRepository, never()).save(any());
    }

    private static Conversation conversation(UUID id, String monitorId, String clientId) {
        Conversation c = new Conversation();
        c.setId(id);
        c.setMonitorId(monitorId);
        c.setClientId(clientId);
        return c;
    }

    private static ConversationResponse response(UUID id) {
        return new ConversationResponse(id, "m", "c", Instant.now(), null);
    }
}