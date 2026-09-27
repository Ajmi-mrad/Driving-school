package com.example.communicationservice;

import com.example.communicationservice.dev.DevDataService;
import com.example.communicationservice.dev.dto.CommunicationSeedRequest;
import com.example.communicationservice.dev.dto.SeedResult;
import com.example.communicationservice.repository.ConversationRepository;
import com.example.communicationservice.repository.MessageRepository;
import com.example.communicationservice.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests d'intégration de l'outil de dev communication (seed / reset) sur une vraie base Postgres.
 */
@SpringBootTest(properties = "app.dev-tools.enabled=true")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CommunicationDevDataIT {

    @Autowired
    private DevDataService devDataService;
    @Autowired
    private ConversationRepository conversationRepository;
    @Autowired
    private MessageRepository messageRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    private static CommunicationSeedRequest request() {
        Map<String, String> users = new LinkedHashMap<>();
        for (String key : List.of("u-mon-1", "u-mon-2", "u-cli-1", "u-cli-2")) {
            users.put(key, UUID.randomUUID().toString());
        }
        return new CommunicationSeedRequest(users);
    }

    @BeforeEach
    void clean() {
        devDataService.reset();
    }

    @Test
    void seed_createsConversationsMessagesNotifications() {
        SeedResult res = devDataService.seed(request());

        assertThat(res.created()).isEqualTo(10);
        assertThat(conversationRepository.count()).isEqualTo(3);
        assertThat(messageRepository.count()).isEqualTo(4);
        assertThat(notificationRepository.count()).isEqualTo(3);
    }

    @Test
    void seed_isIdempotent() {
        devDataService.seed(request());
        SeedResult second = devDataService.seed(request());

        assertThat(second.created()).isZero();
        assertThat(second.skipped()).isEqualTo(10);
        assertThat(conversationRepository.count()).isEqualTo(3); // not doubled
    }

    @Test
    void reset_truncatesAll() {
        devDataService.seed(request());
        devDataService.reset();

        assertThat(conversationRepository.count()).isZero();
        assertThat(messageRepository.count()).isZero();
        assertThat(notificationRepository.count()).isZero();
    }
}
