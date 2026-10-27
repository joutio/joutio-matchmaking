package com.joutio.matchmaking.domain.model;

import com.joutio.microservice.core.domain.model.i18n.Language;

import java.time.Instant;
import java.util.Set;

/**
 * Candidat d'appariement extrait du pool de recherche (index dérivé, cf. {@code MatchmakingPoolPort}).
 */
public record MatchmakingCandidate(
        String matchmakingId,
        String playerId,
        Set<Language> languages,
        Instant createdAt
) {
}
