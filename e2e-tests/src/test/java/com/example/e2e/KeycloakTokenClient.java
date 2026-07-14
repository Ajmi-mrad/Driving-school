package com.example.e2e;

import io.restassured.RestAssured;

/**
 * Obtains a real access token from Keycloak via the OAuth2 password grant against the public
 * {@code auto-ecole-frontend} client (which has direct access grants enabled in the realm).
 */
final class KeycloakTokenClient {

    private KeycloakTokenClient() {
    }

    static String passwordGrantToken(String username, String password) {
        return RestAssured.given()
                .contentType("application/x-www-form-urlencoded")
                .formParam("grant_type", "password")
                .formParam("client_id", E2EConfig.CLIENT_ID)
                .formParam("username", username)
                .formParam("password", password)
                .when()
                .post(E2EConfig.KEYCLOAK_URL + "/realms/" + E2EConfig.REALM
                        + "/protocol/openid-connect/token")
                .then()
                .statusCode(200)
                .extract()
                .path("access_token");
    }
}
