package com.joutio.matchmaking.domain.model;

/** Étape d'une relance : renvoi d'une invitation, ou appel d'un joueur absent de la salle (« wizz »). */
public enum RoomReminderStage {
    INVITATION,
    PRESENCE
}
