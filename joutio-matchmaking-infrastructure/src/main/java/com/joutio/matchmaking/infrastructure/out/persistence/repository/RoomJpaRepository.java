package com.joutio.matchmaking.infrastructure.out.persistence.repository;

import com.joutio.matchmaking.domain.model.RoomStatus;
import com.joutio.matchmaking.infrastructure.out.persistence.entity.RoomEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface RoomJpaRepository extends JpaRepository<RoomEntity, String> {

    @Query("""
            select r from RoomEntity r
            where r.status in :statuses
              and (r.initiatorId = :playerId or r.opponentId = :playerId or r.participantId = :playerId)
            order by r.createdAt desc
            """)
    List<RoomEntity> findByPlayerIdAndStatusIn(
            @Param("playerId") String playerId, @Param("statuses") Collection<RoomStatus> statuses);

    @Query("""
            select r from RoomEntity r
            where r.status in :statuses
              and ((r.initiatorId = :a and (r.opponentId = :b or r.participantId = :b))
                or (r.initiatorId = :b and (r.opponentId = :a or r.participantId = :a)))
            """)
    List<RoomEntity> findBetween(
            @Param("a") String a, @Param("b") String b, @Param("statuses") Collection<RoomStatus> statuses);

    @Query("""
            select r.roomId from RoomEntity r
            where r.status in :statuses
              and r.updatedAt < :before
            """)
    List<String> findIdsByStatusUpdatedBefore(@Param("statuses") Collection<RoomStatus> statuses,
                                              @Param("before") Instant before);

    @Query("""
            select r.roomId from RoomEntity r
            where r.status in :statuses
              and r.expiresAt < :before
            """)
    List<String> findIdsByStatusExpiringBefore(@Param("statuses") Collection<RoomStatus> statuses,
                                               @Param("before") Instant before);
}
