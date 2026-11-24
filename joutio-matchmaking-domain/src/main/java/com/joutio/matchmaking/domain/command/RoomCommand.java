package com.joutio.matchmaking.domain.command;

import com.joutio.matchmaking.domain.model.DrawnQuestion;
import com.joutio.matchmaking.domain.model.RoomCancelReason;
import com.joutio.matchmaking.domain.model.RoomLeaveReason;
import com.joutio.matchmaking.domain.model.RoomOrigin;
import com.joutio.matchmaking.domain.model.RoomPlayer;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.util.List;
import java.util.Set;

/**
 * Commandes de la salle : défi ou revanche (invité nommé qui accepte), salon partagé par lien, appariement, ou
 * entraînement contre le bot. L'identifiant de l'agrégat sert de référence de partage ({@code /j/{roomId}}).
 */
public interface RoomCommand {

    String roomId();

    /**
     * Ouvre une salle ; le créateur en est l'initiateur. L'origine décide du second joueur
     * ({@link RoomOrigin}) : défi et revanche nomment un invité qui doit accepter, l'appariement nomme les deux
     * joueurs, un salon partagé n'a pas d'invité nommé. {@code vsBot} marque une salle dont le second joueur est le
     * bot ; {@code botDifficulty} est le nom de l'enum game (nullable, défaut NORMAL), en {@code String} pour que
     * le domaine matchmaking ne dépende pas du domaine game. {@code sourceGameId} : partie terminée d'origine d'une
     * revanche.
     */
    record CreateRoomCommand(
            @TargetAggregateIdentifier String roomId,
            String topicId,
            String initiatorId,
            String opponentId,
            boolean vsBot,
            String botDifficulty,
            RoomOrigin origin,
            String sourceGameId)
            implements RoomCommand {}

    /** L'invité accepte le défi : la salle s'ouvre aux deux joueurs. */
    record AcceptRoomInvitationCommand(@TargetAggregateIdentifier String roomId, String playerId)
            implements RoomCommand {}

    /** L'invité refuse le défi : la salle se termine. */
    record DeclineRoomInvitationCommand(@TargetAggregateIdentifier String roomId, String playerId)
            implements RoomCommand {}

    /**
     * Le joueur <b>apparaît</b> dans la salle : présence temps réel et, pour le second humain,
     * enregistrement implicite comme participant. Émise par le BFF quand il observe l'abonnement
     * du joueur au canal de présence du salon ; aucune saga ne le fait à sa place (seule
     * exception : le bot d'une salle {@code vsBot}). {@code player} est le snapshot préparé par la
     * façade (profil + progression) : la salle ne fait aucune I/O pour résoudre les joueurs.
     * {@code presenceId} identifie la session temps réel qui porte la présence (nullable : ancien
     * client par {@code POST /join}).
     */
    record JoinRoomCommand(
            @TargetAggregateIdentifier String roomId,
            String playerId,
            RoomPlayer player,
            String presenceId)
            implements RoomCommand {}

    /**
     * Le joueur n'est plus présent : sortie <b>non destructive</b> (il reste participant et peut
     * revenir jusqu'à l'expiration). Constatée par le serveur ({@code reason}) ; ne s'applique
     * qu'à la présence ouverte par la même session ({@code presenceId}), si bien qu'une sortie
     * tardive d'une ancienne session est sans effet. Sans effet aussi une fois la préparation
     * engagée. {@code presenceId} nul : sortie inconditionnelle d'un ancien client
     * ({@code POST /leave}), refusée pendant la préparation comme avant.
     */
    record LeaveRoomCommand(
            @TargetAggregateIdentifier String roomId,
            String playerId,
            String presenceId,
            RoomLeaveReason reason)
            implements RoomCommand {}

    /**
     * Relance, selon l'étape de la salle ({@code RoomReminderRules}) : en invitation, le lanceur renvoie son défi ;
     * en attente des joueurs, un joueur présent appelle l'autre, absent. Une préparation engagée l'ignore.
     */
    record RemindRoomCommand(@TargetAggregateIdentifier String roomId, String playerId)
            implements RoomCommand {}

    /**
     * Annule la salle au nom de son initiateur (possible tant qu'elle n'est pas terminale) : bouton, ou blocage
     * entre les deux joueurs.
     */
    record CancelRoomCommand(@TargetAggregateIdentifier String roomId, String playerId, RoomCancelReason reason)
            implements RoomCommand {}

    /** Commande interne (saga) : les profils des joueurs sont résolus. */
    record MarkRoomPlayersLoadedCommand(@TargetAggregateIdentifier String roomId)
            implements RoomCommand {}

    /** Commande interne (saga) : les questions déjà jouées par les joueurs sont résolues. */
    record MarkRoomPlayedQuestionsLoadedCommand(
            @TargetAggregateIdentifier String roomId, Set<String> playedQuestionIds)
            implements RoomCommand {}

    /**
     * Commande interne (saga) : les questions sont tirées, la création de la partie peut démarrer.
     */
    record MarkRoomQuestionsLoadedCommand(
            @TargetAggregateIdentifier String roomId, List<DrawnQuestion> questions)
            implements RoomCommand {}

    /**
     * Commande interne (saga) : le service game a accusé la création de la partie ({@code
     * GameCreatedEvent}). La salle peut être clôturée par {@link CompleteRoomCommand}.
     */
    record MarkRoomGameCreatedCommand(@TargetAggregateIdentifier String roomId, String gameId)
            implements RoomCommand {}

    /**
     * Commande interne (saga) : la partie a été créée ; la salle reste lisible jusqu'à sa purge.
     */
    record CompleteRoomCommand(@TargetAggregateIdentifier String roomId, String gameId)
            implements RoomCommand {}

    /**
     * Commande interne (saga) : la partie n'a pas pu être préparée (questions insuffisantes,
     * création impossible). Le joueur présent est informé par {@code RoomFailedEvent}.
     */
    record FailRoomCommand(@TargetAggregateIdentifier String roomId, String reason)
            implements RoomCommand {}

    /** Commande interne (saga, balayeur) : l'échéance de l'étape en cours est passée. */
    record ExpireRoomCommand(@TargetAggregateIdentifier String roomId) implements RoomCommand {}

    /** Commande interne (saga, après rétention) : purge un état terminal. */
    record PurgeRoomCommand(@TargetAggregateIdentifier String roomId) implements RoomCommand {}

    record ConfirmRoomTopicAvailabilityCommand(@TargetAggregateIdentifier String roomId)
            implements RoomCommand {}
}
