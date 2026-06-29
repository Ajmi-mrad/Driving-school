package com.example.authservice.exception;

import java.util.UUID;

/** Levée lorsqu'aucun utilisateur ne correspond à l'identifiant demandé. */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UUID id) {
        super("Utilisateur introuvable: " + id);
    }
}