package com.joutio.matchmaking.domain.port.out;

import com.joutio.matchmaking.domain.model.Matchmaking;

import java.util.Optional;

/**
 * Port sortant — read model « ticket » d'appariement (vue produit).
 */
public interface MatchmakingRepositoryPort {

    void save(Matchmaking matchmaking);

    Optional<Matchmaking> findById(String matchmakingId);

    void deleteById(String matchmakingId);
}
