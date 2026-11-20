package com.joutio.matchmaking.domain.model;

/**
 * Origine d'une salle, fixée à la création : distingue dans Défis les salons privés partagés des défis acceptés et des
 * revanches (`defis.md`).
 */
public enum RoomOrigin {
    /** Salon privé ouvert par un joueur, rejoint par lien. */
    SHARED,
    /** Défi nominatif accepté. */
    CHALLENGE,
    /** Défi lancé depuis un résultat (revanche). */
    REMATCH,
    /** Appariement public. */
    MATCHMAKING,
    /** Entraînement contre le bot. */
    BOT
}
