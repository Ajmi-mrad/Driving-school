package com.example.communicationservice.config;

import com.example.communicationservice.keycloak.KeycloakRealmRoleConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Sécurité en mode Resource Server OAuth2 : chaque requête HTTP entrante doit porter un JWT signé
 * par Keycloak (validé via la JWKS du realm). Le RBAC fin est porté par @PreAuthorize.
 *
 * <p>CORS n'est PAS géré ici : le frontend n'atteint ce service qu'à travers l'api-gateway, qui
 * porte la configuration CORS centralisée (une seule origine autorisée). Définir CORS en plus ici
 * dupliquait l'en-tête {@code Access-Control-Allow-Origin} dans la réponse, ce que le navigateur
 * rejette (« Failed to fetch ») — les autres services s'appuient tous uniquement sur la gateway.
 *
 * <p>Le point d'entrée WebSocket {@code /ws/**} (handshake + transport SockJS) est laissé en
 * {@code permitAll} : l'authentification de la session STOMP est faite séparément, sur la frame
 * {@code CONNECT}, par {@code StompAuthChannelInterceptor} (le token y est porté en en-tête STOMP,
 * pas dans la requête HTTP de handshake).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }
}
