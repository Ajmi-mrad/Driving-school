package com.example.authservice;

import com.example.authservice.config.AuditConfig;
import com.example.authservice.domain.Role;
import com.example.authservice.domain.User;
import com.example.authservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie l'étape 2 : Flyway crée le schéma, Hibernate le valide (ddl-auto=validate), et les
 * colonnes d'audit se remplissent automatiquement à la persistance.
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "eureka.client.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, AuditConfig.class})
class UserAuditIT {

    @Autowired
    UserRepository userRepository;

    @Test
    void persistsUserAndPopulatesAuditColumns() {
        User user = new User();
        user.setKeycloakId(UUID.randomUUID().toString());
        user.setUsername("audit-" + UUID.randomUUID());
        user.setEmail("audit-" + UUID.randomUUID() + "@example.com");
        user.setFirstName("Test");
        user.setLastName("Client");
        user.addRole(Role.CLIENT);
        user.setPermitNumber("P-123");

        User saved = userRepository.saveAndFlush(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getRoles()).containsExactly(Role.CLIENT);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getCreatedBy()).isEqualTo("system");
        assertThat(saved.getUpdatedBy()).isEqualTo("system");
    }
}