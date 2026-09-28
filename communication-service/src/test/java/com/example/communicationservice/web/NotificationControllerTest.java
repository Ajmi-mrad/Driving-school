package com.example.communicationservice.web;

import com.example.communicationservice.config.SecurityConfig;
import com.example.communicationservice.domain.NotificationType;
import com.example.communicationservice.service.NotificationService;
import com.example.communicationservice.web.dto.NotificationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de sécurité (tranche MVC) de l'endpoint interne {@code POST /api/notifications/booking} :
 * réservé au staff et aux appels de service ({@code SERVICE}, jeton client_credentials du
 * booking-service). Le {@link NotificationService} est mocké.
 */
@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
class NotificationControllerTest {

    private static final String BODY = """
            {"recipientId":"monitor-1","type":"SESSION_REQUESTED","title":"Nouvelle demande","body":"…","referenceId":"s-1"}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private NotificationService notificationService;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void booking_withoutToken_isUnauthorized() throws Exception {
        mvc.perform(post("/api/notifications/booking")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verify(notificationService, never()).createBooking(any(), any(), any(), any(), any());
    }

    @Test
    void booking_asClient_isForbidden() throws Exception {
        mvc.perform(post("/api/notifications/booking").with(role("CLIENT"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verify(notificationService, never()).createBooking(any(), any(), any(), any(), any());
    }

    @Test
    void booking_asService_isCreated() throws Exception {
        when(notificationService.createBooking(any(), any(), any(), any(), any()))
                .thenReturn(new NotificationResponse(UUID.randomUUID(), NotificationType.SESSION_REQUESTED,
                        "Nouvelle demande", "…", "s-1", null, null, Instant.now()));

        mvc.perform(post("/api/notifications/booking").with(role("SERVICE"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());

        verify(notificationService).createBooking(eq("monitor-1"), eq(NotificationType.SESSION_REQUESTED),
                eq("Nouvelle demande"), eq("…"), eq("s-1"));
    }

    @Test
    void booking_missingRecipient_isBadRequest() throws Exception {
        String invalid = """
                {"type":"SESSION_REQUESTED","title":"Nouvelle demande"}
                """;
        mvc.perform(post("/api/notifications/booking").with(role("SERVICE"))
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest());
        verify(notificationService, never()).createBooking(any(), any(), any(), any(), any());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor role(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
