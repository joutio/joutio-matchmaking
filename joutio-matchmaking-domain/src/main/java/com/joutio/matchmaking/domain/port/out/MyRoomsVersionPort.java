package com.joutio.matchmaking.domain.port.out;

/**
 * Port sortant - version des salles d'un joueur : un compteur par joueur, incrémenté atomiquement à chaque écriture
 * d'une de ses salles, donc monotone même si plusieurs segments écrivent en même temps.
 */
public interface MyRoomsVersionPort {

    /** Incrémente la version du joueur et renvoie la nouvelle valeur (1 à la première écriture). */
    long increment(String playerId);

    /** Version courante, 0 si aucune salle du joueur n'a encore été écrite. */
    long current(String playerId);
}
