package com.example.e2e;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end smoke of the deployed platform. Drives real HTTP through the api-gateway ({@code :8222})
 * with a real Keycloak OWNER token, proving that gateway routing + JWT auth + every backing service
 * (and its database) are wired correctly across the stack.
 *
 * <p>Requires the docker-compose stack to be running and {@code E2E_OWNER_PASSWORD} to be set; when
 * the password is absent the suite is skipped (not failed), so it is CI-safe by default.
 *
 * <p>The deep cross-service write path (booking → auth/vehicle/finance, complete → consume) is
 * covered by the service integration/chaos tests; this suite validates the deployed wiring and safe
 * finance/vehicle writes without polluting Keycloak with users.
 */
@Tag("e2e")
class HappyPathE2E {

    private static String token;

    @BeforeAll
    static void authenticate() {
        assumeTrue(E2EConfig.OWNER_PASSWORD != null,
                "E2E_OWNER_PASSWORD not set — skipping live E2E suite");
        RestAssured.baseURI = E2EConfig.GATEWAY_URL;
        token = KeycloakTokenClient.passwordGrantToken(E2EConfig.OWNER_USER, E2EConfig.OWNER_PASSWORD);
    }

    private RequestSpecification asOwner() {
        return RestAssured.given().header("Authorization", "Bearer " + token);
    }

    @Test
    void identityIsResolvedThroughGateway() {
        asOwner().when().get("/api/me")
                .then().statusCode(200)
                .body("roles", hasItem("OWNER"));
    }

    @Test
    void everyServiceIsReachableThroughGateway() {
        // auth-service already covered by /api/me; hit one read endpoint per remaining service that
        // is part of the local docker-compose stack. (booking-service has no compose file yet, so it
        // is exercised by the service integration/chaos tests rather than this live smoke.)
        asOwner().when().get("/api/vehicles").then().statusCode(200);           // vehicle-service
        asOwner().when().get("/api/forfaits").then().statusCode(200);           // finance-service
        asOwner().when().get("/api/enrollments").then().statusCode(200);        // finance-service
        asOwner().when().get("/api/notifications").then().statusCode(200);      // communication-service
    }

    @Test
    void financeAndVehicleWritesPropagate() {
        String unique = UUID.randomUUID().toString().substring(0, 8);

        // Create a forfait (finance-service) and confirm it is listed back.
        String forfaitName = "E2E Pack " + unique;
        asOwner().contentType(ContentType.JSON)
                .body("""
                        {"name":"%s","description":"e2e","drivingHours":20,"codeSessions":10,"price":1200.00}
                        """.formatted(forfaitName))
                .when().post("/api/forfaits")
                .then().statusCode(201);
        asOwner().when().get("/api/forfaits")
                .then().statusCode(200)
                .body("name", hasItem(forfaitName));

        // Create a vehicle (vehicle-service) and confirm it shows up as available.
        String plate = "E2E-" + unique;
        asOwner().contentType(ContentType.JSON)
                .body("""
                        {"brand":"Renault","model":"Clio","registrationNumber":"%s",
                         "gearboxType":"MANUAL","fuelType":"PETROL","manufactureYear":2022,"mileage":15000}
                        """.formatted(plate))
                .when().post("/api/vehicles")
                .then().statusCode(201);
        asOwner().when().get("/api/vehicles")
                .then().statusCode(200)
                .body("registrationNumber", hasItem(plate));
    }
}
