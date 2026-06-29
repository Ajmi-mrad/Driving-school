package com.example.bookingservice.repository;

import com.example.bookingservice.domain.BookingSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BookingSettingsRepository extends JpaRepository<BookingSettings, UUID> {
}