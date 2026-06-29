package com.example.vehicleservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class VehicleServiceApplicationTests {

    // JwtDecoder est construit depuis jwk-set-uri (appel réseau au démarrage) : on le mocke
    // pour que le contexte démarre sans Keycloak.
    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void contextLoads() {
    }

}