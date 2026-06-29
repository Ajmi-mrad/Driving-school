package com.example.communicationservice.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Ouverture (ou récupération) d'une conversation avec un interlocuteur, désigné par son identifiant
 * Keycloak ({@code sub}). L'appelant est l'autre participant (déduit de son JWT).
 */
public record CreateConversationRequest(
        @NotBlank String counterpartId
) {
}