package com.example.bookingservice.web.dto;

import jakarta.validation.constraints.Size;

/**
 * Corps optionnel d'une décision (confirmation / refus) d'une demande de séance. Permet au
 * staff/moniteur de joindre une note à l'élève (ex. motif du refus, créneaux disponibles proposés).
 */
public record DecisionRequest(
        @Size(max = 1000) String comment
) {
}
