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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Logique métier et synchronisation Keycloak ↔ base locale.
 *
 * <p>Création : le compte Keycloak est créé d'abord (source de vérité de l'identité), puis l'entité
 * locale est persistée. Keycloak n'étant pas transactionnel, on enregistre une <b>compensation</b> :
 * si la transaction JPA est annulée (rollback), le compte Keycloak fraîchement créé est supprimé.</p>
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final KeycloakService keycloakService;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository, KeycloakService keycloakService, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.keycloakService = keycloakService;
        this.userMapper = userMapper;
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateUserException("Username déjà utilisé: " + request.username());
        }
        String email = normalizeEmail(request.email());
        if (email != null && userRepository.existsByEmail(email)) {
            throw new DuplicateUserException("Email déjà utilisé: " + email);
        }

        // 1) Création côté Keycloak (identité)
        String keycloakId = keycloakService.createUser(
                request.username(), email, request.firstName(), request.lastName(), request.password());
        // Compensation : suppression du compte Keycloak si la transaction locale échoue
        registerKeycloakCompensation(keycloakId);

        for (Role role : request.roles()) {
            keycloakService.assignRealmRole(keycloakId, role.name());
        }

        // 2) Persistance de l'entité métier
        User user = new User();
        user.setKeycloakId(keycloakId);
        user.setUsername(request.username());
        user.setEmail(email);
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        if (request.phones() != null) {
            user.setPhones(new LinkedHashSet<>(request.phones()));
        }
        user.setRoles(EnumSet.copyOf(request.roles()));
        user.setPermitNumber(request.permitNumber());
        if (request.notificationsEnabled() != null) {
            user.setNotificationsEnabled(request.notificationsEnabled());
        }

        User saved = userRepository.save(user);
        log.info("Utilisateur créé id={} username={}", saved.getId(), saved.getUsername());
        return userMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID id) {
        return userMapper.toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers(Role role) {
        List<User> users = (role == null)
                ? userRepository.findAll()
                : userRepository.findByRolesContaining(role);
        return users.stream().map(userMapper::toResponse).toList();
    }

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        User user = findOrThrow(id);
        String email = normalizeEmail(request.email());
        if (email != null && !email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw new DuplicateUserException("Email déjà utilisé: " + email);
        }

        // Propagation des attributs d'identité vers Keycloak ; en cas d'échec, l'exception
        // déclenche le rollback de la transaction locale (aucune modification persistée).
        keycloakService.updateUser(user.getKeycloakId(), email, request.firstName(), request.lastName());

        if (email != null) {
            user.setEmail(email);
        }
        if (request.firstName() != null) {
            user.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName());
        }
        if (request.phones() != null) {
            user.setPhones(new LinkedHashSet<>(request.phones()));
        }
        if (request.permitNumber() != null) {
            user.setPermitNumber(request.permitNumber());
        }
        if (request.notificationsEnabled() != null) {
            user.setNotificationsEnabled(request.notificationsEnabled());
        }

        // saveAndFlush : force le flush (et le déclenchement de @PreUpdate / updatedAt) avant de
        // mapper la réponse, sinon le DTO renverrait un updatedAt encore à sa valeur d'origine.
        User saved = userRepository.saveAndFlush(user);
        log.info("Utilisateur mis à jour id={}", saved.getId());
        return userMapper.toResponse(saved);
    }

    /**
     * Déclenche l'envoi par Keycloak d'un email de réinitialisation de mot de passe.
     * Nécessite que l'utilisateur ait un email (sinon rien à envoyer).
     */
    @Transactional(readOnly = true)
    public void requestPasswordReset(UUID id) {
        User user = findOrThrow(id);
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Aucun email associé : réinitialisation par email impossible");
        }
        keycloakService.sendPasswordResetEmail(user.getKeycloakId());
    }

    @Transactional
    public void deactivateUser(UUID id) {
        User user = findOrThrow(id);
        keycloakService.setEnabled(user.getKeycloakId(), false);
        user.setActive(false);
        userRepository.save(user);
        log.info("Utilisateur désactivé id={}", id);
    }

    private User findOrThrow(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    private String normalizeEmail(String email) {
        return (email == null || email.isBlank()) ? null : email.trim();
    }

    private void registerKeycloakCompensation(String keycloakId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        try {
                            keycloakService.deleteUser(keycloakId);
                            log.warn("Rollback: compte Keycloak {} supprimé (compensation)", keycloakId);
                        } catch (Exception e) {
                            log.error("Échec de la compensation Keycloak pour {}", keycloakId, e);
                        }
                    }
                }
            });
        }
    }
}
