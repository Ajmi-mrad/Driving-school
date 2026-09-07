package com.example.financeservice.web.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/** Corps de {@code POST /api/invoices/email} : les documents à envoyer par email à leurs clients. */
public record EmailInvoicesRequest(
        @NotEmpty List<UUID> ids
) {
}
