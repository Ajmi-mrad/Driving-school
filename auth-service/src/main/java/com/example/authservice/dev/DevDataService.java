package com.example.authservice.dev;

import com.example.authservice.dev.dto.AuthExport;
import com.example.authservice.dev.dto.SeedResult;
import com.example.authservice.dev.dto.SeedUsersResponse;
import com.example.authservice.domain.Role;
import com.example.authservice.domain.User;
import com.example.authservice.keycloak.KeycloakService;
import com.example.authservice.repository.UserRepository;
import com.example.authservice.service.UserService;
import com.example.authservice.web.dto.CreateUserRequest;
import com.example.authservice.web.dto.UserResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Outil de développement (activé par {@code app.dev-tools.enabled=true}) : peuplement et remise à
 * zéro des données. Le seed crée des comptes de démonstration <b>connectables</b> (Keycloak + base
 * locale) en réutilisant {@link UserService#createUser}, puis renvoie la carte {@code seedKey -> sub}
 * consommée par les services aval. La remise à zéro ne touche QUE les comptes de démonstration ;
 * le compte réel {@code owner} et les comptes de service ne sont jamais supprimés.
 */
@Service
@ConditionalOnProperty(name = "app.dev-tools.enabled", havingValue = "true")
public class DevDataService {

    private static final Logger log = LoggerFactory.getLogger(DevDataService.class);

    /** Mot de passe commun aux comptes de démonstration (environnement de dev uniquement). */
    public static final String DEV_PASSWORD = "Passw0rd!";

    /** Définition d'un utilisateur de démonstration, alignée sur le jeu de données frontend (mock/db.ts). */
    private record SeedUser(String key, String username, String email, String firstName, String lastName,
                            List<String> phones, Role role, String permitNumber) {
    }

    private static final List<SeedUser> SEED_USERS = List.of(
            new SeedUser("u-owner", "directeur", "directeur@autoecole-lucky.fr", "Karim", "Ben Salah",
                    List.of("+33 6 12 34 56 78"), Role.OWNER, null),
            new SeedUser("u-sec", "secretaire", "accueil@autoecole-lucky.fr", "Sophie", "Marchand",
                    List.of("+33 6 22 33 44 55"), Role.SECRETARY, null),
            new SeedUser("u-mon-1", "moniteur.paul", "paul@autoecole-lucky.fr", "Paul", "Girard",
                    List.of("+33 6 33 44 55 66"), Role.MONITOR, "MON-2019-0421"),
            new SeedUser("u-mon-2", "moniteur.nadia", "nadia@autoecole-lucky.fr", "Nadia", "Lefevre",
                    List.of("+33 6 44 55 66 77"), Role.MONITOR, "MON-2021-0187"),
            new SeedUser("u-cli-1", "lucas.m", "lucas.martin@example.com", "Lucas", "Martin",
                    List.of("+33 6 55 66 77 88"), Role.CLIENT, null),
            new SeedUser("u-cli-2", "emma.d", "emma.dubois@example.com", "Emma", "Dubois",
                    List.of("+33 6 66 77 88 99"), Role.CLIENT, null),
            new SeedUser("u-cli-3", "hugo.b", "hugo.bernard@example.com", "Hugo", "Bernard",
                    List.of("+33 6 77 88 99 00"), Role.CLIENT, null),
            new SeedUser("u-cli-4", "chloe.p", "chloe.petit@example.com", "Chloe", "Petit",
                    List.of("+33 6 88 99 00 11"), Role.CLIENT, null));

    private static final Set<String> SEED_USERNAMES = SEED_USERS.stream()
            .map(SeedUser::username).collect(Collectors.toUnmodifiableSet());

    /** Comptes à ne JAMAIS supprimer, même s'ils apparaissaient dans la base locale. */
    private static final String PROTECTED_OWNER = "owner";
    private static final String SERVICE_ACCOUNT_PREFIX = "service-account-";

    private final UserService userService;
    private final UserRepository userRepository;
    private final KeycloakService keycloakService;

    @PersistenceContext
    private EntityManager entityManager;

    public DevDataService(UserService userService, UserRepository userRepository,
                          KeycloakService keycloakService) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.keycloakService = keycloakService;
    }

    /**
     * Crée les 8 comptes de démonstration (idempotent : les comptes déjà présents sont ignorés mais
     * leur {@code sub} est tout de même renvoyé, pour une carte stable entre deux exécutions).
     */
    @Transactional
    public SeedUsersResponse seed() {
        Map<String, String> map = new LinkedHashMap<>();
        int created = 0;
        int skipped = 0;
        for (SeedUser su : SEED_USERS) {
            Optional<User> existing = userRepository.findByUsername(su.username());
            if (existing.isPresent()) {
                map.put(su.key(), existing.get().getKeycloakId());
                skipped++;
                continue;
            }
            CreateUserRequest request = new CreateUserRequest(
                    su.username(), su.email(), su.firstName(), su.lastName(),
                    new LinkedHashSet<>(su.phones()), DEV_PASSWORD, Set.of(su.role()),
                    su.permitNumber(), Boolean.TRUE);
            UserResponse res = userService.createUser(request);
            map.put(su.key(), res.keycloakId());
            created++;
        }
        log.info("Seed auth: {} créés, {} ignorés", created, skipped);
        return new SeedUsersResponse(map, new SeedResult(created, skipped));
    }

    /**
     * Supprime UNIQUEMENT les comptes de démonstration (Keycloak + base) et vide le journal d'audit.
     * Garde-fous : le compte {@code owner}, les comptes {@code service-account-*} et l'appelant
     * lui-même ({@code callerKeycloakId}) sont préservés — un opérateur connecté en tant que compte
     * de démonstration (p. ex. {@code directeur}) ne peut pas se supprimer en pleine session.
     *
     * @param callerKeycloakId le {@code sub} de l'utilisateur authentifié, ou {@code null}
     */
    @Transactional
    public void reset(String callerKeycloakId) {
        int deleted = 0;
        for (User user : userRepository.findAll()) {
            String username = user.getUsername();
            if (username == null) {
                continue;
            }
            if (PROTECTED_OWNER.equals(username) || username.startsWith(SERVICE_ACCOUNT_PREFIX)) {
                continue;
            }
            if (!SEED_USERNAMES.contains(username)) {
                continue;
            }
            if (user.getKeycloakId().equals(callerKeycloakId)) {
                continue; // ne pas supprimer l'opérateur en cours
            }
            keycloakService.deleteUser(user.getKeycloakId());
            userRepository.delete(user);
            deleted++;
        }
        entityManager.createNativeQuery("TRUNCATE TABLE audit_event").executeUpdate();
        log.info("Reset auth: {} comptes de démonstration supprimés, audit vidé", deleted);
    }

    @Transactional(readOnly = true)
    public AuthExport export() {
        return new AuthExport(userService.listUsers(null));
    }
}
