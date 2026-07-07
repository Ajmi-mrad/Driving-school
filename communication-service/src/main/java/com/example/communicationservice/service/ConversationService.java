package com.example.communicationservice.service;

import com.example.communicationservice.domain.Conversation;
import com.example.communicationservice.domain.Message;
import com.example.communicationservice.domain.MessageType;
import com.example.communicationservice.exception.ConversationNotFoundException;
import com.example.communicationservice.exception.NotAParticipantException;
import com.example.communicationservice.mapper.CommunicationMapper;
import com.example.communicationservice.repository.ConversationRepository;
import com.example.communicationservice.repository.MessageRepository;
import com.example.communicationservice.ws.PresenceRegistry;
import com.example.communicationservice.web.dto.ConversationResponse;
import com.example.communicationservice.web.dto.MessageResponse;
import com.example.communicationservice.web.dto.SendMessageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Logique métier de la messagerie : ouverture de conversations, historique, accusés de lecture,
 * et envoi de messages (persistance + diffusion temps réel via STOMP).
 */
@Service
public class ConversationService {

    private static final String ROLE_MONITOR = "MONITOR";
    private static final int PREVIEW_MAX = 500;
    /** Destination point-à-point côté client : résolue par Spring en /user/{sub}/queue/messages. */
    private static final String USER_QUEUE_MESSAGES = "/queue/messages";

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final CommunicationMapper mapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final PresenceRegistry presenceRegistry;
    private final NotificationService notificationService;

    public ConversationService(ConversationRepository conversationRepository,
                               MessageRepository messageRepository,
                               CommunicationMapper mapper,
                               SimpMessagingTemplate messagingTemplate,
                               PresenceRegistry presenceRegistry,
                               NotificationService notificationService) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.mapper = mapper;
        this.messagingTemplate = messagingTemplate;
        this.presenceRegistry = presenceRegistry;
        this.notificationService = notificationService;
    }

    /**
     * Récupère ou crée la conversation entre l'appelant et son interlocuteur. L'attribution des rôles
     * (qui est moniteur, qui est élève) découle du rôle de l'appelant : la paire (monitor_id, client_id)
     * est ainsi stable quel que soit celui qui ouvre la conversation, ce qui garantit l'unicité.
     */
    @Transactional
    public ConversationResponse getOrCreate(String callerSub, Set<String> callerRoles, String counterpartId) {
        boolean callerIsMonitor = callerRoles.contains(ROLE_MONITOR);
        String monitorId = callerIsMonitor ? callerSub : counterpartId;
        String clientId = callerIsMonitor ? counterpartId : callerSub;

        Conversation conversation = conversationRepository.findByMonitorIdAndClientId(monitorId, clientId)
                .orElseGet(() -> {
                    Conversation c = new Conversation();
                    c.setMonitorId(monitorId);
                    c.setClientId(clientId);
                    c.setLastMessageAt(Instant.now());
                    return conversationRepository.save(c);
                });
        return mapper.toConversationResponse(conversation);
    }

    /** Inbox de l'appelant (conversations où il est moniteur ou élève), plus récentes d'abord. */
    @Transactional(readOnly = true)
    public List<ConversationResponse> listForUser(String callerSub) {
        return conversationRepository
                .findByMonitorIdOrClientIdOrderByLastMessageAtDesc(callerSub, callerSub)
                .stream()
                .map(mapper::toConversationResponse)
                .toList();
    }

    /** Historique paginé d'une conversation — réservé à ses deux participants. */
    @Transactional(readOnly = true)
    public Page<MessageResponse> getMessages(UUID conversationId, String callerSub, Pageable pageable) {
        requireParticipant(loadConversation(conversationId), callerSub);
        return messageRepository.findByConversationIdOrderBySentAtDesc(conversationId, pageable)
                .map(mapper::toMessageResponse);
    }

    /** Marque comme lus les messages adressés à l'appelant dans cette conversation. */
    @Transactional
    public void markRead(UUID conversationId, String callerSub) {
        requireParticipant(loadConversation(conversationId), callerSub);
        messageRepository.markRead(conversationId, callerSub, Instant.now());
    }

    /** Total des messages non lus adressés à l'appelant (badge). */
    @Transactional(readOnly = true)
    public long unreadCount(String callerSub) {
        return messageRepository.countUnreadForUser(callerSub);
    }

    /**
     * Persiste un message entrant (envoyé via STOMP) puis le pousse au destinataire et en renvoie une
     * copie à l'expéditeur (echo, pour confirmer la livraison et synchroniser ses autres onglets).
     * L'expéditeur est imposé par l'appelant (le {@code Principal} de la session), jamais par le client.
     */
    @Transactional
    public MessageResponse handleIncomingMessage(String senderSub, SendMessageRequest request) {
        Conversation conversation = loadConversation(request.conversationId());
        requireParticipant(conversation, senderSub);

        Instant now = Instant.now();
        Message message = new Message();
        message.setConversationId(conversation.getId());
        message.setSenderId(senderSub);
        message.setType(MessageType.TEXT);
        message.setContent(request.content());
        message.setSentAt(now);
        Message saved = messageRepository.save(message);

        conversation.setLastMessageAt(now);
        conversation.setLastMessagePreview(preview(request.content()));

        MessageResponse response = mapper.toMessageResponse(saved);
        String recipient = counterpart(conversation, senderSub);
        messagingTemplate.convertAndSendToUser(recipient, USER_QUEUE_MESSAGES, response);
        messagingTemplate.convertAndSendToUser(senderSub, USER_QUEUE_MESSAGES, response);

        // Le destinataire en ligne reçoit déjà le message live ; hors-ligne, on dépose une notification.
        if (!presenceRegistry.isOnline(recipient)) {
            notificationService.notifyNewMessage(recipient, conversation, preview(request.content()));
        }
        return response;
    }

    private Conversation loadConversation(UUID id) {
        return conversationRepository.findById(id)
                .orElseThrow(() -> new ConversationNotFoundException(id));
    }

    private void requireParticipant(Conversation conversation, String sub) {
        if (!isParticipant(conversation, sub)) {
            throw new NotAParticipantException();
        }
    }

    private boolean isParticipant(Conversation conversation, String sub) {
        return conversation.getMonitorId().equals(sub) || conversation.getClientId().equals(sub);
    }

    private String counterpart(Conversation conversation, String sub) {
        return conversation.getMonitorId().equals(sub)
                ? conversation.getClientId()
                : conversation.getMonitorId();
    }

    private String preview(String content) {
        return content.length() <= PREVIEW_MAX ? content : content.substring(0, PREVIEW_MAX);
    }
}