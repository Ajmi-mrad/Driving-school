package com.example.financeservice.web;

import com.example.financeservice.service.ForfaitService;
import com.example.financeservice.web.dto.CreateForfaitRequest;
import com.example.financeservice.web.dto.ForfaitResponse;
import com.example.financeservice.web.dto.UpdateForfaitRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Catalogue des forfaits. Écriture réservée au staff (OWNER/SECRETARY) ; lecture pour tout
 * utilisateur authentifié (un client doit pouvoir consulter l'offre).
 */
@RestController
@RequestMapping("/api/forfaits")
public class ForfaitController {

    private final ForfaitService forfaitService;

    public ForfaitController(ForfaitService forfaitService) {
        this.forfaitService = forfaitService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY')")
    public ResponseEntity<ForfaitResponse> create(@Valid @RequestBody CreateForfaitRequest request) {
        ForfaitResponse created = forfaitService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY')")
    public ForfaitResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateForfaitRequest request) {
        return forfaitService.update(id, request);
    }

    @GetMapping("/{id}")
    public ForfaitResponse get(@PathVariable UUID id) {
        return forfaitService.get(id);
    }

    @GetMapping
    public List<ForfaitResponse> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return forfaitService.list(activeOnly);
    }
}
