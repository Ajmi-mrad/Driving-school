package com.example.vehicleservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test d'intégration du cycle complet sur une vraie base Postgres (Testcontainers).
 * JWT OWNER simulé ; pas de Keycloak (JwtDecoder mocké).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class VehicleLifecycleIT {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private static final SimpleGrantedAuthority OWNER = new SimpleGrantedAuthority("ROLE_OWNER");

    @Test
    void fullLifecycle_create_read_update_status_maintenance_retire() throws Exception {
        String reg = "TUN-" + UUID.randomUUID();
        String createBody = """
                {"brand":"Renault","model":"Clio","registrationNumber":"%s",
                 "gearboxType":"MANUAL","fuelType":"DIESEL","manufactureYear":2021,"mileage":15000}"""
                .formatted(reg);

        // CREATE
        MvcResult created = mockMvc.perform(post("/api/vehicles").with(jwt().authorities(OWNER))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andReturn();
        String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        // READ
        mockMvc.perform(get("/api/vehicles/{id}", id).with(jwt().authorities(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationNumber").value(reg));

        // LIST filtrée par statut
        mockMvc.perform(get("/api/vehicles").param("status", "AVAILABLE").with(jwt().authorities(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + id + "')]").exists());

        // UPDATE
        mockMvc.perform(put("/api/vehicles/{id}", id).with(jwt().authorities(OWNER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mileage\":20000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mileage").value(20000));

        // PATCH status
        mockMvc.perform(patch("/api/vehicles/{id}/status", id).with(jwt().authorities(OWNER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_USE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_USE"));

        // ADD maintenance
        mockMvc.perform(post("/api/vehicles/{id}/maintenance", id).with(jwt().authorities(OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"REVISION\",\"performedAt\":\"2026-03-01\",\"cost\":120.50}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vehicleId").value(id));

        // LIST maintenance
        mockMvc.perform(get("/api/vehicles/{id}/maintenance", id).with(jwt().authorities(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // RETIRE (soft delete)
        mockMvc.perform(delete("/api/vehicles/{id}", id).with(jwt().authorities(OWNER)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/vehicles/{id}", id).with(jwt().authorities(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OUT_OF_SERVICE"));
    }
}