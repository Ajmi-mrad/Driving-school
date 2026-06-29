package com.example.communicationservice.web;

import com.example.communicationservice.config.SecurityConfig;
import com.example.communicationservice.exception.NotAParticipantException;
import com.example.communicationservice.service.ConversationService;
import com.example.communicationservice.ws.PresenceRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de sécurité (tranche MVC) du contrôleur REST : on vérifie le RBAC porté par @PreAuthorize et
 * le 403 « non-participant » remonté par le service via le {@code GlobalExceptionHandler}.
 *
 * <p>Le post-processeur {@code jwt(...)} pose directement un {@code JwtAuthenticationToken} dans le
 * contexte (pas de vraie validation), mais la {@code SecurityFilterChain} a quand même besoin d'un
 * bean {@link JwtDecoder} au démarrage : d'où son mock.
 */
@WebMvcTest(ConversationController.class)
@Import(SecurityConfig.class)
class ConversationControllerSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ConversationService conversationService;
    @MockitoBean
    private PresenceRegistry presenceRegistry;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void list_withoutToken_isUnauthorized() throws Exception {
        mvc.perform(get("/api/conversations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_asClient_isOk() throws Exception {
        when(conversationService.listForUser(any())).thenReturn(List.of());

        mvc.perform(get("/api/conversations")
                        .with(jwt().jwt(b -> b.subject("client-1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CLIENT"))))
                .andExpect(status().isOk());

        verify(conversationService).listForUser("client-1");
    }

    @Test
    void list_asMonitor_isOk() throws Exception {
        when(conversationService.listForUser(any())).thenReturn(List.of());

        mvc.perform(get("/api/conversations")
                        .with(jwt().jwt(b -> b.subject("monitor-1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_MONITOR"))))
                .andExpect(status().isOk());
    }

    @Test
    void list_asOwner_isForbidden() throws Exception {
        mvc.perform(get("/api/conversations")
                        .with(jwt().jwt(b -> b.subject("owner-1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_OWNER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void history_whenNotParticipant_isForbidden() throws Exception {
        when(conversationService.getMessages(any(UUID.class), any(), any(Pageable.class)))
                .thenThrow(new NotAParticipantException());

        mvc.perform(get("/api/conversations/{id}/messages", UUID.randomUUID())
                        .with(jwt().jwt(b -> b.subject("intrus"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CLIENT"))))
                .andExpect(status().isForbidden());
    }
}