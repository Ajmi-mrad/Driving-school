package com.example.vehicleservice.web;

import com.example.vehicleservice.domain.FuelType;
import com.example.vehicleservice.domain.VehicleStatus;
import com.example.vehicleservice.service.VehicleService;
import com.example.vehicleservice.web.dto.CreateVehicleRequest;
import com.example.vehicleservice.web.dto.UpdateStatusRequest;
import com.example.vehicleservice.web.dto.UpdateVehicleRequest;
import com.example.vehicleservice.web.dto.VehicleResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * API de gestion du parc automobile. Lecture : OWNER/SECRETARY/MONITOR. Écriture : OWNER/SECRETARY.
 */
@RestController
@RequestMapping("/api/vehicles")
@PreAuthorize("hasAnyRole('OWNER','SECRETARY','MONITOR')")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY')")
    public ResponseEntity<VehicleResponse> create(@Valid @RequestBody CreateVehicleRequest request) {
        VehicleResponse created = vehicleService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public VehicleResponse get(@PathVariable UUID id) {
        return vehicleService.get(id);
    }

    @GetMapping
    public List<VehicleResponse> list(@RequestParam(required = false) VehicleStatus status,
                                      @RequestParam(required = false) FuelType fuelType) {
        return vehicleService.list(status, fuelType);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY')")
    public VehicleResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateVehicleRequest request) {
        return vehicleService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY')")
    public VehicleResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest request) {
        return vehicleService.updateStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retire(@PathVariable UUID id) {
        vehicleService.retire(id);
    }
}