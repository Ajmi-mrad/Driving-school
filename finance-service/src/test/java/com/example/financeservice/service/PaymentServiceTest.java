package com.example.financeservice.service;

import com.example.financeservice.client.NotificationClient;
import com.example.financeservice.domain.Enrollment;
import com.example.financeservice.domain.EnrollmentStatus;
import com.example.financeservice.domain.InvoiceType;
import com.example.financeservice.domain.Payment;
import com.example.financeservice.domain.PaymentMethod;
import com.example.financeservice.exception.InvalidPaymentException;
import com.example.financeservice.mapper.FinanceMapper;
import com.example.financeservice.repository.EnrollmentRepository;
import com.example.financeservice.repository.PaymentRepository;
import com.example.financeservice.web.dto.CreatePaymentRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires (Mockito) de la logique d'encaissement : mise à jour du solde, émission du reçu,
 * clôture de l'inscription une fois soldée, refus sur inscription annulée, et relance.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private InvoiceService invoiceService;
    @Mock
    private NotificationClient notificationClient;
    @Mock
    private FinanceMapper mapper;

    @InjectMocks
    private PaymentService service;

    private Enrollment activeEnrollment(BigDecimal total, BigDecimal paid) {
        Enrollment e = new Enrollment();
        e.setId(UUID.randomUUID());
        e.setClientId("client-1");
        e.setForfaitId(UUID.randomUUID());
        e.setStatus(EnrollmentStatus.ACTIVE);
        e.setTotalPrice(total);
        e.setAmountPaid(paid);
        e.setEnrolledAt(Instant.now());
        return e;
    }

    @Test
    void fullPaymentCompletesEnrollmentAndIssuesReceipt() {
        Enrollment enrollment = activeEnrollment(new BigDecimal("1000.00"), BigDecimal.ZERO);
        when(enrollmentRepository.findById(enrollment.getId())).thenReturn(Optional.of(enrollment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.record(new CreatePaymentRequest(enrollment.getId(), new BigDecimal("1000.00"),
                PaymentMethod.CARD, "ref-1"));

        assertThat(enrollment.getAmountPaid()).isEqualByComparingTo("1000.00");
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
        verify(invoiceService).issue(eq(enrollment), eq(InvoiceType.RECEIPT), eq(new BigDecimal("1000.00")));
    }

    @Test
    void partialPaymentKeepsEnrollmentActive() {
        Enrollment enrollment = activeEnrollment(new BigDecimal("1000.00"), BigDecimal.ZERO);
        when(enrollmentRepository.findById(enrollment.getId())).thenReturn(Optional.of(enrollment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.record(new CreatePaymentRequest(enrollment.getId(), new BigDecimal("400.00"),
                PaymentMethod.CASH, null));

        assertThat(enrollment.getAmountPaid()).isEqualByComparingTo("400.00");
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
    }

    @Test
    void paymentOnCancelledEnrollmentIsRejected() {
        Enrollment enrollment = activeEnrollment(new BigDecimal("1000.00"), BigDecimal.ZERO);
        enrollment.setStatus(EnrollmentStatus.CANCELLED);
        when(enrollmentRepository.findById(enrollment.getId())).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> service.record(new CreatePaymentRequest(enrollment.getId(),
                new BigDecimal("100.00"), PaymentMethod.CASH, null)))
                .isInstanceOf(InvalidPaymentException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void paymentExceedingOutstandingIsRejected() {
        Enrollment enrollment = activeEnrollment(new BigDecimal("1000.00"), new BigDecimal("800.00"));
        when(enrollmentRepository.findById(enrollment.getId())).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> service.record(new CreatePaymentRequest(enrollment.getId(),
                new BigDecimal("300.00"), PaymentMethod.CASH, null)))
                .isInstanceOf(InvalidPaymentException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void paymentOnCompletedEnrollmentIsRejected() {
        Enrollment enrollment = activeEnrollment(new BigDecimal("1000.00"), new BigDecimal("1000.00"));
        enrollment.setStatus(EnrollmentStatus.COMPLETED);
        when(enrollmentRepository.findById(enrollment.getId())).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> service.record(new CreatePaymentRequest(enrollment.getId(),
                new BigDecimal("100.00"), PaymentMethod.CASH, null)))
                .isInstanceOf(InvalidPaymentException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void remindSendsReminderWhenOutstanding() {
        Enrollment enrollment = activeEnrollment(new BigDecimal("1000.00"), new BigDecimal("300.00"));
        when(enrollmentRepository.findById(enrollment.getId())).thenReturn(Optional.of(enrollment));
        when(notificationClient.sendPaymentReminder(eq("client-1"), eq(new BigDecimal("700.00")), any()))
                .thenReturn(true);

        boolean delivered = service.remind(enrollment.getId(), null);

        assertThat(delivered).isTrue();
        verify(notificationClient).sendPaymentReminder(eq("client-1"), eq(new BigDecimal("700.00")), any());
    }

    @Test
    void remindRejectedWhenNothingDue() {
        Enrollment enrollment = activeEnrollment(new BigDecimal("1000.00"), new BigDecimal("1000.00"));
        when(enrollmentRepository.findById(enrollment.getId())).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> service.remind(enrollment.getId(), null))
                .isInstanceOf(InvalidPaymentException.class);
        verify(notificationClient, never()).sendPaymentReminder(any(), any(), any());
    }
}
