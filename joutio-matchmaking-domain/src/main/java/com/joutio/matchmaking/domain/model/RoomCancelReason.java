package com.joutio.matchmaking.domain.model;

/** Motif d'annulation d'une salle. */
public enum RoomCancelReason {
    /** L'initiateur annule (bouton). */
    PLAYER_CANCELLED,
    /** Un blocage entre les deux joueurs met fin à la salle. */
    PLAYER_BLOCKED
}
