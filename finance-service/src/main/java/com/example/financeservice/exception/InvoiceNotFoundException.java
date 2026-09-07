package com.example.financeservice.exception;

import java.util.UUID;

public class InvoiceNotFoundException extends RuntimeException {

    public InvoiceNotFoundException(UUID id) {
        super("Document introuvable : " + id);
    }
}
