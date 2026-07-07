package com.example.financeservice.service;

import com.example.financeservice.client.UserClient;
import com.example.financeservice.client.dto.UserInfo;
import com.example.financeservice.domain.Enrollment;
import com.example.financeservice.domain.EnrollmentStatus;
import com.example.financeservice.domain.Forfait;
import com.example.financeservice.domain.InvoiceType;
import com.example.financeservice.mapper.FinanceMapper;
import com.example.financeservice.repository.EnrollmentRepository;
import com.example.financeservice.repository.ForfaitRepository;
import com.example.financeservice.web.dto.ConsumeRequest;
import com.example.financeservice.web.dto.CreateEnrollmentRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires (Mockito) des inscriptions : validation du client, recopie des quotas du forfait
 * et émission de facture à l'achat ; décompte de consommation sur l'inscription active.
 */
@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private ForfaitRepository forfaitRepository;
    @Mock
    private InvoiceService invoiceService;
    @Mock
    private UserClient userClient;
    @Mock
    private FinanceMapper mapper;

    @InjectMocks
    private EnrollmentService service;

    private Forfait forfait() {
        Forfait f = new Forfait();
        f.setId(UUID.randomUUID());
        f.setName("Pack B - 20h");
        f.setDrivingHours(20);
        f.setCodeSessions(10);
        f.setPrice(new BigDecimal("1200.00"));
        f.setActive(true);
        return f;
    }

    @Test
    void createCopiesQuotasAndIssuesInvoice() {
        Forfait forfait = forfait();
        when(userClient.getByKeycloakId("client-1"))
                .thenReturn(Optional.of(new UserInfo("client-1", "Sam", "Doe", "sam@x.io")));
        when(forfaitRepository.findById(forfait.getId())).thenReturn(Optional.of(forfait));
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(new CreateEnrollmentRequest("client-1", forfait.getId()));

        ArgumentCaptor<Enrollment> captor = ArgumentCaptor.forClass(Enrollment.class);
        verify(enrollmentRepository).save(captor.capture());
        Enrollment saved = captor.getValue();
        assertThat(saved.getRemainingDrivingHours()).isEqualTo(20);
        assertThat(saved.getRemainingCodeSessions()).isEqualTo(10);
        assertThat(saved.getTotalPrice()).isEqualByComparingTo("1200.00");
        assertThat(saved.getStatus()).isEqualTo(EnrollmentStatus.ACTIVE);
        verify(invoiceService).issue(eq(saved), eq(InvoiceType.INVOICE), eq(new BigDecimal("1200.00")));
    }

    @Test
    void createProceedsWhenClientLookupUnavailable() {
        // L'auth-service n'expose pas encore la résolution par identifiant Keycloak aux comptes de
        // service : la validation est « best-effort » et n'empêche pas l'inscription.
        Forfait forfait = forfait();
        when(userClient.getByKeycloakId("ghost")).thenReturn(Optional.empty());
        when(forfaitRepository.findById(forfait.getId())).thenReturn(Optional.of(forfait));
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(new CreateEnrollmentRequest("ghost", forfait.getId()));

        verify(enrollmentRepository).save(any(Enrollment.class));
    }

    @Test
    void consumeDeductsFromActiveEnrollment() {
        Enrollment enrollment = new Enrollment();
        enrollment.setId(UUID.randomUUID());
        enrollment.setClientId("client-1");
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        enrollment.setRemainingDrivingHours(20);
        enrollment.setRemainingCodeSessions(10);
        enrollment.setTotalPrice(new BigDecimal("1200.00"));
        enrollment.setAmountPaid(BigDecimal.ZERO);
        enrollment.setEnrolledAt(Instant.now());
        when(enrollmentRepository.findFirstByClientIdAndStatusOrderByEnrolledAtAsc("client-1", EnrollmentStatus.ACTIVE))
                .thenReturn(Optional.of(enrollment));

        service.consume(new ConsumeRequest("client-1", 2, 0));

        assertThat(enrollment.getRemainingDrivingHours()).isEqualTo(18);
        assertThat(enrollment.getRemainingCodeSessions()).isEqualTo(10);
    }

    @Test
    void consumeNeverGoesNegative() {
        Enrollment enrollment = new Enrollment();
        enrollment.setId(UUID.randomUUID());
        enrollment.setClientId("client-1");
        enrollment.setStatus(EnrollmentStatus.ACTIVE);
        enrollment.setRemainingDrivingHours(1);
        enrollment.setRemainingCodeSessions(0);
        enrollment.setTotalPrice(new BigDecimal("1200.00"));
        enrollment.setAmountPaid(BigDecimal.ZERO);
        enrollment.setEnrolledAt(Instant.now());
        when(enrollmentRepository.findFirstByClientIdAndStatusOrderByEnrolledAtAsc("client-1", EnrollmentStatus.ACTIVE))
                .thenReturn(Optional.of(enrollment));

        service.consume(new ConsumeRequest("client-1", 3, 0));

        assertThat(enrollment.getRemainingDrivingHours()).isZero();
    }
}
