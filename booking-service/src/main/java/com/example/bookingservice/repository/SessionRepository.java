package com.example.bookingservice.repository;

import com.example.bookingservice.domain.Session;
import com.example.bookingservice.domain.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

public interface SessionRepository
        extends JpaRepository<Session, UUID>, JpaSpecificationExecutor<Session> {

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

    // Recherche filtrée : voir SessionSpecifications#filter (findAll(Specification, Sort)).
    // Les prédicats nuls sont omis pour éviter l'ambiguïté de type Postgres sur « :param is null ».
}