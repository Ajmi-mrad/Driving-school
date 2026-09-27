package com.example.bookingservice.web;

import com.example.bookingservice.domain.ExamStatus;
import com.example.bookingservice.domain.ExamType;
import com.example.bookingservice.service.ExamService;
import com.example.bookingservice.web.dto.CreateExamRequest;
import com.example.bookingservice.web.dto.ExamResponse;
import com.example.bookingservice.web.dto.ExamResultRequest;
import com.example.bookingservice.web.dto.RescheduleExamRequest;
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

/**
 * API des examens officiels (code / conduite). La planification, le report, l'annulation sont
 * réservés au staff ; l'enregistrement d'un résultat est ouvert au moniteur également. La
 * consultation est filtrée par rôle dans le service (élève/moniteur ne voient que leurs examens).
 */
@RestController
@RequestMapping("/api/exams")
public class ExamController {

    private final ExamService examService;

    public ExamController(ExamService examService) {
        this.examService = examService;
    }

    /** Planification d'un examen pour un élève (staff uniquement). */
    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY')")
    public ResponseEntity<ExamResponse> create(@Valid @RequestBody CreateExamRequest request) {
        ExamResponse created = examService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public ExamResponse get(@PathVariable UUID id, JwtAuthenticationToken auth) {
        return examService.get(id, AuthSupport.sub(auth), AuthSupport.roles(auth));
    }

    /** Le staff voit tout ; moniteur et élève ne voient que leurs examens. */
    @GetMapping
    public List<ExamResponse> list(@RequestParam(required = false) ExamStatus status,
                                   @RequestParam(required = false) ExamType type,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                   @RequestParam(required = false) String monitorId,
                                   @RequestParam(required = false) String clientId,
                                   JwtAuthenticationToken auth) {
        return examService.list(status, type, from, to, monitorId, clientId,
                AuthSupport.sub(auth), AuthSupport.roles(auth));
    }

    /** Enregistrement du résultat (staff ou moniteur). */
    @PatchMapping("/{id}/result")
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY','MONITOR')")
    public ExamResponse recordResult(@PathVariable UUID id, @Valid @RequestBody ExamResultRequest request) {
        return examService.recordResult(id, request);
    }

    @PatchMapping("/{id}/reschedule")
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY')")
    public ExamResponse reschedule(@PathVariable UUID id, @Valid @RequestBody RescheduleExamRequest request) {
        return examService.reschedule(id, request.scheduledAt());
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY')")
    public ExamResponse cancel(@PathVariable UUID id) {
        return examService.cancel(id);
    }
}
