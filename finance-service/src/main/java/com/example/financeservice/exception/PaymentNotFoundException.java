package com.example.financeservice.exception;

import java.util.UUID;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(UUID id) {
        super("Paiement introuvable : " + id);
    }
}