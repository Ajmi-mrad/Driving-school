package com.example.vehicleservice;

import com.example.vehicleservice.config.SecurityConfig;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérifie l'étape 3 : lecture autorisée à OWNER/SECRETARY/MONITOR, écriture réservée à
 * OWNER/SECRETARY. 401 sans token, 403 si le rôle ne permet pas l'action.
 */
@WebMvcTest(controllers = RbacProbeController.class)
@Import(SecurityConfig.class)
class SecurityRulesTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private static final SimpleGrantedAuthority OWNER = new SimpleGrantedAuthority("ROLE_OWNER");
    private static final SimpleGrantedAuthority MONITOR = new SimpleGrantedAuthority("ROLE_MONITOR");

    @Test
    void read_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/test/read")).andExpect(status().isUnauthorized());
    }

    @Test
    void read_asMonitor_returns200() throws Exception {
        mockMvc.perform(get("/api/test/read").with(jwt().authorities(MONITOR)))
                .andExpect(status().isOk());
    }

    @Test
    void write_asMonitor_returns403() throws Exception {
        mockMvc.perform(post("/api/test/write").with(jwt().authorities(MONITOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    void write_asOwner_returns200() throws Exception {
        mockMvc.perform(post("/api/test/write").with(jwt().authorities(OWNER)))
                .andExpect(status().isOk());
    }
}

/** Contrôleur de test : reproduit les règles RBAC du parc (lecture vs écriture). */
@RestController
class RbacProbeController {

    @GetMapping("/api/test/read")
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY','MONITOR')")
    public String read() {
        return "ok";
    }

    @PostMapping("/api/test/write")
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY')")
    public String write() {
        return "ok";
    }
}