package com.example.communicationservice.dev;

import com.example.communicationservice.dev.dto.CommunicationExport;
import com.example.communicationservice.dev.dto.CommunicationSeedRequest;
import com.example.communicationservice.dev.dto.SeedResult;
import com.example.communicationservice.domain.Conversation;
import com.example.communicationservice.domain.Message;
import com.example.communicationservice.domain.Notification;
import com.example.communicationservice.domain.NotificationType;
import com.example.communicationservice.mapper.CommunicationMapper;
import com.example.communicationservice.repository.ConversationRepository;
import com.example.communicationservice.repository.MessageRepository;
import com.example.communicationservice.repository.NotificationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Outil de développement (activé par {@code app.dev-tools.enabled=true}) : peuplement et remise à
 * zéro des conversations, messages et notifications. Jeu de données aligné sur le mock frontend
 * (mock/db.ts). Les participants proviennent de la carte {@code seedKey -> sub} du seed auth.
 */
@Service
@ConditionalOnProperty(name = "app.dev-tools.enabled", havingValue = "true")
public class DevDataService {

    private static final Logger log = LoggerFactory.getLogger(DevDataService.class);

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final NotificationRepository notificationRepository;
    private final CommunicationMapper communicationMapper;

    @PersistenceContext
    private EntityManager entityManager;

    public DevDataService(ConversationRepository conversationRepository, MessageRepository messageRepository,
                          NotificationRepository notificationRepository, CommunicationMapper communicationMapper) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.notificationRepository = notificationRepository;
        this.communicationMapper = communicationMapper;
    }

    @Transactional
    public SeedResult seed(CommunicationSeedRequest request) {
        if (conversationRepository.count() > 0) {
            return new SeedResult(0, (int) totalRows());
        }
        Map<String, String> users = request == null ? Map.of() : request.users();
        Instant now = Instant.now();
        String mon1 = sub(users, "u-mon-1");
        String mon2 = sub(users, "u-mon-2");
        String cli1 = sub(users, "u-cli-1");
        String cli2 = sub(users, "u-cli-2");

        Conversation c1 = conversation(mon1, cli1, minutesAgo(now, 60), "Parfait, a demain 14h alors !");
        Conversation c2 = conversation(mon1, cli2, hoursAgo(now, 26), "Merci pour la seance");
        conversation(mon2, cli1, hoursAgo(now, 72), "N'oubliez pas vos documents");

        message(c1.getId(), cli1, "Bonjour, est-ce possible de decaler ma lecon ?", hoursAgo(now, 3), hoursAgo(now, 2));
        message(c1.getId(), mon1, "Bonjour Lucas, oui bien sur. Demain 14h vous convient ?", hoursAgo(now, 2), hoursAgo(now, 1));
        message(c1.getId(), mon1, "Parfait, a demain 14h alors !", minutesAgo(now, 60), null);
        message(c2.getId(), cli2, "Merci pour la seance", hoursAgo(now, 26), hoursAgo(now, 25));

        notification(cli1, NotificationType.NEW_MESSAGE, "Nouveau message",
                "Paul Girard vous a envoye un message.", c1.getId().toString(), null, null);
        notification(cli1, NotificationType.PAYMENT_DUE, "Paiement en attente",
                "Il vous reste un solde de 490 EUR a regler.", null, new BigDecimal("490.00"), null);
        notification(mon1, NotificationType.NEW_MESSAGE, "Nouveau message",
                "Lucas Martin vous a envoye un message.", c1.getId().toString(), null, hoursAgo(now, 2));

        int created = (int) totalRows();
        log.info("Seed communication: {} entités (conversations, messages, notifications)", created);
        return new SeedResult(created, 0);
    }

    /** Nombre total de lignes de communication (conversations + messages + notifications). */
    private long totalRows() {
        return conversationRepository.count() + messageRepository.count() + notificationRepository.count();
    }

    @Transactional
    public void reset() {
        entityManager.createNativeQuery(
                "TRUNCATE TABLE messages, conversations, notifications RESTART IDENTITY CASCADE").executeUpdate();
        log.info("Reset communication: conversations, messages et notifications vidés");
    }

    @Transactional(readOnly = true)
    public CommunicationExport export() {
        return new CommunicationExport(
                conversationRepository.findAll().stream().map(communicationMapper::toConversationResponse).toList(),
                messageRepository.findAll().stream().map(communicationMapper::toMessageResponse).toList(),
                notificationRepository.findAll().stream().map(communicationMapper::toNotificationResponse).toList());
    }

    // -- helpers ------------------------------------------------------------

    private static Instant hoursAgo(Instant now, long h) {
        return now.minus(Duration.ofHours(h));
    }

    private static Instant minutesAgo(Instant now, long m) {
        return now.minus(Duration.ofMinutes(m));
    }

    private static String sub(Map<String, String> users, String key) {
        String value = users.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Seed communication : identifiant Keycloak manquant pour " + key
                            + " (le seed auth doit être exécuté en premier)");
        }
        return value;
    }

    private Conversation conversation(String monitorId, String clientId, Instant lastMessageAt, String preview) {
        Conversation c = new Conversation();
        c.setMonitorId(monitorId);
        c.setClientId(clientId);
        c.setLastMessageAt(lastMessageAt);
        c.setLastMessagePreview(preview);
        return conversationRepository.save(c);
    }

    private void message(UUID conversationId, String senderId, String content, Instant sentAt, Instant readAt) {
        Message m = new Message();
        m.setConversationId(conversationId);
        m.setSenderId(senderId);
        m.setContent(content);
        m.setSentAt(sentAt);
        m.setReadAt(readAt);
        messageRepository.save(m);
    }

    private void notification(String recipientId, NotificationType type, String title, String body,
                              String referenceId, BigDecimal amount, Instant readAt) {
        Notification n = new Notification();
        n.setRecipientId(recipientId);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setReferenceId(referenceId);
        n.setAmount(amount);
        n.setReadAt(readAt);
        notificationRepository.save(n);
    }
}
