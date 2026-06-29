package com.example.authservice.mapper;

import com.example.authservice.domain.Role;
import com.example.authservice.domain.User;
import com.example.authservice.web.dto.UserResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    private final UserMapper mapper = new UserMapperImpl();

    @Test
    void mapsAllFieldsToResponse() {
        UUID id = UUID.randomUUID();
        Instant created = Instant.parse("2026-01-01T10:00:00Z");
        Instant updated = Instant.parse("2026-01-02T10:00:00Z");

        User user = new User();
        user.setId(id);
        user.setKeycloakId("kc-1");
        user.setUsername("client1");
        user.setEmail("client1@example.com");
        user.setFirstName("Sami");
        user.setLastName("Ben Ali");
        user.setPhones(Set.of("20123456", "55123456"));
        user.setActive(true);
        user.setRoles(Set.of(Role.CLIENT));
        user.setNotificationsEnabled(false);
        user.setPermitNumber("P-1");
        user.setCreatedAt(created);
        user.setCreatedBy("system");
        user.setUpdatedAt(updated);
        user.setUpdatedBy("admin");

        UserResponse response = mapper.toResponse(user);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.keycloakId()).isEqualTo("kc-1");
        assertThat(response.username()).isEqualTo("client1");
        assertThat(response.email()).isEqualTo("client1@example.com");
        assertThat(response.firstName()).isEqualTo("Sami");
        assertThat(response.lastName()).isEqualTo("Ben Ali");
        assertThat(response.phones()).containsExactlyInAnyOrder("20123456", "55123456");
        assertThat(response.active()).isTrue();
        assertThat(response.roles()).containsExactly(Role.CLIENT);
        assertThat(response.notificationsEnabled()).isFalse();
        assertThat(response.permitNumber()).isEqualTo("P-1");
        assertThat(response.createdAt()).isEqualTo(created);
        assertThat(response.createdBy()).isEqualTo("system");
        assertThat(response.updatedAt()).isEqualTo(updated);
        assertThat(response.updatedBy()).isEqualTo("admin");
    }
}