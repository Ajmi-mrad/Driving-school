package com.example.communicationservice.service;

import com.example.communicationservice.domain.Conversation;
import com.example.communicationservice.domain.Notification;
import com.example.communicationservice.domain.NotificationType;
import com.example.communicationservice.mapper.CommunicationMapper;
import com.example.communicationservice.repository.NotificationRepository;
import com.example.communicationservice.web.dto.NotificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Notifications in-app : persistance, diffusion temps réel ({@code /user/{sub}/queue/notifications})
 * et fil de lecture. Deux origines en v1 : un message reçu hors-ligne ({@link #notifyNewMessage}) et
 * un rappel de paiement envoyé par un administrateur ({@link #createPayment}).
 */
@Service
public class NotificationService {

    /** Destination point-à-point côté client : résolue par Spring en /user/{sub}/queue/notifications. */
    private static final String USER_QUEUE_NOTIFICATIONS = "/queue/notifications";
    private static final String NEW_MESSAGE_TITLE = "Nouveau message";
    private static final String PAYMENT_TITLE = "Paiement dû";

    private final NotificationRepository notificationRepository;
    private final CommunicationMapper mapper;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(NotificationRepository notificationRepository,
                               CommunicationMapper mapper,
                               SimpMessagingTemplate messagingTemplate) {
        this.notificationRepository = notificationRepository;
        this.mapper = mapper;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Crée une notification {@code NEW_MESSAGE} pour un destinataire hors-ligne (l'appel est fait par
     * {@code ConversationService} uniquement dans ce cas) et la pousse pour mise à jour de sa cloche.
     */
    @Transactional
    public void notifyNewMessage(String recipientSub, Conversation conversation, String preview) {
        Notification notification = new Notification();
        notification.setRecipientId(recipientSub);
        notification.setType(NotificationType.NEW_MESSAGE);
        notification.setTitle(NEW_MESSAGE_TITLE);
        notification.setBody(preview);
        notification.setReferenceId(conversation.getId().toString());
        persistAndPush(notification);
    }

    /** Envoi manuel (administrateur) d'un rappel de paiement à un client. */
    @Transactional
    public NotificationResponse createPayment(String clientSub, BigDecimal amount, String message) {
        Notification notification = new Notification();
        notification.setRecipientId(clientSub);
        notification.setType(NotificationType.PAYMENT_DUE);
        notification.setTitle(PAYMENT_TITLE);
        notification.setBody(StringUtils.hasText(message) ? message : null);
        notification.setAmount(amount);
        return persistAndPush(notification);
    }

    /** Fil de notifications de l'appelant, plus récentes d'abord. */
    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(String recipientSub, Pageable pageable) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientSub, pageable)
                .map(mapper::toNotificationResponse);
    }

    /** Nombre de notifications non lues de l'appelant (badge). */
    @Transactional(readOnly = true)
    public long unreadCount(String recipientSub) {
        return notificationRepository.countByRecipientIdAndReadAtIsNull(recipientSub);
    }

    /** Marque une notification de l'appelant comme lue (silencieux si déjà lue ; ignore si absente). */
    @Transactional
    public void markRead(UUID id, String recipientSub) {
        notificationRepository.findByIdAndRecipientId(id, recipientSub).ifPresent(notification -> {
            if (notification.getReadAt() == null) {
                notification.setReadAt(Instant.now());
            }
        });
    }

    /** Marque toutes les notifications non lues de l'appelant comme lues. */
    @Transactional
    public void markAllRead(String recipientSub) {
        notificationRepository.markAllRead(recipientSub, Instant.now());
    }

    private NotificationResponse persistAndPush(Notification notification) {
        Notification saved = notificationRepository.save(notification);
        NotificationResponse response = mapper.toNotificationResponse(saved);
        messagingTemplate.convertAndSendToUser(saved.getRecipientId(), USER_QUEUE_NOTIFICATIONS, response);
        return response;
    }
}
