package com.example.authservice.keycloak;

import com.example.authservice.exception.KeycloakOperationException;
import jakarta.ws.rs.core.Response;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Encapsule les opérations sur l'API Admin Keycloak : création de compte, affectation de rôle,
 * mise à jour des attributs d'identité, activation/désactivation. Keycloak reste la source de
 * vérité de l'identité ; ce service est appelé en premier lors de la synchronisation (étape 5).
 */
@Service
public class KeycloakService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakService.class);

    private final Keycloak keycloak;
    private final KeycloakProperties properties;

    public KeycloakService(Keycloak keycloak, KeycloakProperties properties) {
        this.keycloak = keycloak;
        this.properties = properties;
    }

    /**
     * Crée un compte Keycloak et lui affecte un mot de passe permanent.
     *
     * @return l'identifiant Keycloak ({@code sub}) du compte créé
     */
    public String createUser(String username, String email, String firstName, String lastName, String password) {
        UserRepresentation representation = new UserRepresentation();
        representation.setUsername(username);
        if (email != null && !email.isBlank()) {
            representation.setEmail(email);
            representation.setEmailVerified(true);
        }
        representation.setFirstName(firstName);
        representation.setLastName(lastName);
        representation.setEnabled(true);

        UsersResource users = realm().users();
        try (Response response = users.create(representation)) {
            int status = response.getStatus();
            if (status != Response.Status.CREATED.getStatusCode()) {
                throw new KeycloakOperationException(
                        "Échec de création du compte Keycloak pour " + username + " (HTTP " + status + ")");
            }
            String userId = CreatedResponseUtil.getCreatedId(response);
            if (password != null && !password.isBlank()) {
                users.get(userId).resetPassword(passwordCredential(password));
            }
            log.info("Compte Keycloak créé: {} (id={})", username, userId);
            return userId;
        }
    }

    /** Affecte un rôle de realm à un utilisateur. */
    public void assignRealmRole(String userId, String roleName) {
        RoleRepresentation role = realm().roles().get(roleName).toRepresentation();
        realm().users().get(userId).roles().realmLevel().add(List.of(role));
        log.info("Rôle {} affecté à l'utilisateur Keycloak {}", roleName, userId);
    }

    /** Met à jour les attributs d'identité (email, prénom, nom) propagés vers Keycloak. */
    public void updateUser(String userId, String email, String firstName, String lastName) {
        UserResource userResource = realm().users().get(userId);
        UserRepresentation representation = userResource.toRepresentation();
        if (email != null) {
            representation.setEmail(email);
        }
        if (firstName != null) {
            representation.setFirstName(firstName);
        }
        if (lastName != null) {
            representation.setLastName(lastName);
        }
        userResource.update(representation);
        log.info("Compte Keycloak {} mis à jour", userId);
    }

    /** Active ou désactive un compte (la désactivation remplace la suppression physique). */
    public void setEnabled(String userId, boolean enabled) {
        UserResource userResource = realm().users().get(userId);
        UserRepresentation representation = userResource.toRepresentation();
        representation.setEnabled(enabled);
        userResource.update(representation);
        log.info("Compte Keycloak {} {}", userId, enabled ? "activé" : "désactivé");
    }

    /**
     * Supprime physiquement un compte Keycloak. Réservé à la compensation (rollback) en cas
     * d'échec de la persistance locale juste après la création (étape 5) ; la désactivation
     * ({@link #setEnabled}) est préférée pour le cycle de vie normal.
     */
    public void deleteUser(String userId) {
        try (Response response = realm().users().delete(userId)) {
            log.info("Compte Keycloak {} supprimé (compensation), HTTP {}", userId, response.getStatus());
        }
    }

    /**
     * Envoie un email de réinitialisation de mot de passe : Keycloak adresse à l'utilisateur un
     * lien d'action {@code UPDATE_PASSWORD}. Nécessite un SMTP configuré sur le realm et un email
     * sur le compte.
     */
    public void sendPasswordResetEmail(String userId) {
        realm().users().get(userId).executeActionsEmail(List.of("UPDATE_PASSWORD"));
        log.info("Email de réinitialisation de mot de passe envoyé (utilisateur Keycloak {})", userId);
    }

    private CredentialRepresentation passwordCredential(String password) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);
        return credential;
    }

    private RealmResource realm() {
        return keycloak.realm(properties.getRealm());
    }
}
