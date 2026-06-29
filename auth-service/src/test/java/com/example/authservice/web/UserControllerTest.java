package com.example.authservice.web;

import com.example.authservice.config.SecurityConfig;
import com.example.authservice.domain.Role;
import com.example.authservice.exception.DuplicateUserException;
import com.example.authservice.exception.KeycloakOperationException;
import com.example.authservice.exception.UserNotFoundException;
import com.example.authservice.service.UserService;
import com.example.authservice.web.dto.CreateUserRequest;
import com.example.authservice.web.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérifie le câblage REST et le RBAC du UserController : authentification requise, rôles autorisés,
 * et la règle « seul un OWNER peut créer un OWNER ». Le UserService est mocké.
 */
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    UserService userService;

    private static final SimpleGrantedAuthority OWNER = new SimpleGrantedAuthority("ROLE_OWNER");
    private static final SimpleGrantedAuthority SECRETARY = new SimpleGrantedAuthority("ROLE_SECRETARY");
    private static final SimpleGrantedAuthority CLIENT = new SimpleGrantedAuthority("ROLE_CLIENT");

    private UserResponse sample() {
        return new UserResponse(UUID.randomUUID(), "kc-1", "jdoe", "j@example.com", "John", "Doe",
                Set.of("12345678"), true, Set.of(Role.CLIENT), true, null,
                Instant.now(), "system", Instant.now(), "system");
    }

    private String body(String roleJson) {
        return """
                {"username":"newuser","email":"n@example.com","firstName":"New","lastName":"User",
                 "password":"password1","roles":[%s]}""".formatted(roleJson);
    }

    @Test
    void create_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body("\"CLIENT\"")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void owner_canCreateOwner_returns201() throws Exception {
        given(userService.createUser(any())).willReturn(sample());
        mockMvc.perform(post("/api/users").with(jwt().authorities(OWNER))
                        .contentType(MediaType.APPLICATION_JSON).content(body("\"OWNER\"")))
                .andExpect(status().isCreated());
    }

    @Test
    void secretary_cannotCreateOwner_returns403() throws Exception {
        mockMvc.perform(post("/api/users").with(jwt().authorities(SECRETARY))
                        .contentType(MediaType.APPLICATION_JSON).content(body("\"OWNER\"")))
                .andExpect(status().isForbidden());
    }

    @Test
    void secretary_canCreateClient_returns201() throws Exception {
        given(userService.createUser(any())).willReturn(sample());
        mockMvc.perform(post("/api/users").with(jwt().authorities(SECRETARY))
                        .contentType(MediaType.APPLICATION_JSON).content(body("\"CLIENT\"")))
                .andExpect(status().isCreated());
    }

    @Test
    void client_cannotListUsers_returns403() throws Exception {
        mockMvc.perform(get("/api/users").with(jwt().authorities(CLIENT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_withInvalidBody_returns400() throws Exception {
        String missingUsername = """
                {"firstName":"New","lastName":"User","password":"password1","roles":["CLIENT"]}""";
        mockMvc.perform(post("/api/users").with(jwt().authorities(OWNER))
                        .contentType(MediaType.APPLICATION_JSON).content(missingUsername))
                .andExpect(status().isBadRequest());
    }

    @Test
    void get_notFound_returns404() throws Exception {
        given(userService.getUser(any())).willThrow(new UserNotFoundException(UUID.randomUUID()));
        mockMvc.perform(get("/api/users/{id}", UUID.randomUUID()).with(jwt().authorities(OWNER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_duplicate_returns409() throws Exception {
        given(userService.createUser(any())).willThrow(new DuplicateUserException("dup"));
        mockMvc.perform(post("/api/users").with(jwt().authorities(OWNER))
                        .contentType(MediaType.APPLICATION_JSON).content(body("\"CLIENT\"")))
                .andExpect(status().isConflict());
    }

    @Test
    void create_keycloakFailure_returns502() throws Exception {
        given(userService.createUser(any())).willThrow(new KeycloakOperationException("kc down"));
        mockMvc.perform(post("/api/users").with(jwt().authorities(OWNER))
                        .contentType(MediaType.APPLICATION_JSON).content(body("\"CLIENT\"")))
                .andExpect(status().isBadGateway());
    }

    @Test
    void resetPassword_asOwner_returns202() throws Exception {
        mockMvc.perform(post("/api/users/{id}/reset-password", UUID.randomUUID())
                        .with(jwt().authorities(OWNER)))
                .andExpect(status().isAccepted());
    }

    @Test
    void resetPassword_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/users/{id}/reset-password", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}