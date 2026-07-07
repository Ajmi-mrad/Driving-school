package com.example.communicationservice.web;

import com.example.communicationservice.service.ConversationService;
import com.example.communicationservice.web.dto.ConversationResponse;
import com.example.communicationservice.web.dto.CreateConversationRequest;
import com.example.communicationservice.web.dto.MessageResponse;
import com.example.communicationservice.web.dto.PresenceResponse;
import com.example.communicationservice.web.dto.UnreadCountResponse;
import com.example.communicationservice.ws.PresenceRegistry;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * API REST de la messagerie : inbox, ouverture de conversation, historique, accusés de lecture,
 * compteur de non-lus et présence. Le temps réel (envoi/réception live) passe par STOMP
 * ({@code ChatController}), pas par cette API. Seuls moniteurs et élèves participent au chat.
 */
@RestController
@RequestMapping("/api/conversations")
@PreAuthorize("hasAnyRole('MONITOR','CLIENT')")
public class ConversationController {

    private final ConversationService conversationService;
    private final PresenceRegistry presenceRegistry;

    public ConversationController(ConversationService conversationService, PresenceRegistry presenceRegistry) {
        this.conversationService = conversationService;
        this.presenceRegistry = presenceRegistry;
    }

    @GetMapping
    public List<ConversationResponse> list(JwtAuthenticationToken auth) {
        return conversationService.listForUser(AuthSupport.sub(auth));
    }

    @PostMapping
    public ResponseEntity<ConversationResponse> create(@Valid @RequestBody CreateConversationRequest request,
                                                       JwtAuthenticationToken auth) {
        ConversationResponse created = conversationService.getOrCreate(
                AuthSupport.sub(auth), AuthSupport.roles(auth), request.counterpartId());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(JwtAuthenticationToken auth) {
        return new UnreadCountResponse(conversationService.unreadCount(AuthSupport.sub(auth)));
    }

    /** Parmi les identifiants demandés, lesquels sont actuellement connectés. */
    @GetMapping("/presence")
    public PresenceResponse presence(@RequestParam Set<String> ids) {
        return new PresenceResponse(presenceRegistry.filterOnline(ids));
    }

    @GetMapping("/{id}/messages")
    public Page<MessageResponse> messages(@PathVariable UUID id,
                                          @PageableDefault(size = 30) @ParameterObject Pageable pageable,
                                          JwtAuthenticationToken auth) {
        return conversationService.getMessages(id, AuthSupport.sub(auth), pageable);
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id, JwtAuthenticationToken auth) {
        conversationService.markRead(id, AuthSupport.sub(auth));
        return ResponseEntity.noContent().build();
    }
}