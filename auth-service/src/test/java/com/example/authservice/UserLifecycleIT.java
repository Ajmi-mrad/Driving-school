package com.example.authservice;

import com.example.authservice.keycloak.KeycloakService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test d'intégration du cycle complet (équivalent automatisé du test Postman) :
 * création → consultation → liste filtrée → mise à jour → désactivation, sur une vraie base
 * Postgres (Testcontainers). Keycloak est mocké ; le JWT OWNER est simulé.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UserLifecycleIT {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    KeycloakService keycloakService;
    @MockitoBean
    JwtDecoder jwtDecoder;

    private static final SimpleGrantedAuthority OWNER = new SimpleGrantedAuthority("ROLE_OWNER");

    @BeforeEach
    void stubKeycloak() {
        when(keycloakService.createUser(any(), any(), any(), any(), any()))
                .thenReturn(UUID.randomUUID().toString());
    }

    @Test
    void fullLifecycle_create_read_update_deactivate() throws Exception {
        String username = "lc-" + UUID.randomUUID();
        String createBody = """
                {"username":"%s","email":"%s@example.com","firstName":"Sami","lastName":"Ben Ali",
                 "phones":["20123456"],"password":"password1","roles":["CLIENT"],"permitNumber":"P-1"}"""
                .formatted(username, username);

        // CREATE
        MvcResult created = mockMvc.perform(post("/api/users").with(jwt().authorities(OWNER))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("CLIENT"))
                .andReturn();
        JsonNode createdJson = objectMapper.readTree(created.getResponse().getContentAsString());
        String id = createdJson.get("id").asText();
        Instant createdAt = Instant.parse(createdJson.get("createdAt").asText());

        // READ
        mockMvc.perform(get("/api/users/{id}", id).with(jwt().authorities(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));

        // LIST filtrée par rôle
        mockMvc.perform(get("/api/users").param("role", "CLIENT").with(jwt().authorities(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + id + "')]").exists());

        // UPDATE
        MvcResult updated = mockMvc.perform(put("/api/users/{id}", id).with(jwt().authorities(OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Samira\",\"notificationsEnabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Samira"))
                .andExpect(jsonPath("$.notificationsEnabled").value(false))
                .andReturn();
        Instant updatedAt = Instant.parse(objectMapper.readTree(updated.getResponse().getContentAsString())
                .get("updatedAt").asText());
        // saveAndFlush => updatedAt rafraîchi dans la réponse même
        assertThat(updatedAt).isAfter(createdAt);

        // DEACTIVATE (soft delete)
        mockMvc.perform(delete("/api/users/{id}", id).with(jwt().authorities(OWNER)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/{id}", id).with(jwt().authorities(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }
}
