package com.example.bookingservice.service;

import com.example.bookingservice.client.CommunicationClient;
import com.example.bookingservice.client.UserClient;
import com.example.bookingservice.client.VehicleClient;
import com.example.bookingservice.client.dto.NotificationRequest;
import com.example.bookingservice.client.dto.UserInfo;
import com.example.bookingservice.client.dto.VehicleInfo;
import com.example.bookingservice.domain.Exam;
import com.example.bookingservice.domain.ExamStatus;
import com.example.bookingservice.domain.ExamType;
import com.example.bookingservice.exception.BookingConflictException;
import com.example.bookingservice.exception.CrossServiceValidationException;
import com.example.bookingservice.exception.InvalidSessionStateException;
import com.example.bookingservice.mapper.ExamMapper;
import com.example.bookingservice.repository.ExamRepository;
import com.example.bookingservice.web.dto.CreateExamRequest;
import com.example.bookingservice.web.dto.ExamResultRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires (Mockito) de la logique des examens : calcul du numéro de tentative, garde-fous
 * d'état (planifié seulement), contrôle d'accès en lecture et routage des notifications {@code EXAM_*}.
 *
 * <p>{@code runAfterCommit} s'exécute immédiatement en l'absence de transaction active (contexte
 * unitaire) : les appels à {@link CommunicationClient#notify} sont donc vérifiables directement.
 */
@ExtendWith(MockitoExtension.class)
class ExamServiceTest {

    private static final String CLIENT_SUB = "client-1";
    private static final String MONITOR_SUB = "monitor-9";
    private static final String STAFF_SUB = "owner-0";

    @Mock
    private ExamRepository examRepository;
    @Mock
    private ExamMapper examMapper;
    @Mock
    private UserClient userClient;
    @Mock
    private VehicleClient vehicleClient;
    @Mock
    private CommunicationClient communicationClient;

    @InjectMocks
    private ExamService service;

    private static UserInfo client(boolean active, String role) {
        return new UserInfo(CLIENT_SUB, "Sam", "Doe", Set.of(role), active);
    }

    private Exam scheduled(ExamType type, String monitorId) {
        Exam exam = new Exam();
        exam.setId(UUID.randomUUID());
        exam.setType(type);
        exam.setClientId(CLIENT_SUB);
        exam.setMonitorId(monitorId);
        exam.setScheduledAt(Instant.now().plus(2, ChronoUnit.DAYS));
        exam.setStatus(ExamStatus.SCHEDULED);
        exam.setAttemptNumber(1);
        return exam;
    }

    // ---- create ----

    @Test
    void create_computesAttemptNumber_andNotifiesClientScheduled() {
        when(userClient.getByKeycloakId(CLIENT_SUB)).thenReturn(Optional.of(client(true, "CLIENT")));
        when(examRepository.existsByClientIdAndTypeAndStatus(CLIENT_SUB, ExamType.CODE, ExamStatus.SCHEDULED))
                .thenReturn(false);
        when(examRepository.countByClientIdAndTypeAndStatusNot(CLIENT_SUB, ExamType.CODE, ExamStatus.CANCELLED))
                .thenReturn(1L);
        when(examRepository.save(any(Exam.class))).thenAnswer(inv -> withId(inv.getArgument(0)));

        service.create(new CreateExamRequest(ExamType.CODE, CLIENT_SUB, null, null,
                Instant.now().plus(3, ChronoUnit.DAYS), "Centre Tunis"));

        ArgumentCaptor<Exam> examCaptor = ArgumentCaptor.forClass(Exam.class);
        verify(examRepository).save(examCaptor.capture());
        Exam saved = examCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ExamStatus.SCHEDULED);
        assertThat(saved.getAttemptNumber()).isEqualTo(2); // 1 prior + 1

        ArgumentCaptor<NotificationRequest> notif = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(communicationClient).notify(notif.capture());
        assertThat(notif.getValue().type()).isEqualTo("EXAM_SCHEDULED");
        assertThat(notif.getValue().recipientId()).isEqualTo(CLIENT_SUB);
    }

    @Test
    void create_rejectsUnknownClient() {
        when(userClient.getByKeycloakId(CLIENT_SUB)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new CreateExamRequest(ExamType.CODE, CLIENT_SUB, null, null,
                Instant.now().plus(1, ChronoUnit.DAYS), null)))
                .isInstanceOf(CrossServiceValidationException.class);
        verify(examRepository, never()).save(any());
        verifyNoInteractions(communicationClient);
    }

    @Test
    void create_rejectsInactiveOrWrongRoleClient() {
        when(userClient.getByKeycloakId(CLIENT_SUB)).thenReturn(Optional.of(client(false, "CLIENT")));

        assertThatThrownBy(() -> service.create(new CreateExamRequest(ExamType.CODE, CLIENT_SUB, null, null,
                Instant.now().plus(1, ChronoUnit.DAYS), null)))
                .isInstanceOf(CrossServiceValidationException.class);
    }

    @Test
    void create_rejectsDuplicateScheduledOfSameType() {
        when(userClient.getByKeycloakId(CLIENT_SUB)).thenReturn(Optional.of(client(true, "CLIENT")));
        when(examRepository.existsByClientIdAndTypeAndStatus(CLIENT_SUB, ExamType.CODE, ExamStatus.SCHEDULED))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateExamRequest(ExamType.CODE, CLIENT_SUB, null, null,
                Instant.now().plus(1, ChronoUnit.DAYS), null)))
                .isInstanceOf(BookingConflictException.class);
        verify(examRepository, never()).save(any());
    }

    @Test
    void create_rejectsVehicleOnCodeExam() {
        when(userClient.getByKeycloakId(CLIENT_SUB)).thenReturn(Optional.of(client(true, "CLIENT")));
        when(examRepository.existsByClientIdAndTypeAndStatus(CLIENT_SUB, ExamType.CODE, ExamStatus.SCHEDULED))
                .thenReturn(false);

        assertThatThrownBy(() -> service.create(new CreateExamRequest(ExamType.CODE, CLIENT_SUB, null,
                UUID.randomUUID(), Instant.now().plus(1, ChronoUnit.DAYS), null)))
                .isInstanceOf(CrossServiceValidationException.class);
        verify(examRepository, never()).save(any());
    }

    @Test
    void create_drivingWithVehicle_validatesVehicle() {
        UUID vehicleId = UUID.randomUUID();
        when(userClient.getByKeycloakId(CLIENT_SUB)).thenReturn(Optional.of(client(true, "CLIENT")));
        when(examRepository.existsByClientIdAndTypeAndStatus(CLIENT_SUB, ExamType.DRIVING, ExamStatus.SCHEDULED))
                .thenReturn(false);
        when(vehicleClient.get(vehicleId)).thenReturn(Optional.of(new VehicleInfo(vehicleId, "AVAILABLE")));
        when(examRepository.countByClientIdAndTypeAndStatusNot(CLIENT_SUB, ExamType.DRIVING, ExamStatus.CANCELLED))
                .thenReturn(0L);
        when(examRepository.save(any(Exam.class))).thenAnswer(inv -> withId(inv.getArgument(0)));

        service.create(new CreateExamRequest(ExamType.DRIVING, CLIENT_SUB, null, vehicleId,
                Instant.now().plus(1, ChronoUnit.DAYS), null));

        ArgumentCaptor<Exam> examCaptor = ArgumentCaptor.forClass(Exam.class);
        verify(examRepository).save(examCaptor.capture());
        assertThat(examCaptor.getValue().getVehicleId()).isEqualTo(vehicleId);
        assertThat(examCaptor.getValue().getAttemptNumber()).isEqualTo(1);
    }

    // ---- recordResult ----

    @Test
    void recordResult_rejectsInvalidOutcome_beforeLoadingExam() {
        assertThatThrownBy(() -> service.recordResult(UUID.randomUUID(),
                new ExamResultRequest(ExamStatus.SCHEDULED, null)))
                .isInstanceOf(InvalidSessionStateException.class);
        verify(examRepository, never()).findById(any());
    }

    @Test
    void recordResult_rejectsWhenExamNotScheduled() {
        Exam exam = scheduled(ExamType.DRIVING, MONITOR_SUB);
        exam.setStatus(ExamStatus.PASSED);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));

        assertThatThrownBy(() -> service.recordResult(exam.getId(),
                new ExamResultRequest(ExamStatus.FAILED, null)))
                .isInstanceOf(InvalidSessionStateException.class);
    }

    @Test
    void recordResult_passed_setsStatusAndNotifies() {
        Exam exam = scheduled(ExamType.DRIVING, MONITOR_SUB);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(examRepository.save(any(Exam.class))).thenAnswer(inv -> inv.getArgument(0));

        service.recordResult(exam.getId(), new ExamResultRequest(ExamStatus.PASSED, "Bravo"));

        assertThat(exam.getStatus()).isEqualTo(ExamStatus.PASSED);
        assertThat(exam.getResultNote()).isEqualTo("Bravo");
        assertNotifiedWithType("EXAM_PASSED");
    }

    @Test
    void recordResult_failed_notifiesExamFailed() {
        Exam exam = scheduled(ExamType.CODE, null);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(examRepository.save(any(Exam.class))).thenAnswer(inv -> inv.getArgument(0));

        service.recordResult(exam.getId(), new ExamResultRequest(ExamStatus.FAILED, null));

        assertThat(exam.getStatus()).isEqualTo(ExamStatus.FAILED);
        assertNotifiedWithType("EXAM_FAILED");
    }

    @Test
    void recordResult_noShow_notifiesExamFailed() {
        Exam exam = scheduled(ExamType.DRIVING, MONITOR_SUB);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(examRepository.save(any(Exam.class))).thenAnswer(inv -> inv.getArgument(0));

        service.recordResult(exam.getId(), new ExamResultRequest(ExamStatus.NO_SHOW, null));

        assertThat(exam.getStatus()).isEqualTo(ExamStatus.NO_SHOW);
        assertNotifiedWithType("EXAM_FAILED");
    }

    // ---- reschedule / cancel ----

    @Test
    void reschedule_updatesTime_andNotifies() {
        Exam exam = scheduled(ExamType.DRIVING, MONITOR_SUB);
        Instant newWhen = Instant.now().plus(9, ChronoUnit.DAYS);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(examRepository.save(any(Exam.class))).thenAnswer(inv -> inv.getArgument(0));

        service.reschedule(exam.getId(), newWhen);

        assertThat(exam.getScheduledAt()).isEqualTo(newWhen);
        assertNotifiedWithType("EXAM_RESCHEDULED");
    }

    @Test
    void reschedule_rejectsWhenNotScheduled() {
        Exam exam = scheduled(ExamType.DRIVING, MONITOR_SUB);
        exam.setStatus(ExamStatus.CANCELLED);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));

        assertThatThrownBy(() -> service.reschedule(exam.getId(), Instant.now().plus(1, ChronoUnit.DAYS)))
                .isInstanceOf(InvalidSessionStateException.class);
        verify(communicationClient, never()).notify(any());
    }

    @Test
    void cancel_setsCancelled_andNotifies() {
        Exam exam = scheduled(ExamType.CODE, null);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(examRepository.save(any(Exam.class))).thenAnswer(inv -> inv.getArgument(0));

        service.cancel(exam.getId());

        assertThat(exam.getStatus()).isEqualTo(ExamStatus.CANCELLED);
        assertNotifiedWithType("EXAM_CANCELLED");
    }

    // ---- get access control ----

    @Test
    void get_allowsAssignedMonitor() {
        Exam exam = scheduled(ExamType.DRIVING, MONITOR_SUB);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));

        service.get(exam.getId(), MONITOR_SUB, Set.of("MONITOR"));

        verify(examMapper).toResponse(exam);
    }

    @Test
    void get_deniesUnrelatedClient() {
        Exam exam = scheduled(ExamType.DRIVING, MONITOR_SUB);
        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));

        assertThatThrownBy(() -> service.get(exam.getId(), "intruder", Set.of("CLIENT")))
                .isInstanceOf(AccessDeniedException.class);
        verify(examMapper, never()).toResponse(any());
    }

    // ---- list ----

    @Test
    void list_returnsMappedResults_sortedByScheduledAtAsc() {
        Exam a = scheduled(ExamType.CODE, null);
        Exam b = scheduled(ExamType.DRIVING, MONITOR_SUB);
        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        when(examRepository.findAll(any(Specification.class), sortCaptor.capture()))
                .thenReturn(List.of(a, b));

        service.list(null, null, null, null, null, null, CLIENT_SUB, Set.of("CLIENT"));

        Sort.Order order = sortCaptor.getValue().getOrderFor("scheduledAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
        verify(examMapper).toResponse(a);
        verify(examMapper).toResponse(b);
    }

    // ---- helpers ----

    /** Simulate the DB assigning a generated id on save (needed for the notification referenceId). */
    private static Exam withId(Exam exam) {
        if (exam.getId() == null) {
            exam.setId(UUID.randomUUID());
        }
        return exam;
    }

    private void assertNotifiedWithType(String expectedType) {
        ArgumentCaptor<NotificationRequest> notif = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(communicationClient).notify(notif.capture());
        assertThat(notif.getValue().type()).isEqualTo(expectedType);
        assertThat(notif.getValue().recipientId()).isEqualTo(CLIENT_SUB);
    }
}
