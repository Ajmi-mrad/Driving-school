package com.example.financeservice.web;

import com.example.financeservice.service.EnrollmentService;
import com.example.financeservice.web.dto.ConsumeRequest;
import com.example.financeservice.web.dto.CreateEnrollmentRequest;
import com.example.financeservice.web.dto.EnrollmentResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Inscriptions à un forfait. Création et décompte de consommation réservés au staff (le décompte est
 * appelé par le booking-service via son compte de service). Consultation : staff = tout ;
 * client = ses propres inscriptions.
 */
@RestController
@RequestMapping("/api/enrollments")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY')")
    public ResponseEntity<EnrollmentResponse> create(@Valid @RequestBody CreateEnrollmentRequest request) {
        EnrollmentResponse created = enrollmentService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY','CLIENT')")
    public EnrollmentResponse get(@PathVariable UUID id, JwtAuthenticationToken auth) {
        return enrollmentService.get(id, AuthSupport.sub(auth), AuthSupport.roles(auth));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY','CLIENT')")
    public List<EnrollmentResponse> list(@RequestParam(required = false) String clientId,
                                         JwtAuthenticationToken auth) {
        return enrollmentService.list(clientId, AuthSupport.sub(auth), AuthSupport.roles(auth));
    }

    /** Décompte de consommation d'une séance réalisée (appelé par le booking-service). */
    @PostMapping("/consume")
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY')")
    public ResponseEntity<Void> consume(@Valid @RequestBody ConsumeRequest request) {
        enrollmentService.consume(request);
        return ResponseEntity.noContent().build();
    }
}
