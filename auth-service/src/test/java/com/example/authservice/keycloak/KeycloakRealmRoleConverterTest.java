package com.example.authservice.keycloak;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRealmRoleConverterTest {

    private final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();

    @Test
    void mapsRealmRolesToPrefixedAuthorities() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-1")
                .claim("realm_access", Map.of("roles", List.of("OWNER", "CLIENT")))
                .build();

        Collection<GrantedAuthority> authorities = converter.convert(jwt);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_OWNER", "ROLE_CLIENT");
    }

    @Test
    void returnsEmptyWhenNoRealmAccessClaim() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-1")
                .build();

        assertThat(converter.convert(jwt)).isEmpty();
    }
}