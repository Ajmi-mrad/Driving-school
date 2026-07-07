package com.example.financeservice.domain;

/**
 * Moyens de paiement acceptés (cahier des charges §3.5).
 */
public enum PaymentMethod {
    CASH,       // espèces
    CHECK,      // chèque
    TRANSFER,   // virement
    CARD        // carte
}
