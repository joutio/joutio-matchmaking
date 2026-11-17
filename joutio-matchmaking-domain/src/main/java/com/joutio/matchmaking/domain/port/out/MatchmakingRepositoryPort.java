package com.joutio.matchmaking.domain.port.out;

import com.joutio.matchmaking.domain.model.Matchmaking;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Port sortant — read model « ticket » d'appariement (vue produit).
 */
public interface MatchmakingRepositoryPort {

    void save(Matchmaking matchmaking);

    Optional<Matchmaking> findById(String matchmakingId);

    /** Recherches encore {@code SEARCHING} dont l'échéance précède {@code before} (deadline perdue). */
    List<String> findSearchingIdsWithDeadlineBefore(Instant before);

    /** Tickets terminaux sans mise à jour depuis {@code before} (purge perdue). */
    List<String> findTerminalIdsBefore(Instant before);

    void deleteById(String matchmakingId);
}
