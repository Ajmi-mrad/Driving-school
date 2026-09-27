package com.example.vehicleservice.dev.dto;

/** Résumé d'une opération de seed : nombre d'entités créées et ignorées (déjà présentes). */
public record SeedResult(int created, int skipped) {
}
