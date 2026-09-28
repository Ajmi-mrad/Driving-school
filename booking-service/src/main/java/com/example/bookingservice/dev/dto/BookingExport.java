package com.example.bookingservice.dev.dto;

import com.example.bookingservice.web.dto.BookingSettingsResponse;
import com.example.bookingservice.web.dto.ExamResponse;
import com.example.bookingservice.web.dto.SessionResponse;

import java.util.List;

/** Export JSON des données booking (séances + examens + paramètres). */
public record BookingExport(List<SessionResponse> sessions, List<ExamResponse> exams,
                            BookingSettingsResponse settings) {
}
