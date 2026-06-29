package com.example.authservice.service;

import com.example.authservice.domain.Role;
import com.example.authservice.domain.User;
import com.example.authservice.exception.DuplicateUserException;
import com.example.authservice.exception.UserNotFoundException;
import com.example.authservice.keycloak.KeycloakService;
import com.example.authservice.mapper.UserMapper;
import com.example.authservice.repository.UserRepository;
import com.example.authservice.web.dto.CreateUserRequest;
import com.example.authservice.web.dto.UpdateUserRequest;
import com.example.authservice.web.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    KeycloakService keycloakService;
    @Mock
    UserMapper userMapper;
    @InjectMocks
    UserService userService;

    private CreateUserRequest createRequest(Set<Role> roles) {
        return new CreateUserRequest("client1", "client1@example.com", "Sami", "Ben Ali",
                Set.of("20123456"), "password1", roles, "P-1", true);
    }

    private UserResponse dummyResponse() {
        return new UserResponse(UUID.randomUUID(), "kc-id", "client1", "client1@example.com",
                "Sami", "Ben Ali", Set.of("20123456"), true, Set.of(Role.CLIENT), true, "P-1",
                Instant.now(), "system", Instant.now(), "system");
    }

    @Test
    void createUser_createsKeycloakThenPersists() {
        CreateUserRequest request = createRequest(Set.of(Role.CLIENT));
        when(userRepository.existsByUsername("client1")).thenReturn(false);
        when(userRepository.existsByEmail("client1@example.com")).thenReturn(false);
        when(keycloakService.createUser("client1", "client1@example.com", "Sami", "Ben Ali", "password1"))
                .thenReturn("kc-id");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userMapper.toResponse(any(User.class))).thenReturn(dummyResponse());

        UserResponse result = userService.createUser(request);

        assertThat(result).isNotNull();
        verify(keycloakService).createUser("client1", "client1@example.com", "Sami", "Ben Ali", "password1");
        verify(keycloakService).assignRealmRole("kc-id", "CLIENT");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void createUser_duplicateUsername_throwsAndSkipsKeycloak() {
        CreateUserRequest request = createRequest(Set.of(Role.CLIENT));
        when(userRepository.existsByUsername("client1")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(DuplicateUserException.class);
        verifyNoInteractions(keycloakService);
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_duplicateEmail_throwsAndSkipsKeycloak() {
        CreateUserRequest request = createRequest(Set.of(Role.CLIENT));
        when(userRepository.existsByUsername("client1")).thenReturn(false);
        when(userRepository.existsByEmail("client1@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(DuplicateUserException.class);
        verifyNoInteractions(keycloakService);
    }

    @Test
    void getUser_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUser(id))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void updateUser_propagatesToKeycloakAndFlushes() {
        UUID id = UUID.randomUUID();
        User existing = new User();
        existing.setKeycloakId("kc-id");
        existing.setUsername("client1");
        existing.setEmail("old@example.com");
        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userMapper.toResponse(any(User.class))).thenReturn(dummyResponse());

        UpdateUserRequest request = new UpdateUserRequest("new@example.com", "Samira", null, null, null, false);
        userService.updateUser(id, request);

        verify(keycloakService).updateUser("kc-id", "new@example.com", "Samira", null);
        verify(userRepository).saveAndFlush(any(User.class));
        assertThat(existing.getEmail()).isEqualTo("new@example.com");
        assertThat(existing.getFirstName()).isEqualTo("Samira");
        assertThat(existing.isNotificationsEnabled()).isFalse();
    }

    @Test
    void deactivateUser_disablesInKeycloakAndLocally() {
        UUID id = UUID.randomUUID();
        User existing = new User();
        existing.setKeycloakId("kc-id");
        existing.setActive(true);
        when(userRepository.findById(id)).thenReturn(Optional.of(existing));

        userService.deactivateUser(id);

        verify(keycloakService).setEnabled("kc-id", false);
        verify(userRepository).save(existing);
        assertThat(existing.isActive()).isFalse();
    }

    @Test
    void requestPasswordReset_withEmail_sendsEmail() {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setKeycloakId("kc-id");
        user.setEmail("a@b.com");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        userService.requestPasswordReset(id);

        verify(keycloakService).sendPasswordResetEmail("kc-id");
    }

    @Test
    void requestPasswordReset_withoutEmail_throwsBadRequest() {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setKeycloakId("kc-id");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.requestPasswordReset(id))
                .isInstanceOf(ResponseStatusException.class);
        verify(keycloakService, never()).sendPasswordResetEmail(any());
    }
}
