package com.joutio.matchmaking.domain.model;

/**
 * Origine d'une salle, fixée à sa création. Elle décide de qui peut devenir le second joueur :
 * <ul>
 *   <li>défi et revanche : un joueur nommé, qui doit d'abord accepter ({@link #requiresAcceptance()}) ;</li>
 *   <li>salon partagé : le premier qui ouvre le lien et entre (aucun invité nommé) ;</li>
 *   <li>appariement : les deux joueurs nommés ont déjà consenti en cherchant ;</li>
 *   <li>bot : le joueur système, présent dès la création.</li>
 * </ul>
 */
public enum RoomOrigin {
    /** Salon privé ouvert par un joueur, rejoint par lien. */
    SHARED,
    /** Défi nominatif. */
    CHALLENGE,
    /** Défi lancé depuis le résultat d'une partie (revanche). */
    REMATCH,
    /** Appariement public. */
    MATCHMAKING,
    /** Entraînement contre le bot. */
    BOT;

    /** Le second joueur est nommé et doit accepter avant d'entrer : la salle commence en {@code INVITED}. */
    public boolean requiresAcceptance() {
        return this == CHALLENGE || this == REMATCH;
    }

    /** Le second joueur est nommé dès la création. */
    public boolean isNominative() {
        return this == CHALLENGE || this == REMATCH || this == MATCHMAKING;
    }
}
