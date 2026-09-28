package com.example.bookingservice.web;

import com.example.bookingservice.service.MonitorAvailabilityService;
import com.example.bookingservice.web.dto.AvailabilityRuleResponse;
import com.example.bookingservice.web.dto.CreateTimeOffRequest;
import com.example.bookingservice.web.dto.FreeSlotResponse;
import com.example.bookingservice.web.dto.TimeOffResponse;
import com.example.bookingservice.web.dto.UpdateAvailabilityRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * API de disponibilité des moniteurs : heures de travail hebdomadaires, absences, et créneaux
 * réservables calculés. Le staff gère n'importe quel moniteur ; un moniteur ne gère que le sien
 * (contrôle fin dans le service). La consultation ({@code availability}, {@code time-off}) et le
 * calcul des créneaux ({@code free-slots}) sont ouverts à tout appelant authentifié — un élève en a
 * besoin pour réserver.
 */
@RestController
@RequestMapping("/api/monitors/{monitorId}")
public class MonitorAvailabilityController {

    private final MonitorAvailabilityService availabilityService;

    public MonitorAvailabilityController(MonitorAvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping("/availability")
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY','MONITOR')")
    public List<AvailabilityRuleResponse> getAvailability(@PathVariable String monitorId,
                                                          JwtAuthenticationToken auth) {
        return availabilityService.getAvailability(monitorId, AuthSupport.sub(auth), AuthSupport.roles(auth));
    }

    /** Remplace en bloc les heures de travail (staff, ou le moniteur pour lui-même). */
    @PutMapping("/availability")
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY','MONITOR')")
    public List<AvailabilityRuleResponse> replaceAvailability(@PathVariable String monitorId,
                                                              @Valid @RequestBody UpdateAvailabilityRequest request,
                                                              JwtAuthenticationToken auth) {
        return availabilityService.replaceAvailability(monitorId, request.rules(),
                AuthSupport.sub(auth), AuthSupport.roles(auth));
    }

    @GetMapping("/time-off")
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY','MONITOR')")
    public List<TimeOffResponse> listTimeOff(@PathVariable String monitorId, JwtAuthenticationToken auth) {
        return availabilityService.listTimeOff(monitorId, AuthSupport.sub(auth), AuthSupport.roles(auth));
    }

    @PostMapping("/time-off")
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY','MONITOR')")
    public ResponseEntity<TimeOffResponse> createTimeOff(@PathVariable String monitorId,
                                                         @Valid @RequestBody CreateTimeOffRequest request,
                                                         JwtAuthenticationToken auth) {
        TimeOffResponse created = availabilityService.createTimeOff(monitorId, request,
                AuthSupport.sub(auth), AuthSupport.roles(auth));
        return ResponseEntity.status(201).body(created);
    }

    @DeleteMapping("/time-off/{timeOffId}")
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY','MONITOR')")
    public ResponseEntity<Void> deleteTimeOff(@PathVariable String monitorId, @PathVariable UUID timeOffId,
                                              JwtAuthenticationToken auth) {
        availabilityService.deleteTimeOff(monitorId, timeOffId, AuthSupport.sub(auth), AuthSupport.roles(auth));
        return ResponseEntity.noContent().build();
    }

    /**
     * Créneaux réservables calculés sur {@code [from, to)}. Consommé par la réservation et le futur
     * assistant de planification par IA. {@code slotMinutes} par défaut : 60.
     */
    @GetMapping("/free-slots")
    public List<FreeSlotResponse> freeSlots(
            @PathVariable String monitorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "60") int slotMinutes) {
        return availabilityService.computeFreeSlots(monitorId, from, to, slotMinutes);
    }
}
