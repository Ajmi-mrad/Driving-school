package com.example.authservice.repository;

import com.example.authservice.domain.Role;
import com.example.authservice.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByKeycloakId(String keycloakId);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /** Liste les utilisateurs portant un rôle donné (utilisé pour la liste filtrée par rôle). */
    List<User> findByRolesContaining(Role role);
}