package com.joutio.matchmaking.domain.model;

import lombok.Builder;

import java.time.Instant;

/** Read model d'une salle. L'identifiant de l'agrégat sert de référence de partage ({@code /j/{roomId}}). */
@Builder(toBuilder = true)
public record Room(
        String roomId,
        String topicId,
        String initiatorId,
        String opponentId,
        String participantId,
        String gameId,
        boolean vsBot,
        RoomOrigin origin,
        /** Partie terminée d'origine d'une revanche. */
        String sourceGameId,
        RoomStatus status,
        boolean initiatorPresent,
        boolean participantPresent,
        Instant createdAt,
        /** Échéance de l'étape en cours (réponse à l'invitation, puis attente des joueurs). */
        Instant expiresAt,
        /** Prochaine relance possible d'une invitation, sinon {@code null}. */
        Instant remindableAt,
        Instant updatedAt
) {
}
