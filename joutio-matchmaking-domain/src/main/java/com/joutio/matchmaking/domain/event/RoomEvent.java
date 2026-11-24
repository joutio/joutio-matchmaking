package com.joutio.matchmaking.domain.event;

import com.joutio.matchmaking.domain.model.DrawnQuestion;
import com.joutio.matchmaking.domain.model.RoomCancelReason;
import com.joutio.matchmaking.domain.model.RoomLeaveReason;
import com.joutio.matchmaking.domain.model.RoomOrigin;
import com.joutio.matchmaking.domain.model.RoomPlayer;
import com.joutio.matchmaking.domain.model.RoomReminderStage;
import com.joutio.matchmaking.domain.model.RoomStatus;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Événements de la salle : invitation (défi, revanche), présence, préparation de la partie et fin. Les événements
 * qui changent la liste des salles d'un joueur portent ses destinataires ({@code roomPlayerIds} ou les deux
 * identifiants) pour que les consommateurs n'aient pas à relire la salle.
 *
 * <p>L'apparition d'un joueur ({@link RoomEnteredEvent}) porte à la
 * fois la présence, l'enregistrement du second humain et son snapshot de profil préparé par la
 * façade (aucune I/O dans la saga). Les étapes de préparation asynchrones portent leur résultat
 * pour que la saga le mémorise sans relire un read model.
 */
public interface RoomEvent {

    String roomId();

    /**
     * Salle ouverte. {@code status} vaut {@code INVITED} pour un défi ou une revanche (l'invité doit répondre), sinon
     * {@code WAITING_PLAYERS}. {@code expiresAt} est l'échéance de cette première étape ; {@code sourceGameId} la
     * partie d'origine d'une revanche.
     */
    record RoomCreatedEvent(
            String roomId,
            String topicId,
            String initiatorId,
            String opponentId,
            boolean vsBot,
            String botDifficulty,
            RoomOrigin origin,
            String sourceGameId,
            RoomStatus status,
            Instant expiresAt,
            Instant createdAt)
            implements RoomEvent {}

    /** L'invité accepte : la salle attend les deux joueurs jusqu'à {@code expiresAt}. */
    record RoomAcceptedEvent(
            String roomId,
            String topicId,
            String initiatorId,
            String opponentId,
            RoomOrigin origin,
            Instant expiresAt,
            Instant acceptedAt)
            implements RoomEvent {}

    /** L'invité refuse : la salle se termine. */
    record RoomDeclinedEvent(
            String roomId,
            String topicId,
            String initiatorId,
            String opponentId,
            RoomOrigin origin,
            Instant declinedAt)
            implements RoomEvent {}

    /**
     * Un joueur apparaît dans la salle (présence temps réel, idempotent). {@code player} est {@code
     * null} pour le bot (présence système, aucun profil à résoudre). {@code presenceId} : session
     * temps réel qui porte la présence (nul pour le bot) ; un joueur déjà
     * présent qui revient par une autre session ré-émet l'événement avec la nouvelle.
     * {@code roomPlayerIds} : joueurs humains du salon après l'entrée (initiateur, invité, participant), pour
     * notifier chacun sans relire la salle (2026-11-16).
     */
    record RoomEnteredEvent(
            String roomId,
            String playerId,
            RoomPlayer player,
            Instant enteredAt,
            String presenceId,
            Set<String> roomPlayerIds)
            implements RoomEvent {}

    /**
     * Les deux joueurs sont présents : la préparation démarre immédiatement ({@code
     * FETCHING_PLAYERS}) ; la partie est créée dès la fin de la préparation ({@link
     * RoomQuestionsLoadedEvent}).
     */
    record RoomAllPlayersPresentEvent(String roomId, Instant presentAt) implements RoomEvent {}

    /**
     * Les profils des joueurs sont résolus : la préparation passe en {@code
     * FETCHING_PLAYED_QUESTIONS} (résolution de l'historique des questions jouées).
     */
    record RoomPlayersLoadedEvent(String roomId, Instant loadedAt) implements RoomEvent {}

    /**
     * Les questions déjà jouées par les joueurs sont résolues : la préparation passe en {@code
     * FETCHING_QUESTIONS} (tirage filtré).
     */
    record RoomPlayedQuestionsLoadedEvent(
            String roomId, Set<String> playedQuestionIds, Instant loadedAt) implements RoomEvent {}

    /**
     * Les questions sont tirées : la préparation passe en {@code CREATING_GAME} ; la saga lance la
     * création de la partie (fire-and-forget) et attend l'accusé {@link RoomGameCreatedEvent}.
     */
    record RoomQuestionsLoadedEvent(String roomId, List<DrawnQuestion> questions, Instant loadedAt)
            implements RoomEvent {}

    /**
     * La partie a été créée par le service game (accusé de {@code CreateGameCommand}) : la salle
     * passe en {@code GAME_CREATED} et peut être clôturée.
     */
    record RoomGameCreatedEvent(String roomId, String gameId, Instant gameCreatedAt)
            implements RoomEvent {}

    /**
     * Le joueur n'est plus présent (il reste participant). {@code reason} est nul pour les
     * événements antérieurs aux canaux de présence. {@code roomPlayerIds} : joueurs humains du salon
     * (2026-11-16).
     */
    record RoomLeftEvent(
            String roomId,
            String playerId,
            Instant leftAt,
            String presenceId,
            RoomLeaveReason reason,
            Set<String> roomPlayerIds)
            implements RoomEvent {}

    /**
     * Relance de {@code targetId} par {@code playerId}. En invitation, l'invitation est renvoyée (nouvelle entrée et
     * push, prochaine relance à {@code remindableAt}) ; en présence, l'absent est appelé à rejoindre la salle
     * (« wizz »). Aucun effet sur la préparation.
     */
    record RoomRemindedEvent(
            String roomId,
            String playerId,
            String targetId,
            RoomReminderStage stage,
            String topicId,
            String sourceGameId,
            Instant expiresAt,
            Instant remindedAt,
            Instant remindableAt)
            implements RoomEvent {}

    record RoomCancelledEvent(
            String roomId,
            String initiatorId,
            RoomCancelReason reason,
            Set<String> roomPlayerIds,
            Instant cancelledAt)
            implements RoomEvent {}

    /** La partie a été créée depuis la salle (le {@code gameId} est le deep link de l'arène). */
    record RoomCompletedEvent(
            String roomId,
            String gameId,
            RoomOrigin origin,
            String initiatorId,
            Set<String> roomPlayerIds,
            Instant completedAt)
            implements RoomEvent {}

    /** La partie n'a pas pu être préparée : les joueurs présents sont informés de l'échec. */
    record RoomFailedEvent(String roomId, String reason, Instant failedAt) implements RoomEvent {}

    /** L'échéance de l'étape est passée : invitation sans réponse ou salle jamais lancée. */
    record RoomExpiredEvent(String roomId, Set<String> roomPlayerIds, Instant expiredAt) implements RoomEvent {}

    /** Purge après rétention : l'agrégat est supprimé et la projection supprime sa ligne. */
    record RoomPurgedEvent(String roomId, Instant purgedAt) implements RoomEvent {}

    record RoomTopicAvailabilityConfirmedEvent(String roomId, Instant confirmedAt)
            implements RoomEvent {}
}
