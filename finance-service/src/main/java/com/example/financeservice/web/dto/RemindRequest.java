package com.example.financeservice.web.dto;

import jakarta.validation.constraints.Size;

/**
 * Message optionnel accompagnant une relance de paiement.
 */
public record RemindRequest(
        @Size(max = 1000) String message
) {
}
