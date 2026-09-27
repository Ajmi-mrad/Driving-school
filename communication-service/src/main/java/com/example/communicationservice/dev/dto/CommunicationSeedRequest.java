package com.example.communicationservice.dev.dto;

import java.util.Map;

/**
 * Corps du seed communication : carte {@code seedKey -> keycloakSub} produite par le seed auth,
 * utilisée pour renseigner les participants des conversations, les expéditeurs et les destinataires.
 */
public record CommunicationSeedRequest(Map<String, String> users) {
}
