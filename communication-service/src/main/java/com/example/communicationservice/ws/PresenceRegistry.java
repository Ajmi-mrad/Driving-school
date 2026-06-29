package com.example.communicationservice.ws;

import com.example.communicationservice.web.dto.PresenceEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registre de présence en mémoire : qui est connecté en WebSocket. Alimenté par les événements de
 * cycle de vie des sessions STOMP ; chaque changement est diffusé sur {@code /topic/presence} pour
 * que les clients mettent à jour leurs indicateurs « en ligne ».
 *
 * <p>Limite v1 assumée : on ne compte pas les sessions multiples d'un même utilisateur — une
 * déconnexion le marque hors-ligne même s'il a un autre onglet ouvert. Suffisant pour un indicateur
 * indicatif ; un compteur par utilisateur serait l'amélioration naturelle.
 */
@Component
public class PresenceRegistry {

    private static final String TOPIC_PRESENCE = "/topic/presence";

    private final Set<String> onlineUsers = ConcurrentHashMap.newKeySet();
    private final SimpMessagingTemplate messagingTemplate;

    public PresenceRegistry(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        String sub = nameOf(event.getUser());
        if (sub != null && onlineUsers.add(sub)) {
            messagingTemplate.convertAndSend(TOPIC_PRESENCE, new PresenceEvent(sub, true));
        }
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        String sub = nameOf(event.getUser());
        if (sub != null && onlineUsers.remove(sub)) {
            messagingTemplate.convertAndSend(TOPIC_PRESENCE, new PresenceEvent(sub, false));
        }
    }

    public boolean isOnline(String userId) {
        return onlineUsers.contains(userId);
    }

    /** Sous-ensemble des identifiants donnés qui sont en ligne. */
    public Set<String> filterOnline(Collection<String> userIds) {
        return userIds.stream().filter(onlineUsers::contains).collect(Collectors.toSet());
    }

    private String nameOf(Principal principal) {
        return principal != null ? principal.getName() : null;
    }
}