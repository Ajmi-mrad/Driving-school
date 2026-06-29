package com.example.bookingservice.repository;

import com.example.bookingservice.domain.Session;
import com.example.bookingservice.domain.SessionStatus;
import com.example.bookingservice.domain.SessionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {

    /**
     * Chevauchement de créneau pour un moniteur. Deux intervalles [start,end) se chevauchent ssi
     * {@code existing.start < new.end ET existing.end > new.start}. {@code excludeId} permet d'ignorer
     * la séance en cours de modification (report / confirmation).
     */
    @Query("""
            select count(s) > 0 from Session s
            where s.monitorId = :monitorId
              and s.status in :statuses
              and s.startTime < :end and s.endTime > :start
              and (:excludeId is null or s.id <> :excludeId)
            """)
    boolean monitorHasOverlap(@Param("monitorId") String monitorId,
                              @Param("statuses") Collection<SessionStatus> statuses,
                              @Param("start") Instant start,
                              @Param("end") Instant end,
                              @Param("excludeId") UUID excludeId);

    @Query("""
            select count(s) > 0 from Session s
            where s.vehicleId = :vehicleId
              and s.status in :statuses
              and s.startTime < :end and s.endTime > :start
              and (:excludeId is null or s.id <> :excludeId)
            """)
    boolean vehicleHasOverlap(@Param("vehicleId") UUID vehicleId,
                              @Param("statuses") Collection<SessionStatus> statuses,
                              @Param("start") Instant start,
                              @Param("end") Instant end,
                              @Param("excludeId") UUID excludeId);

    @Query("""
            select count(s) > 0 from Session s
            where s.clientId = :clientId
              and s.status in :statuses
              and s.startTime < :end and s.endTime > :start
              and (:excludeId is null or s.id <> :excludeId)
            """)
    boolean clientHasOverlap(@Param("clientId") String clientId,
                             @Param("statuses") Collection<SessionStatus> statuses,
                             @Param("start") Instant start,
                             @Param("end") Instant end,
                             @Param("excludeId") UUID excludeId);

    /** Recherche filtrée (tous les filtres sont optionnels), triée par date de début. */
    @Query("""
            select s from Session s
            where (:status is null or s.status = :status)
              and (:type is null or s.type = :type)
              and (:from is null or s.startTime >= :from)
              and (:to is null or s.startTime < :to)
              and (:monitorId is null or s.monitorId = :monitorId)
              and (:clientId is null or s.clientId = :clientId)
            order by s.startTime
            """)
    List<Session> search(@Param("status") SessionStatus status,
                         @Param("type") SessionType type,
                         @Param("from") Instant from,
                         @Param("to") Instant to,
                         @Param("monitorId") String monitorId,
                         @Param("clientId") String clientId);
}