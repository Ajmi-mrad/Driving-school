package com.example.authservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AuthServiceApplicationTests {

    // JwtDecoder est construit à partir de jwk-set-uri (appel réseau au démarrage) : on le mocke
    // pour que le contexte démarre sans Keycloak. Le bean admin Keycloak, lui, est paresseux.
    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void contextLoads() {
    }

}
