package com.joutio.matchmaking.infrastructure.out.persistence.repository;

import com.joutio.matchmaking.infrastructure.out.persistence.entity.RoomEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface RoomJpaRepository extends JpaRepository<RoomEntity, String> {

    @Query(
            """
            select r from RoomEntity r
            where r.status not in (com.joutio.matchmaking.domain.model.RoomStatus.COMPLETED, com.joutio.matchmaking.domain.model.RoomStatus.CANCELLED, com.joutio.matchmaking.domain.model.RoomStatus.EXPIRED, com.joutio.matchmaking.domain.model.RoomStatus.FAILED)
              and r.expiresAt > :now
              and (r.initiatorId = :playerId or r.opponentId = :playerId or r.participantId = :playerId)
            order by r.createdAt desc
            """)
    List<RoomEntity> findOpenByPlayerId(
            @Param("playerId") String playerId, @Param("now") Instant now);
}
