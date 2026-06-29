package com.example.communicationservice.ws;

import com.example.communicationservice.keycloak.KeycloakRealmRoleConverter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;

/**
 * Authentifie la session WebSocket/STOMP au moment de la frame {@code CONNECT}.
 *
 * <p>La sécurité HTTP ne protège que la requête de handshake ; la session STOMP, elle, est
 * persistante et a besoin de sa propre identité. À la connexion, le client envoie son JWT dans
 * l'en-tête natif STOMP {@code Authorization: Bearer <token>}. On le valide avec le <b>même</b>
 * {@link JwtDecoder} que le resource server (signature via la JWKS du realm), on en dérive les rôles
 * ({@link KeycloakRealmRoleConverter}) et on attache l'authentification à la session via
 * {@code accessor.setUser(...)}. À partir de là, le nom du principal = {@code sub} du JWT, ce qui
 * permet le routage {@code /user/{sub}/queue/...} et l'injection du {@code Principal} dans les
 * {@code @MessageMapping}. Un token absent ou invalide fait échouer la connexion.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final Converter<Jwt, Collection<GrantedAuthority>> authoritiesConverter = new KeycloakRealmRoleConverter();

    public StompAuthChannelInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
                throw new IllegalArgumentException("Jeton d'authentification manquant sur la connexion WebSocket");
            }
            String token = authHeader.substring(BEARER_PREFIX.length());
            Jwt jwt = jwtDecoder.decode(token); // lève une exception si signature/exp invalides
            Collection<GrantedAuthority> authorities = authoritiesConverter.convert(jwt);
            accessor.setUser(new JwtAuthenticationToken(jwt, authorities));
        }
        return message;
    }
}