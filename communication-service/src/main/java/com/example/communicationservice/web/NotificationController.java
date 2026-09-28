package com.example.communicationservice.web;

import com.example.communicationservice.service.NotificationService;
import com.example.communicationservice.web.dto.CreateBookingNotificationRequest;
import com.example.communicationservice.web.dto.CreatePaymentNotificationRequest;
import com.example.communicationservice.web.dto.NotificationResponse;
import com.example.communicationservice.web.dto.UnreadCountResponse;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * API REST des notifications in-app : fil de l'utilisateur, badge de non-lus, accusés de lecture,
 * et envoi manuel d'un rappel de paiement (réservé aux administrateurs). Le temps réel (push live)
 * passe par STOMP sur {@code /user/queue/notifications}, alimenté par {@code NotificationService}.
 */
@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("hasAnyRole('OWNER','SECRETARY','MONITOR','CLIENT')")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public Page<NotificationResponse> list(@PageableDefault(size = 20) @ParameterObject Pageable pageable,
                                           JwtAuthenticationToken auth) {
        return notificationService.list(AuthSupport.sub(auth), pageable);
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(JwtAuthenticationToken auth) {
        return new UnreadCountResponse(notificationService.unreadCount(AuthSupport.sub(auth)));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID id, JwtAuthenticationToken auth) {
        notificationService.markRead(id, AuthSupport.sub(auth));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllRead(JwtAuthenticationToken auth) {
        notificationService.markAllRead(AuthSupport.sub(auth));
        return ResponseEntity.noContent().build();
    }

    /**
     * Notification liée à une séance (types {@code SESSION_*}). Endpoint interne : appelé par le
     * booking-service avec un jeton {@code client_credentials} (rôle {@code SERVICE}) lors d'une
     * demande de séance ou d'une décision (validation / refus / report / annulation). Surcharge la
     * règle de la classe.
     */
    @PostMapping("/booking")
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY','SERVICE')")
    public ResponseEntity<NotificationResponse> sendBooking(@Valid @RequestBody CreateBookingNotificationRequest request) {
        NotificationResponse created = notificationService.createBooking(
                request.recipientId(), request.type(), request.title(), request.body(), request.referenceId());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .replacePath("/api/notifications/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * Rappel de paiement destiné à un client. Endpoint interne : appelé par le finance-service
     * (bouton « Envoyer un rappel ») avec un jeton client_credentials (rôle {@code SERVICE}),
     * ou directement par le staff. Surcharge la règle de la classe.
     */
    @PostMapping("/payment")
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY','SERVICE')")
    public ResponseEntity<NotificationResponse> sendPayment(@Valid @RequestBody CreatePaymentNotificationRequest request) {
        NotificationResponse created = notificationService.createPayment(
                request.clientId(), request.amount(), request.message());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .replacePath("/api/notifications/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }
}
