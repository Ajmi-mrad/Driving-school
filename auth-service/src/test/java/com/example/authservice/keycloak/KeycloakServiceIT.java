package com.example.authservice.keycloak;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie l'étape 4 contre un Keycloak réel : appeler {@link KeycloakService} crée effectivement
 * un compte visible dans le realm. Désactivé par défaut (n'exige Keycloak que sur demande) ;
 * lancer avec {@code KEYCLOAK_IT=true ./mvnw test -Dtest=KeycloakServiceIT}.
 */
@EnabledIfEnvironmentVariable(named = "KEYCLOAK_IT", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KeycloakServiceIT {

    static Keycloak keycloak;
    static KeycloakService service;
    static String userId;
    static String username;
    static String email;

    @BeforeAll
    static void setUp() {
        KeycloakProperties props = new KeycloakProperties();
        props.setServerUrl(System.getenv().getOrDefault("KEYCLOAK_SERVER_URL", "http://localhost:8080"));
        props.setRealm("auto-ecole");
        props.setClientId("auth-service");
        props.setClientSecret(System.getenv().getOrDefault(
                "KEYCLOAK_AUTH_SERVICE_SECRET", "KAy4oDfhP3EwmMGluIeVmFC8NgS7bE1I"));

        keycloak = KeycloakBuilder.builder()
                .serverUrl(props.getServerUrl())
                .realm(props.getRealm())
                .clientId(props.getClientId())
                .clientSecret(props.getClientSecret())
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .build();
        service = new KeycloakService(keycloak, props);
        username = "it-" + UUID.randomUUID();
        email = username + "@example.com";
    }

    @Test
    @Order(1)
    void createsUserAndAssignsRole() {
        userId = service.createUser(username, email, "Test", "Owner", "Passw0rd!");
        assertThat(userId).isNotBlank();

        service.assignRealmRole(userId, "CLIENT");

        UserRepresentation rep = keycloak.realm("auto-ecole").users().get(userId).toRepresentation();
        assertThat(rep.getEmail()).isEqualTo(email);
        assertThat(rep.getUsername()).isEqualTo(username);
        assertThat(rep.isEnabled()).isTrue();
    }

    @Test
    @Order(2)
    void disablesUser() {
        service.setEnabled(userId, false);

        UserRepresentation rep = keycloak.realm("auto-ecole").users().get(userId).toRepresentation();
        assertThat(rep.isEnabled()).isFalse();
    }

    @AfterAll
    static void cleanUp() {
        if (service != null && userId != null) {
            service.deleteUser(userId);
        }
        if (keycloak != null) {
            keycloak.close();
        }
    }
}