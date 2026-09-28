package com.example.authservice;

import com.example.authservice.dev.DevDataService;
import com.example.authservice.dev.dto.SeedUsersResponse;
import com.example.authservice.domain.Role;
import com.example.authservice.domain.User;
import com.example.authservice.keycloak.KeycloakService;
import com.example.authservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.EnumSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests d'intégration de l'outil de dev auth. Keycloak est mocké (aucun appel réseau) ; on vérifie
 * surtout les garde-fous de la remise à zéro : jamais {@code owner}, {@code service-account-*}, ni
 * l'appelant lui-même.
 */
@SpringBootTest(properties = "app.dev-tools.enabled=true")
@Import(TestcontainersConfiguration.class)
class AuthDevDataIT {

    @Autowired
    private DevDataService devDataService;
    @Autowired
    private UserRepository userRepository;

    // JwtDecoder fait un appel réseau au démarrage : mocké pour démarrer sans Keycloak.
    @MockitoBean
    private JwtDecoder jwtDecoder;
    // Identité Keycloak simulée : createUser renvoie un sub aléatoire, les autres appels sont no-op.
    @MockitoBean
    private KeycloakService keycloakService;

    @BeforeEach
    void clean() {
        when(keycloakService.createUser(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> UUID.randomUUID().toString());
        userRepository.deleteAll();
    }

    @Test
    void seed_createsEightLoginableDemoUsers() {
        SeedUsersResponse res = devDataService.seed();

        assertThat(res.result().created()).isEqualTo(8);
        assertThat(res.users()).hasSize(8).containsKeys("u-owner", "u-cli-1", "u-mon-1");
        assertThat(userRepository.count()).isEqualTo(8);
    }

    @Test
    void seed_isIdempotent_returningStableSubs() {
        SeedUsersResponse first = devDataService.seed();
        SeedUsersResponse second = devDataService.seed();

        assertThat(second.result().created()).isZero();
        assertThat(second.result().skipped()).isEqualTo(8);
        assertThat(second.users()).isEqualTo(first.users()); // same seedKey -> sub map
        assertThat(userRepository.count()).isEqualTo(8);
    }

    @Test
    void reset_deletesOnlySeedUsers_preservingOwnerAndServiceAccounts() {
        insertUser("owner");
        insertUser("service-account-finance-service");
        insertUser("legacy.manual.user"); // pre-existing non-seed account
        devDataService.seed(); // + 8 demo users

        devDataService.reset(null);

        assertThat(userRepository.findAll()).extracting(User::getUsername)
                .containsExactlyInAnyOrder("owner", "service-account-finance-service", "legacy.manual.user")
                .doesNotContain("directeur", "lucas.m");
    }

    @Test
    void reset_neverDeletesTheAuthenticatedCaller() {
        devDataService.seed();
        String directeurSub = userRepository.findByUsername("directeur").orElseThrow().getKeycloakId();

        devDataService.reset(directeurSub);

        assertThat(userRepository.findByUsername("directeur")).isPresent();
        assertThat(userRepository.count()).isEqualTo(1); // only the caller survives
    }

    private void insertUser(String username) {
        User user = new User();
        user.setKeycloakId(UUID.randomUUID().toString());
        user.setUsername(username);
        user.setRoles(EnumSet.of(Role.CLIENT));
        userRepository.save(user);
    }
}
