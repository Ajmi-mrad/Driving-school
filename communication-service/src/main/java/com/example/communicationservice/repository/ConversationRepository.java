package com.example.communicationservice.repository;

import com.example.communicationservice.domain.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /** Conversation existante pour une paire (moniteur, élève) — sert au get-or-create. */
    Optional<Conversation> findByMonitorIdAndClientId(String monitorId, String clientId);

    /** Inbox d'un utilisateur : toutes les conversations où il est moniteur OU élève, plus récentes d'abord. */
    List<Conversation> findByMonitorIdOrClientIdOrderByLastMessageAtDesc(String monitorId, String clientId);
}