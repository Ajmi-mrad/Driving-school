package com.example.financeservice.dev.dto;

import java.util.Map;

/**
 * Corps du seed finance : carte {@code seedKey -> keycloakSub} produite par le seed auth, utilisée
 * pour renseigner {@code client_id} sur les inscriptions, paiements et factures.
 */
public record FinanceSeedRequest(Map<String, String> users) {
}
