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

    /**
     * Vue vivante des salles du joueur ({@link com.joutio.matchmaking.domain.model.MyRooms}) : même contenu que
     * {@link GetMyOpenRooms}, versionné, et émis à chaque écriture d'une de ses salles.
     */
    record GetMyRooms(String playerId) implements RoomQuery {
    }

    /** Flux de la salle : l'historique, puis chaque événement émis au commit (vue vivante). */
    record GetRoomEventsQuery(String roomId) implements RoomQuery {
    }
}
