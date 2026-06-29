package com.example.bookingservice.web;

import com.example.bookingservice.service.BookingSettingsService;
import com.example.bookingservice.web.dto.BookingSettingsResponse;
import com.example.bookingservice.web.dto.UpdateBookingSettingsRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Configuration de planification (validation auto, préavis). Lecture staff, écriture propriétaire. */
@RestController
@RequestMapping("/api/booking-settings")
public class BookingSettingsController {

    private final BookingSettingsService settingsService;

    public BookingSettingsController(BookingSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY')")
    public BookingSettingsResponse get() {
        return settingsService.getSettings();
    }

    @PutMapping
    @PreAuthorize("hasRole('OWNER')")
    public BookingSettingsResponse update(@Valid @RequestBody UpdateBookingSettingsRequest request) {
        return settingsService.update(request);
    }
}