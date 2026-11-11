package com.joutio.matchmaking.domain.model;

/**
 * Pourquoi un joueur n'est plus présent dans une salle. La présence est observée par le serveur
 * (canal de présence STOMP du BFF), jamais déclarée par l'interface.
 */
public enum RoomLeaveReason {
    /** Le joueur a quitté l'écran du salon (désabonnement du canal de présence). */
    LEFT,
    /** Sa connexion temps réel s'est fermée et n'est pas revenue pendant la grâce. */
    DISCONNECTED,
    /** Le joueur est hors ligne (plus aucune session, y compris après la chute d'une instance). */
    OFFLINE
}
