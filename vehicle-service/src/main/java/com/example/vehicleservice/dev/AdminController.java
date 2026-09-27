package com.example.vehicleservice.dev;

import com.example.vehicleservice.dev.dto.SeedVehiclesResponse;
import com.example.vehicleservice.dev.dto.VehicleExport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints d'administration des données — OUTIL DE DÉVELOPPEMENT (voir auth-service AdminController).
 * N'existe que si {@code app.dev-tools.enabled=true}. Réservé au propriétaire.
 */
@RestController
@RequestMapping("/api/admin/vehicle")
@ConditionalOnProperty(name = "app.dev-tools.enabled", havingValue = "true")
@PreAuthorize("hasRole('OWNER')")
public class AdminController {

    private final DevDataService devDataService;

    public AdminController(DevDataService devDataService) {
        this.devDataService = devDataService;
    }

    @PostMapping("/seed")
    public SeedVehiclesResponse seed() {
        return devDataService.seed();
    }

    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset() {
        devDataService.reset();
    }

    @GetMapping("/export")
    public VehicleExport export() {
        return devDataService.export();
    }
}
