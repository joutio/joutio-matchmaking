package com.joutio.matchmaking.domain.model;

import java.util.List;

/**
 * Salles non terminales d'un joueur (vue vivante {@code GetMyRooms}) : invitations reçues et envoyées, salles en
 * attente ou en préparation, les plus récentes d'abord. {@code version} est monotone par joueur : elle augmente à
 * chaque écriture d'une de ses salles.
 */
public record MyRooms(String playerId, long version, List<Room> rooms) {
}
