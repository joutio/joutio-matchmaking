package com.joutio.matchmaking.domain.model;

import com.joutio.microservice.core.domain.model.i18n.Language;

/**
 * Joueur résolu depuis joutio-profile (nom, langue, progression à l'instant présent).
 */
public record RoomPlayer(
        String playerId,
        String playerName,
        Language language,
        /** XP cumulée du joueur dans le sujet de la salle (difficulté des questions). */
        int topicXp,
        /** Elo au moment de l'entrée en salle (snapshot porté par la partie). */
        Integer rating
) {
}
