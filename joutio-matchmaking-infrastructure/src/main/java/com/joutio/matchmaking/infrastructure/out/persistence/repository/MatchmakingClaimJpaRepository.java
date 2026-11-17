package com.joutio.matchmaking.infrastructure.out.persistence.repository;

import com.joutio.matchmaking.infrastructure.out.persistence.entity.MatchmakingClaimEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface MatchmakingClaimJpaRepository extends JpaRepository<MatchmakingClaimEntity, String> {

    /**
     * Insère les deux réclamations ; une recherche déjà réclamée est ignorée (pas d'erreur, donc
     * pas de transaction marquée en échec). Renvoie le nombre de lignes insérées : 2 si la paire
     * est libre.
     */
    @Modifying
    @Query(value = """
            insert into matchmaking_claim (matchmaking_id, claimed_at)
            values (:first, :now), (:second, :now)
            on conflict (matchmaking_id) do nothing
            """, nativeQuery = true)
    int insertPair(@Param("first") String first,
                   @Param("second") String second,
                   @Param("now") Instant now);
}
