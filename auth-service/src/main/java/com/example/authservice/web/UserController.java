package com.example.authservice.web;

import com.example.authservice.domain.Role;
import com.example.authservice.service.UserService;
import com.example.authservice.web.dto.CreateUserRequest;
import com.example.authservice.web.dto.UpdateUserRequest;
import com.example.authservice.web.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * API de gestion des utilisateurs. Accès réservé au propriétaire et à la secrétaire ;
 * seul le propriétaire peut créer un autre propriétaire (voir {@code @PreAuthorize} sur create).
 */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasAnyRole('OWNER','SECRETARY')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @PreAuthorize("hasRole('OWNER') or "
            + "(hasRole('SECRETARY') and !#request.roles().contains(T(com.example.authservice.domain.Role).OWNER))")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.createUser(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable UUID id) {
        return userService.getUser(id);
    }

    @GetMapping
    public List<UserResponse> list(@RequestParam(required = false) Role role) {
        return userService.listUsers(role);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.updateUser(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID id) {
        userService.deactivateUser(id);
    }

    /** Déclenche l'envoi d'un email de réinitialisation de mot de passe par Keycloak. */
    @PostMapping("/{id}/reset-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void resetPassword(@PathVariable UUID id) {
        userService.requestPasswordReset(id);
    }
}
