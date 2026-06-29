package com.example.authservice;

import com.example.authservice.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérifie l'étape 3 : un endpoint protégé renvoie 401 sans token, 403 avec un mauvais rôle,
 * et 200 avec le bon rôle. Les JWT sont simulés (spring-security-test), le JwtDecoder est mocké
 * pour ne pas dépendre d'un Keycloak réel.
 */
@WebMvcTest(controllers = OwnerOnlyController.class)
@Import(SecurityConfig.class)
class SecurityRulesTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void returns401WithoutToken() throws Exception {
        mockMvc.perform(get("/api/test/owner"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returns403WithWrongRole() throws Exception {
        mockMvc.perform(get("/api/test/owner")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void returns200WithOwnerRole() throws Exception {
        mockMvc.perform(get("/api/test/owner")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OWNER"))))
                .andExpect(status().isOk());
    }
}

/** Contrôleur minimal de test, protégé par rôle (top-level pour être mappé par @WebMvcTest). */
@RestController
class OwnerOnlyController {

    @GetMapping("/api/test/owner")
    @PreAuthorize("hasRole('OWNER')")
    public String ownerOnly() {
        return "ok";
    }
}