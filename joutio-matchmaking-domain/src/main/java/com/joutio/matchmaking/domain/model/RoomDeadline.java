package com.joutio.matchmaking.domain.model;

import java.time.Duration;
import java.time.Instant;

/**
 * Deadlines de la salle. L'échéance d'expiration ({@link #ROOM_EXPIRY}) dépend de l'étape : réponse à une
 * invitation, puis attente des joueurs. Les deadlines de l'appariement public vivent dans {@link MatchmakingDeadline}.
 */
public interface RoomDeadline {

    /** Expiration de l'étape en cours : invitation sans réponse ou salle jamais lancée. */
    String ROOM_EXPIRY = "room-expiry";

    /** Réponse à un défi. */
    Duration INVITATION_EXPIRY_DURATION = Duration.ofHours(1);

    /** Réponse à une revanche : le lanceur attend sur l'écran de résultat. */
    Duration REMATCH_INVITATION_EXPIRY_DURATION = Duration.ofSeconds(10);

    /** Attente des deux joueurs dans une salle ouverte. */
    Duration ROOM_EXPIRY_DURATION = Duration.ofDays(1);

    /** Échéance d'une salle qui vient d'être créée. */
    static Instant expiryAtCreation(RoomOrigin origin, Instant createdAt) {
        return createdAt.plus(switch (origin) {
            case CHALLENGE -> INVITATION_EXPIRY_DURATION;
            case REMATCH -> REMATCH_INVITATION_EXPIRY_DURATION;
            case SHARED, MATCHMAKING, BOT -> ROOM_EXPIRY_DURATION;
        });
    }

    /**
     * Accusé de création de la partie ({@code GameCreatedEvent}) attendu par la saga : un accusé manquant fait
     * échouer la salle au lieu de la laisser bloquée en {@code CREATING_GAME}.
     */
    String ROOM_GAME_CREATION = "room-game-creation";

    Duration ROOM_GAME_CREATION_DURATION = Duration.ofSeconds(30);

    /** Borne d'une étape de préparation asynchrone : une réponse perdue fait échouer la salle. */
    Duration ROOM_PREPARATION_STEP_TIMEOUT = Duration.ofSeconds(10);

    /** Rétention d'un état terminal avant purge : le client lit l'état. */
    String ROOM_PURGE = "room-purge";

    Duration ROOM_RETENTION_DURATION = Duration.ofMinutes(2);

    /**
     * Filet du balayeur ({@code RoomSweeper}) : une salle restée plus longtemps en préparation a perdu son pas
     * suivant (deadline en mémoire perdue avec son pod).
     */
    Duration ROOM_PREPARATION_STALL = Duration.ofMinutes(1);

    /** Marge du balayeur au-delà d'une échéance (expiration ou purge) que la saga aurait dû tenir. */
    Duration ROOM_SWEEP_GRACE = Duration.ofMinutes(10);
}
