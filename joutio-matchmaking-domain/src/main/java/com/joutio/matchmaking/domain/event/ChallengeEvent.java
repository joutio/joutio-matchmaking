package com.joutio.matchmaking.domain.event;

import java.time.Instant;

/**
 * Événements du défi nominatif (intention asynchrone). La salle temps réel (présence, ready check,
 * création de partie) porte ses propres événements.
 * <p>
 * {@code ChallengeCreatedEvent.gameId} référence — quand il est renseigné — la <b>partie terminée
 * d'origine</b> d'une revanche (jamais la future partie, qui naîtra de la salle).
 */
public interface ChallengeEvent {

    String challengeId();

    record ChallengeCreatedEvent(
            String challengeId,
            String topicId,
            String challengerId,
            String opponentId,
            String gameId,
            Instant expiresAt,
            Instant createdAt
    ) implements ChallengeEvent {
    }

    record ChallengeAcceptedEvent(
            String challengeId,
            String topicId,
            String challengerId,
            String opponentId,
            Instant acceptedAt
    ) implements ChallengeEvent {
    }

    /**
     * La salle du défi a été créée ({@code RoomCreatedEvent}) : le défi expose alors son
     * {@code roomId}. Distinct de l'acceptation, qui ne garantit pas que la salle existe encore
     * (création fire-and-forget par la saga).
     */
    record ChallengeRoomCreatedEvent(
            String challengeId,
            String roomId,
            Instant roomCreatedAt
    ) implements ChallengeEvent {
    }

    /**
     * La **partie du défi a été créée** : la salle issue de l'acceptation a produit un duel (les
     * deux joueurs se sont présentés). C'est le fait « le défi a abouti » — distinct de
     * l'acceptation, qui ne garantit pas que l'invité vienne jouer.
     */
    record ChallengeStartedEvent(
            String challengeId,
            String challengerId,
            String opponentId,
            String gameId,
            Instant startedAt
    ) implements ChallengeEvent {
    }

    /**
     * Le lanceur relance son défi en attente : l'invitation est rappelée à l'adversaire (inbox remise en tête, push).
     * Porte de quoi recréer l'invitation ; {@code remindableAt} est l'instant de la relance suivante possible.
     */
    record ChallengeRemindedEvent(
            String challengeId,
            String topicId,
            String challengerId,
            String opponentId,
            String gameId,
            Instant expiresAt,
            Instant remindedAt,
            Instant remindableAt
    ) implements ChallengeEvent {
    }

    record ChallengeDeclinedEvent(
            String challengeId,
            String challengerId,
            String opponentId,
            Instant declinedAt
    ) implements ChallengeEvent {
    }

    record ChallengeCancelledEvent(
            String challengeId,
            String challengerId,
            Instant cancelledAt
    ) implements ChallengeEvent {
    }

    record ChallengeExpiredEvent(
            String challengeId,
            Instant expiredAt
    ) implements ChallengeEvent {
    }

    /** Purge après rétention : l'agrégat est supprimé et la projection supprime sa ligne. */
    record ChallengePurgedEvent(
            String challengeId,
            Instant purgedAt
    ) implements ChallengeEvent {
    }
}
