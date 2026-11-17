package com.joutio.matchmaking.domain.port.out;

import com.joutio.matchmaking.domain.model.Room;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RoomRepositoryPort {

    void save(Room room);

    Optional<Room> findById(String roomId);

    /** Salles encore ouvertes d'un joueur ({@code WAITING_PLAYERS}, initiateur ou invité). */
    List<Room> findWaitingByPlayerId(String playerId);

    /** Salles en préparation ({@code FETCHING_*}, {@code CREATING_GAME}) sans mise à jour depuis {@code before}. */
    List<String> findStalledPreparationIdsBefore(Instant before);

    /** Salles terminales sans mise à jour depuis {@code before} (purge perdue). */
    List<String> findTerminalIdsBefore(Instant before);

    void deleteById(String roomId);
}
