package com.example.communicationservice.dev;

import com.example.communicationservice.dev.dto.CommunicationExport;
import com.example.communicationservice.dev.dto.CommunicationSeedRequest;
import com.example.communicationservice.dev.dto.SeedResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints d'administration des données — OUTIL DE DÉVELOPPEMENT (voir auth-service AdminController).
 * N'existe que si {@code app.dev-tools.enabled=true}. Réservé au propriétaire.
 */
@RestController
@RequestMapping("/api/admin/communication")
@ConditionalOnProperty(name = "app.dev-tools.enabled", havingValue = "true")
@PreAuthorize("hasRole('OWNER')")
public class AdminController {

    private final DevDataService devDataService;

    public AdminController(DevDataService devDataService) {
        this.devDataService = devDataService;
    }

    @PostMapping("/seed")
    public SeedResult seed(@RequestBody(required = false) CommunicationSeedRequest request) {
        return devDataService.seed(request);
    }

    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset() {
        devDataService.reset();
    }

    @GetMapping("/export")
    public CommunicationExport export() {
        return devDataService.export();
    }
}
