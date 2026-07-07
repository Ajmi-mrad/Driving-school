package com.example.financeservice.web;

import com.example.financeservice.service.PaymentService;
import com.example.financeservice.web.dto.CreatePaymentRequest;
import com.example.financeservice.web.dto.PaymentResponse;
import com.example.financeservice.web.dto.RemindRequest;
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
 * Encaissements et relances. Enregistrement d'un paiement et relance réservés au staff ;
 * consultation : staff = tout ; client = ses propres paiements.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY')")
    public ResponseEntity<PaymentResponse> record(@Valid @RequestBody CreatePaymentRequest request) {
        PaymentResponse created = paymentService.record(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY','CLIENT')")
    public List<PaymentResponse> list(@RequestParam(required = false) String clientId,
                                      JwtAuthenticationToken auth) {
        return paymentService.list(clientId, AuthSupport.sub(auth), AuthSupport.roles(auth));
    }

    /** Relance d'un impayé : notifie le client du solde restant dû. */
    @PostMapping("/{enrollmentId}/remind")
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY')")
    public ResponseEntity<Void> remind(@PathVariable UUID enrollmentId,
                                       @Valid @RequestBody(required = false) RemindRequest request) {
        String message = request == null ? null : request.message();
        boolean delivered = paymentService.remind(enrollmentId, message);
        return delivered ? ResponseEntity.noContent().build()
                : ResponseEntity.status(502).build();
    }
}
