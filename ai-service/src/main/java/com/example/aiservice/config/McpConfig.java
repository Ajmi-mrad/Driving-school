package com.example.aiservice.config;

import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

/**
 * Authentifie les appels vers le serveur MCP GitHub (hébergé, mode read-only). Le customizer est
 * appliqué à toutes les connexions MCP HTTP : on n'ajoute le token que pour l'hôte GitHub, pour ne
 * jamais l'envoyer au serveur MCP Grafana.
 */
@Configuration
public class McpConfig {

    @Bean
    McpSyncHttpClientRequestCustomizer githubMcpAuth(
            @Value("${spring.ai.mcp.client.streamable-http.connections.github.url}") URI githubMcpUrl,
            @Value("${ai.ops.github.token:}") String token,
            @Value("${ai.ops.github.toolsets}") String toolsets) {
        return (request, method, endpoint, body, context) -> {
            if (!token.isBlank() && githubMcpUrl.getHost().equals(endpoint.getHost())) {
                request.header("Authorization", "Bearer " + token);
                // Ne charge que les familles d'outils utiles (commits, CI/CD) : moins de tokens par requête.
                request.header("X-MCP-Toolsets", toolsets);
            }
        };
    }
}
