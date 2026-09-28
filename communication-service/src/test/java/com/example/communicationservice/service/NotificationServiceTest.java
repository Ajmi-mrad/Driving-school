package com.example.communicationservice.service;

import com.example.communicationservice.domain.Conversation;
import com.example.communicationservice.domain.Notification;
import com.example.communicationservice.domain.NotificationType;
import com.example.communicationservice.mapper.CommunicationMapper;
import com.example.communicationservice.repository.NotificationRepository;
import com.example.communicationservice.web.dto.NotificationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires (Mockito) du service de notifications : persistance, diffusion temps réel sur
 * {@code /queue/notifications} et accusés de lecture.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private CommunicationMapper mapper;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private NotificationService service;

    @Test
    void notifyNewMessage_persistsAndPushesToRecipient() {
        UUID convId = UUID.randomUUID();
        Conversation conversation = new Conversation();
        conversation.setId(convId);
        NotificationResponse mapped = sampleResponse(NotificationType.NEW_MESSAGE);
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toNotificationResponse(any())).thenReturn(mapped);

        service.notifyNewMessage("c", conversation, "Bonjour");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getRecipientId()).isEqualTo("c");
        assertThat(saved.getType()).isEqualTo(NotificationType.NEW_MESSAGE);
        assertThat(saved.getBody()).isEqualTo("Bonjour");
        assertThat(saved.getReferenceId()).isEqualTo(convId.toString());
        verify(messagingTemplate).convertAndSendToUser("c", "/queue/notifications", mapped);
    }

    @Test
    void createPayment_persistsTypeAndAmount_andReturnsResponse() {
        NotificationResponse mapped = sampleResponse(NotificationType.PAYMENT_DUE);
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toNotificationResponse(any())).thenReturn(mapped);

        NotificationResponse result = service.createPayment("client-9", new BigDecimal("150.00"), "Solde à régler");

        assertThat(result).isEqualTo(mapped);
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getRecipientId()).isEqualTo("client-9");
        assertThat(saved.getType()).isEqualTo(NotificationType.PAYMENT_DUE);
        assertThat(saved.getAmount()).isEqualByComparingTo("150.00");
        assertThat(saved.getBody()).isEqualTo("Solde à régler");
        verify(messagingTemplate).convertAndSendToUser("client-9", "/queue/notifications", mapped);
    }

    @Test
    void createBooking_persistsTypeTitleBodyReference_andPushes() {
        UUID sessionId = UUID.randomUUID();
        NotificationResponse mapped = sampleResponse(NotificationType.SESSION_REQUESTED);
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toNotificationResponse(any())).thenReturn(mapped);

        NotificationResponse result = service.createBooking("monitor-1", NotificationType.SESSION_REQUESTED,
                "Nouvelle demande de séance", "Demande de séance de conduite le 10/09/2026.", sessionId.toString());

        assertThat(result).isEqualTo(mapped);
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getRecipientId()).isEqualTo("monitor-1");
        assertThat(saved.getType()).isEqualTo(NotificationType.SESSION_REQUESTED);
        assertThat(saved.getTitle()).isEqualTo("Nouvelle demande de séance");
        assertThat(saved.getBody()).isEqualTo("Demande de séance de conduite le 10/09/2026.");
        assertThat(saved.getReferenceId()).isEqualTo(sessionId.toString());
        verify(messagingTemplate).convertAndSendToUser("monitor-1", "/queue/notifications", mapped);
    }

    @Test
    void createBooking_blankBodyAndReference_storedAsNull() {
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toNotificationResponse(any())).thenReturn(sampleResponse(NotificationType.SESSION_CANCELLED));

        service.createBooking("client-1", NotificationType.SESSION_CANCELLED, "Séance annulée", "  ", "  ");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getBody()).isNull();
        assertThat(captor.getValue().getReferenceId()).isNull();
    }

    @Test
    void createBooking_acceptsExamType_persistsAndPushes() {
        UUID examId = UUID.randomUUID();
        NotificationResponse mapped = sampleResponse(NotificationType.EXAM_PASSED);
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toNotificationResponse(any())).thenReturn(mapped);

        NotificationResponse result = service.createBooking("client-7", NotificationType.EXAM_PASSED,
                "Examen réussi", "Félicitations !", examId.toString());

        assertThat(result).isEqualTo(mapped);
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getRecipientId()).isEqualTo("client-7");
        assertThat(saved.getType()).isEqualTo(NotificationType.EXAM_PASSED);
        assertThat(saved.getReferenceId()).isEqualTo(examId.toString());
        verify(messagingTemplate).convertAndSendToUser("client-7", "/queue/notifications", mapped);
    }

    @Test
    void createBooking_rejectsNonSessionType() {
        assertThatThrownBy(() -> service.createBooking("x", NotificationType.NEW_MESSAGE, "t", "b", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createBooking("x", NotificationType.PAYMENT_DUE, "t", "b", null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(notificationRepository);
    }

    @Test
    void markRead_setsReadAt_whenOwnedAndUnread() {
        UUID id = UUID.randomUUID();
        Notification notification = new Notification();
        notification.setRecipientId("c");
        when(notificationRepository.findByIdAndRecipientId(id, "c")).thenReturn(Optional.of(notification));

        service.markRead(id, "c");

        assertThat(notification.getReadAt()).isNotNull();
    }

    @Test
    void unreadCount_delegatesToRepository() {
        when(notificationRepository.countByRecipientIdAndReadAtIsNull("c")).thenReturn(4L);

        assertThat(service.unreadCount("c")).isEqualTo(4L);
    }

    @Test
    void markAllRead_delegatesToRepository() {
        service.markAllRead("c");

        verify(notificationRepository).markAllRead(eq("c"), any(Instant.class));
    }

    private static NotificationResponse sampleResponse(NotificationType type) {
        return new NotificationResponse(UUID.randomUUID(), type, "t", "b", null, null, null, Instant.now());
    }
}
