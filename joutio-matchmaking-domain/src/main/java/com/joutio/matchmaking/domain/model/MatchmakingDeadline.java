package com.joutio.matchmaking.domain.model;

import java.time.Duration;

/**
 * Deadlines de l'appariement public (« Défier le monde »).
 */
public interface MatchmakingDeadline {

    /**
     * Fenêtre de recherche d'un adversaire humain avant **arrêt** (statut {@code EXPIRED}). Aucun
     * repli bot automatique : passé le délai, le client propose de relancer une recherche ou de
     * basculer explicitement sur un entraînement contre un robot (sans XP). L'échéance est aussi
     * exposée au client ({@code deadlineAt}) pour le compte à rebours et son tic-tac.
     */
    String MATCHMAKING_DEADLINE = "matchmaking-deadline";

    Duration MATCHMAKING_DEADLINE_DURATION = Duration.ofSeconds(10);

    /**
     * Accusé de création de la salle d'appariement ({@code RoomCreatedEvent}) attendu par la saga :
     * la commande est fire-and-forget, donc un accusé manquant fait échouer les deux tickets au lieu
     * de les laisser en recherche.
     */
    String MATCHMAKING_ROOM_CREATION = "matchmaking-room-creation";

    Duration MATCHMAKING_ROOM_CREATION_DURATION = Duration.ofSeconds(30);

    /** Rétention d'un ticket d'appariement terminal avant purge (read model). */
    String MATCHMAKING_PURGE = "matchmaking-purge";

    Duration MATCHMAKING_RETENTION_DURATION = Duration.ofMinutes(2);

    /**
     * Marge du balayeur d'appariement ({@code MatchmakingSweeper}) après l'échéance d'une recherche
     * encore {@code SEARCHING} : la deadline en mémoire a été perdue avec son pod.
     */
    Duration MATCHMAKING_EXPIRY_GRACE = Duration.ofSeconds(30);

    /** Marge avant que le balayeur ne purge un ticket terminal dont la purge a été perdue. */
    Duration MATCHMAKING_SWEEP_GRACE = Duration.ofMinutes(10);
}
