package com.example.bookingservice.web;

import com.example.bookingservice.domain.SessionStatus;
import com.example.bookingservice.domain.SessionType;
import com.example.bookingservice.service.SessionService;
import com.example.bookingservice.web.dto.CreateSessionRequest;
import com.example.bookingservice.web.dto.RescheduleRequest;
import com.example.bookingservice.web.dto.SessionResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /** Réservation d'une séance (élève pour lui-même, ou staff pour un élève). */
    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY','CLIENT')")
    public ResponseEntity<SessionResponse> create(@Valid @RequestBody CreateSessionRequest request,
                                                  JwtAuthenticationToken auth) {
        SessionResponse created = sessionService.create(request, AuthSupport.sub(auth), AuthSupport.roles(auth));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public SessionResponse get(@PathVariable UUID id, JwtAuthenticationToken auth) {
        return sessionService.get(id, AuthSupport.sub(auth), AuthSupport.roles(auth));
    }

    /** Calendrier : le staff voit tout ; moniteur et élève ne voient que leurs séances. */
    @GetMapping
    public List<SessionResponse> list(@RequestParam(required = false) SessionStatus status,
                                      @RequestParam(required = false) SessionType type,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                      @RequestParam(required = false) String monitorId,
                                      @RequestParam(required = false) String clientId,
                                      JwtAuthenticationToken auth) {
        return sessionService.list(status, type, from, to, monitorId, clientId,
                AuthSupport.sub(auth), AuthSupport.roles(auth));
    }

    @PatchMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY','MONITOR')")
    public SessionResponse confirm(@PathVariable UUID id) {
        return sessionService.confirm(id);
    }

    @PatchMapping("/{id}/refuse")
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY','MONITOR')")
    public SessionResponse refuse(@PathVariable UUID id) {
        return sessionService.refuse(id);
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY','CLIENT')")
    public SessionResponse cancel(@PathVariable UUID id, JwtAuthenticationToken auth) {
        return sessionService.cancel(id, AuthSupport.sub(auth), AuthSupport.roles(auth));
    }

    @PatchMapping("/{id}/reschedule")
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY','CLIENT')")
    public SessionResponse reschedule(@PathVariable UUID id, @Valid @RequestBody RescheduleRequest request,
                                      JwtAuthenticationToken auth) {
        return sessionService.reschedule(id, request, AuthSupport.sub(auth), AuthSupport.roles(auth));
    }

    /** Clôture d'une séance réalisée : signale la consommation au finance-service. */
    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY','MONITOR')")
    public SessionResponse complete(@PathVariable UUID id) {
        return sessionService.complete(id);
    }
}