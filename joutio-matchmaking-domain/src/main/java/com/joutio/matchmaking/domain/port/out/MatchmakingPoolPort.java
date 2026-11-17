package com.joutio.matchmaking.domain.port.out;

import com.joutio.matchmaking.domain.model.MatchmakingCandidate;

import java.util.List;

/**
 * Port sortant — pool de recherche d'appariement (index dérivé des recherches en cours).
 * <p>
 * Implémentation actuelle : Postgres (la projection {@code matchmaking_entry} sert d'index).
 * Un adaptateur Redis pourra remplacer cet index sans changer le domaine : l'agrégat reste la
 * source de vérité, le pool est reconstructible à partir des {@code MatchmakingStartedEvent}.
 */
public interface MatchmakingPoolPort {

    /**
     * Candidats compatibles : même sujet, Elo ±{@code ratingWindow}, exclusion d'un joueur,
     * non réclamés. Les langues sont filtrées en aval (couverture par le thème).
     */
    List<MatchmakingCandidate> candidates(String topicId, int rating, int ratingWindow, String excludePlayerId, int limit);

    /**
     * Réclame atomiquement la paire (recherche de l'initiateur, recherche candidate) : réussit
     * seulement si aucune des deux n'est déjà réclamée. Une recherche appariée ne peut donc plus
     * être prise par un tiers, même si l'index n'a pas encore vu son appariement.
     */
    boolean claimPair(String matchmakingId, String candidateMatchmakingId);
}
