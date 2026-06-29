package com.example.authservice.web;

import com.example.authservice.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MeController.class)
@Import(SecurityConfig.class)
class MeControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void me_returnsIdentityAndAuthorities() throws Exception {
        mockMvc.perform(get("/api/me").with(jwt()
                        .jwt(j -> j.subject("user-1").claim("preferred_username", "owner").claim("email", "o@e.com"))
                        .authorities(new SimpleGrantedAuthority("ROLE_OWNER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("user-1"))
                .andExpect(jsonPath("$.username").value("owner"))
                .andExpect(jsonPath("$.email").value("o@e.com"))
                .andExpect(jsonPath("$.authorities[0]").value("ROLE_OWNER"));
    }

    @Test
    void me_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }
}
