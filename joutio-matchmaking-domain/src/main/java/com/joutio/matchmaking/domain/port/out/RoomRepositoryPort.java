package com.joutio.matchmaking.domain.port.out;

import com.joutio.matchmaking.domain.model.Room;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RoomRepositoryPort {

    void save(Room room);

    Optional<Room> findById(String roomId);

    /** Salles non terminales d'un joueur (initiateur, invité ou participant), les plus récentes d'abord. */
    List<Room> findActiveByPlayerId(String playerId);

    /** Salles {@code INVITED} ou {@code WAITING_PLAYERS} qui réunissent les deux joueurs, dans un sens ou l'autre. */
    List<Room> findOpenBetween(String playerId, String otherPlayerId);

    /** Salles {@code INVITED} ou {@code WAITING_PLAYERS} dont l'échéance est antérieure à {@code before}. */
    List<String> findOpenIdsExpiredBefore(Instant before);

    /** Salles en préparation ({@code FETCHING_*}, {@code CREATING_GAME}) sans mise à jour depuis {@code before}. */
    List<String> findStalledPreparationIdsBefore(Instant before);

    /** Salles terminales sans mise à jour depuis {@code before} (purge perdue). */
    List<String> findTerminalIdsBefore(Instant before);

    void deleteById(String roomId);
}
