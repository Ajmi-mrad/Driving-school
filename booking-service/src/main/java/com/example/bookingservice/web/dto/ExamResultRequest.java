package com.example.bookingservice.web.dto;

import com.example.bookingservice.domain.ExamStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Enregistrement du résultat d'un examen planifié. {@code outcome} est le statut final :
 * {@code PASSED}, {@code FAILED} ou {@code NO_SHOW} ; {@code note} est une remarque facultative de
 * l'examinateur.
 */
public record ExamResultRequest(
        @NotNull ExamStatus outcome,
        @Size(max = 1000) String note
) {
}
