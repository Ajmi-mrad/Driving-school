package com.example.authservice.dev;

import com.example.authservice.dev.dto.AuthExport;
import com.example.authservice.dev.dto.SeedUsersResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints d'administration des données — OUTIL DE DÉVELOPPEMENT.
 *
 * <p>N'existe que lorsque {@code app.dev-tools.enabled=true} (sinon le bean n'est pas créé et les
 * routes renvoient 404 : impossible en production). Réservé au propriétaire ({@code OWNER}).</p>
 */
@RestController
@RequestMapping("/api/admin/auth")
@ConditionalOnProperty(name = "app.dev-tools.enabled", havingValue = "true")
@PreAuthorize("hasRole('OWNER')")
public class AdminController {

    private final DevDataService devDataService;

    public AdminController(DevDataService devDataService) {
        this.devDataService = devDataService;
    }

    @PostMapping("/seed")
    public SeedUsersResponse seed() {
        return devDataService.seed();
    }

    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(JwtAuthenticationToken auth) {
        // auth.getName() = claim `sub` = keycloakId de l'appelant (préservé de la suppression).
        devDataService.reset(auth == null ? null : auth.getName());
    }

    @GetMapping("/export")
    public AuthExport export() {
        return devDataService.export();
    }
}
