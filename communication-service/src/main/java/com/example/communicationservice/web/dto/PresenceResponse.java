package com.example.communicationservice.web.dto;

import java.util.Set;

/** Sous-ensemble des identifiants demandés qui sont actuellement en ligne. */
public record PresenceResponse(Set<String> online) {
}