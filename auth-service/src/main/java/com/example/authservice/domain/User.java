package com.example.authservice.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Profil métier d'un utilisateur de l'auto-école.
 *
 * <p>Entité unique (pas d'héritage JPA). Un utilisateur peut porter plusieurs {@link Role}
 * (ensemble), à l'image des rôles de realm Keycloak. Identifiant de connexion : {@code username}
 * (obligatoire, unique). L'{@code email} est facultatif (certains clients n'en ont pas) et un
 * utilisateur peut avoir plusieurs numéros de téléphone.</p>
 *
 * <p>Les collections sont en chargement LAZY : le mapping vers DTO se fait dans la couche service
 * transactionnelle (open-in-view=false).</p>
 *
 * <p>L'identité (compte, mot de passe, rôles) vit dans Keycloak ; {@code keycloakId} fait le lien.</p>
 */
@Entity
@Table(name = "users")
public class User extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Identifiant du compte côté Keycloak (claim {@code sub}). */
    @Column(name = "keycloak_id", nullable = false, unique = true, length = 255)
    private String keycloakId;

    /** Identifiant de connexion (username Keycloak), obligatoire et unique. */
    @Column(name = "username", nullable = false, unique = true, length = 255)
    private String username;

    /** Email facultatif (certains clients n'en ont pas). Unique lorsqu'il est renseigné. */
    @Column(name = "email", unique = true, length = 255)
    private String email;

    @Column(name = "first_name", length = 255)
    private String firstName;

    @Column(name = "last_name", length = 255)
    private String lastName;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_phones", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "phone", nullable = false, length = 50)
    private Set<String> phones = new LinkedHashSet<>();

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Set<Role> roles = EnumSet.noneOf(Role.class);

    @Column(name = "notifications_enabled", nullable = false)
    private boolean notificationsEnabled = true;

    /** Numéro de permis — pertinent uniquement pour les clients (élèves). */
    @Column(name = "permit_number", length = 100)
    private String permitNumber;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getKeycloakId() {
        return keycloakId;
    }

    public void setKeycloakId(String keycloakId) {
        this.keycloakId = keycloakId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public Set<String> getPhones() {
        return phones;
    }

    public void setPhones(Set<String> phones) {
        this.phones = phones;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }

    public void addRole(Role role) {
        this.roles.add(role);
    }

    public boolean isNotificationsEnabled() {
        return notificationsEnabled;
    }

    public void setNotificationsEnabled(boolean notificationsEnabled) {
        this.notificationsEnabled = notificationsEnabled;
    }

    public String getPermitNumber() {
        return permitNumber;
    }

    public void setPermitNumber(String permitNumber) {
        this.permitNumber = permitNumber;
    }
}