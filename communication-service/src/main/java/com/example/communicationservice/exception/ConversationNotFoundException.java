package com.example.communicationservice.exception;

import java.util.UUID;

/** Conversation inexistante → HTTP 404. */
public class ConversationNotFoundException extends RuntimeException {

    public ConversationNotFoundException(UUID id) {
        super("Conversation introuvable : " + id);
    }
}