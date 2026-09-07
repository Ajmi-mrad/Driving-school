package com.example.financeservice.exception;

import java.util.UUID;

/**
 * Levée quand on tente de supprimer une inscription qui porte encore des paiements vivants :
 * il faut d'abord annuler les paiements. Mappée en 409 Conflict.
 */
public class EnrollmentHasPaymentsException extends RuntimeException {

    public EnrollmentHasPaymentsException(UUID id) {
        super("Impossible de supprimer l'inscription " + id
                + " : des paiements y sont rattachés. Annulez-les d'abord.");
    }
}