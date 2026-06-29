package com.example.authservice.web;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Identité de l'utilisateur authentifié, telle que vue par le service après validation du JWT
 * et conversion des rôles. Utile pour vérifier de bout en bout l'authentification.
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    @GetMapping
    public Map<String, Object> me(JwtAuthenticationToken authentication) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("subject", authentication.getToken().getSubject());
        body.put("username", authentication.getToken().getClaimAsString("preferred_username"));
        body.put("email", authentication.getToken().getClaimAsString("email"));
        body.put("authorities", authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList());
        return body;
    }
}