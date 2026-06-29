package com.example.communicationservice.domain;

/**
 * Nature d'un message. v1 : uniquement {@link #TEXT}. {@link #FILE} est réservé pour l'intégration
 * future avec le Document Service (MinIO) — présent dès maintenant pour éviter une migration cassante.
 */
public enum MessageType {
    TEXT,
    FILE
}