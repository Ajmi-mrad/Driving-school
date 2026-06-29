package com.example.vehicleservice.web;

import com.example.vehicleservice.service.MaintenanceService;
import com.example.vehicleservice.web.dto.CreateMaintenanceRequest;
import com.example.vehicleservice.web.dto.MaintenanceResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Suivi de l'entretien d'un véhicule. Lecture : OWNER/SECRETARY/MONITOR. Écriture : OWNER/SECRETARY.
 */
@RestController
@RequestMapping("/api/vehicles/{vehicleId}/maintenance")
@PreAuthorize("hasAnyRole('OWNER','SECRETARY','MONITOR')")
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    public MaintenanceController(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY')")
    @ResponseStatus(HttpStatus.CREATED)
    public MaintenanceResponse add(@PathVariable UUID vehicleId,
                                   @Valid @RequestBody CreateMaintenanceRequest request) {
        return maintenanceService.addToVehicle(vehicleId, request);
    }

    @GetMapping
    public List<MaintenanceResponse> list(@PathVariable UUID vehicleId) {
        return maintenanceService.listForVehicle(vehicleId);
    }
}