package com.example.communicationservice.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corps de {@code POST /api/conversations/{id}/messages} : le contenu du message. La conversation
 * vient du chemin et l'expéditeur du JWT (jamais du client), comme pour l'envoi STOMP.
 */
public record MessageContentRequest(
        @NotBlank @Size(max = 4000) String content
) {
}
