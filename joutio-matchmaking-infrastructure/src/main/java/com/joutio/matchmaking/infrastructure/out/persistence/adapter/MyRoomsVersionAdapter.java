package com.joutio.matchmaking.infrastructure.out.persistence.adapter;

import com.joutio.matchmaking.domain.port.out.MyRoomsVersionPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Version des salles d'un joueur ({@code my_rooms_version}) : un upsert atomique, qui verrouille la ligne jusqu'à la
 * fin de la transaction. Deux écritures concurrentes pour le même joueur obtiennent donc deux versions successives, et
 * la seconde lit les salles laissées par la première.
 */
@Component
public class MyRoomsVersionAdapter implements MyRoomsVersionPort {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public long increment(String playerId) {
        Number version = (Number) entityManager.createNativeQuery("""
                        INSERT INTO my_rooms_version (player_id, version) VALUES (:playerId, 1)
                        ON CONFLICT (player_id) DO UPDATE SET version = my_rooms_version.version + 1
                        RETURNING version""")
                .setParameter("playerId", playerId)
                .getSingleResult();
        return version.longValue();
    }

    @Override
    @Transactional(readOnly = true)
    public long current(String playerId) {
        List<?> rows = entityManager
                .createNativeQuery("SELECT version FROM my_rooms_version WHERE player_id = :playerId")
                .setParameter("playerId", playerId)
                .getResultList();
        return rows.isEmpty() ? 0 : ((Number) rows.getFirst()).longValue();
    }
}
