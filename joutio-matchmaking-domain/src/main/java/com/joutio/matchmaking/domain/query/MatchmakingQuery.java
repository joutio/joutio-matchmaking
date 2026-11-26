package com.joutio.matchmaking.domain.query;

/**
 * Queries de la recherche d'appariement public.
 */
public interface MatchmakingQuery {

    record GetMatchmakingByIdQuery(String matchmakingId) implements MatchmakingQuery {
    }

    /** Flux du ticket : l'historique, puis chaque événement émis au commit (vue vivante). */
    record GetMatchmakingEventsQuery(String matchmakingId) implements MatchmakingQuery {
    }
}
