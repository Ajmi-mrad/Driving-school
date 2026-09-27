package com.example.vehicleservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sécurité des endpoints d'administration (RBAC) : réservés au propriétaire, refusés aux autres,
 * 401 sans jeton.
 */
@SpringBootTest(properties = "app.dev-tools.enabled=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class VehicleAdminSecurityIT {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static final SimpleGrantedAuthority OWNER = new SimpleGrantedAuthority("ROLE_OWNER");
    private static final SimpleGrantedAuthority CLIENT = new SimpleGrantedAuthority("ROLE_CLIENT");

    @Test
    void reset_isAllowedForOwner() throws Exception {
        mockMvc.perform(post("/api/admin/vehicle/reset").with(jwt().authorities(OWNER)))
                .andExpect(status().isNoContent());
    }

    @Test
    void seed_isForbiddenForNonOwner() throws Exception {
        mockMvc.perform(post("/api/admin/vehicle/seed").with(jwt().authorities(CLIENT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void seed_isUnauthorizedWithoutToken() throws Exception {
        mockMvc.perform(post("/api/admin/vehicle/seed"))
                .andExpect(status().isUnauthorized());
    }
}
