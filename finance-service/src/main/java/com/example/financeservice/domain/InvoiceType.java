package com.example.financeservice.domain;

/**
 * Nature d'un document financier : facture émise à l'inscription, ou reçu émis à chaque paiement.
 */
public enum InvoiceType {
    INVOICE,    // facture (à l'inscription)
    RECEIPT     // reçu (à l'encaissement)
}
