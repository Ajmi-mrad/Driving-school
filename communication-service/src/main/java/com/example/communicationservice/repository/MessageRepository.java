package com.example.communicationservice.repository;

import com.example.communicationservice.domain.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    /** Historique d'une conversation (page la plus récente d'abord). */
    Page<Message> findByConversationIdOrderBySentAtDesc(UUID conversationId, Pageable pageable);

    /**
     * Marque comme lus tous les messages d'une conversation qui me sont adressés (envoyés par
     * l'autre) et non encore lus. {@code @Modifying} = requête d'écriture (UPDATE en masse).
     */
    @Modifying
    @Query("""
            update Message m set m.readAt = :now
            where m.conversationId = :conversationId
              and m.senderId <> :me
              and m.readAt is null
            """)
    int markRead(@Param("conversationId") UUID conversationId,
                 @Param("me") String me,
                 @Param("now") Instant now);

    /**
     * Nombre total de messages non lus qui me sont adressés, toutes mes conversations confondues
     * (pour le badge / la cloche).
     */
    @Query("""
            select count(m) from Message m
            where m.readAt is null
              and m.senderId <> :me
              and m.conversationId in (
                  select c.id from Conversation c where c.monitorId = :me or c.clientId = :me)
            """)
    long countUnreadForUser(@Param("me") String me);
}