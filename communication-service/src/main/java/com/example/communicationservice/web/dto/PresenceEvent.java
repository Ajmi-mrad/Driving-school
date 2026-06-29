package com.example.communicationservice.web.dto;

/** Événement de présence diffusé sur {@code /topic/presence} à chaque connexion/déconnexion. */
public record PresenceEvent(String userId, boolean online) {
}