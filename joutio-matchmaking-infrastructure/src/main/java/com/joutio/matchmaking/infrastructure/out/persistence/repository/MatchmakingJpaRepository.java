package com.joutio.matchmaking.infrastructure.out.persistence.repository;

import com.joutio.matchmaking.infrastructure.out.persistence.entity.MatchmakingEntity;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface MatchmakingJpaRepository extends JpaRepository<MatchmakingEntity, String> {

    @Query("""
            select e from MatchmakingEntity e
            where e.topicId = :topicId
              and e.status = com.joutio.matchmaking.domain.model.MatchmakingStatus.SEARCHING
              and not exists (select c from MatchmakingClaimEntity c where c.matchmakingId = e.matchmakingId)
              and e.playerId <> :excludePlayerId
              and e.rating between :minRating and :maxRating
            order by abs(e.rating - :rating) asc, e.createdAt asc
            """)
    List<MatchmakingEntity> findCandidates(@Param("topicId") String topicId,
                                           @Param("excludePlayerId") String excludePlayerId,
                                           @Param("rating") int rating,
                                           @Param("minRating") int minRating,
                                           @Param("maxRating") int maxRating,
                                           Limit limit);

    @Query("""
            select e.matchmakingId from MatchmakingEntity e
            where e.status = com.joutio.matchmaking.domain.model.MatchmakingStatus.SEARCHING
              and e.deadlineAt < :before
            """)
    List<String> findSearchingIdsWithDeadlineBefore(@Param("before") Instant before);

    @Query("""
            select e.matchmakingId from MatchmakingEntity e
            where e.status <> com.joutio.matchmaking.domain.model.MatchmakingStatus.SEARCHING
              and e.updatedAt < :before
            """)
    List<String> findTerminalIdsBefore(@Param("before") Instant before);

}
