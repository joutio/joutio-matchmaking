package com.joutio.matchmaking.domain.query;

/**
 * Queries de la salle.
 */
public interface RoomQuery {

    record GetRoomById(String roomId) implements RoomQuery {
    }

    /** Salles non terminales du joueur : invitations reçues et envoyées, salles en attente ou en préparation. */
    record GetMyOpenRooms(String playerId) implements RoomQuery {
    }

    record GetRoomEventsQuery(String roomId) implements RoomQuery {
    }
}
