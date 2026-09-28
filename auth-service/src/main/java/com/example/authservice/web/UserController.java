package com.example.authservice.web;

import com.example.authservice.domain.Role;
import com.example.authservice.repository.AuditEventRepository;
import com.example.authservice.service.UserService;
import com.example.authservice.web.dto.AuditEventResponse;
import com.example.authservice.web.dto.ContactResponse;
import com.example.authservice.web.dto.CreateUserRequest;
import com.example.authservice.web.dto.UpdateUserRequest;
import com.example.authservice.web.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * API de gestion des utilisateurs. Accès réservé au propriétaire et à la secrétaire ;
 * seul le propriétaire peut créer un autre propriétaire (voir {@code @PreAuthorize} sur create).
 */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasAnyRole('OWNER','SECRETARY')")
public class UserController {

    private final UserService userService;
    private final AuditEventRepository auditEventRepository;

    public UserController(UserService userService, AuditEventRepository auditEventRepository) {
        this.userService = userService;
        this.auditEventRepository = auditEventRepository;
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

    /**
     * Résolution d'un utilisateur par son identifiant Keycloak ({@code sub}). Endpoint interne :
     * appelé par les autres services avec un jeton {@code client_credentials} (rôle {@code SERVICE}),
     * en plus du staff. Surcharge la règle staff-only de la classe.
     */
    @GetMapping("/by-keycloak/{kid}")
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY','SERVICE')")
    public UserResponse getByKeycloak(@PathVariable String kid) {
        return userService.getByKeycloakId(kid);
    }

    @GetMapping
    public List<UserResponse> list(@RequestParam(required = false) Role role) {
        return userService.listUsers(role);
    }

    /**
     * Journal d'audit des comptes (append-only), réservé au propriétaire — surcharge la règle staff.
     * Filtrable par action / type d'entité ; le plus récent d'abord.
     */
    @GetMapping("/audit")
    @PreAuthorize("hasRole('OWNER')")
    public List<AuditEventResponse> audit(@RequestParam(required = false) String action,
                                          @RequestParam(required = false) String entityType,
                                          @RequestParam(defaultValue = "200") int limit) {
        int capped = Math.min(Math.max(limit, 1), 500);
        return auditEventRepository.findRecent(action, entityType, Limit.of(capped)).stream()
                .map(e -> new AuditEventResponse(e.getId(), e.getOccurredAt(), e.getActor(),
                        e.getAction(), e.getEntityType(), e.getEntityId(), e.getSummary()))
                .toList();
    }

    /**
     * Répertoire des interlocuteurs de messagerie du participant (moniteur → élèves, élève → moniteurs).
     * Surcharge la règle staff-only : ouvert aux moniteurs et élèves, vue minimale (nom + id Keycloak).
     */
    @GetMapping("/contacts")
    @PreAuthorize("hasAnyRole('OWNER','SECRETARY','MONITOR','CLIENT')")
    public List<ContactResponse> contacts(JwtAuthenticationToken auth) {
        return userService.listContacts(rolesOf(auth));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER') or "
            + "(hasRole('SECRETARY') and (#request.roles() == null or "
            + "!#request.roles().contains(T(com.example.authservice.domain.Role).OWNER)))")
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

    /** Rôles « nus » de l'appelant (sans le préfixe {@code ROLE_} du converter Keycloak). */
    private static Set<String> rolesOf(JwtAuthenticationToken auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .collect(Collectors.toSet());
    }
}
