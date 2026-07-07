package com.example.financeservice.exception;

import java.util.UUID;

public class ForfaitNotFoundException extends RuntimeException {

    public ForfaitNotFoundException(UUID id) {
        super("Forfait introuvable : " + id);
    }
}
