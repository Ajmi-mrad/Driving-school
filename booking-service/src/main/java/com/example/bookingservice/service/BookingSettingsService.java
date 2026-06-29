package com.example.bookingservice.service;

import com.example.bookingservice.domain.BookingSettings;
import com.example.bookingservice.repository.BookingSettingsRepository;
import com.example.bookingservice.web.dto.BookingSettingsResponse;
import com.example.bookingservice.web.dto.UpdateBookingSettingsRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gère la ligne unique de configuration de planification. Fournit aussi l'entité aux autres services
 * (validation auto, préavis d'annulation).
 */
@Service
public class BookingSettingsService {

    private final BookingSettingsRepository repository;

    public BookingSettingsService(BookingSettingsRepository repository) {
        this.repository = repository;
    }

    /** Entité de configuration (créée avec des valeurs par défaut si absente). */
    @Transactional(readOnly = true)
    public BookingSettings getSettingsEntity() {
        return repository.findById(BookingSettings.SINGLETON_ID).orElseGet(this::defaults);
    }

    @Transactional(readOnly = true)
    public BookingSettingsResponse getSettings() {
        return toResponse(getSettingsEntity());
    }

    @Transactional
    public BookingSettingsResponse update(UpdateBookingSettingsRequest request) {
        BookingSettings settings = repository.findById(BookingSettings.SINGLETON_ID).orElseGet(this::defaults);
        settings.setAutoValidationEnabled(request.autoValidationEnabled());
        settings.setCancellationNoticeHours(request.cancellationNoticeHours());
        return toResponse(repository.save(settings));
    }

    private BookingSettings defaults() {
        BookingSettings settings = new BookingSettings();
        settings.setId(BookingSettings.SINGLETON_ID);
        settings.setAutoValidationEnabled(false);
        settings.setCancellationNoticeHours(24);
        return settings;
    }

    private BookingSettingsResponse toResponse(BookingSettings s) {
        return new BookingSettingsResponse(s.isAutoValidationEnabled(), s.getCancellationNoticeHours());
    }
}