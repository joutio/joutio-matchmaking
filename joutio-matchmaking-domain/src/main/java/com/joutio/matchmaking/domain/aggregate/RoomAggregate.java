package com.joutio.matchmaking.domain.aggregate;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

import com.joutio.matchmaking.domain.command.RoomCommand;
import com.joutio.matchmaking.domain.event.RoomEvent;
import com.joutio.matchmaking.domain.exception.RoomExceptions;
import com.joutio.matchmaking.domain.model.RoomDeadline;
import com.joutio.matchmaking.domain.model.RoomOrigin;
import com.joutio.matchmaking.domain.model.RoomReminderRules;
import com.joutio.matchmaking.domain.model.RoomReminderStage;
import com.joutio.matchmaking.domain.model.RoomStatus;
import com.joutio.microservice.core.domain.constant.JoutioConstants;

import org.apache.commons.lang3.StringUtils;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * RoomAggregate — salle de duel : défi ou revanche, salon partagé par lien, appariement, ou entraînement contre le
 * bot.
 *
 * <p>Un défi est une étape de la salle : créée en {@code INVITED}, elle attend la réponse de l'invité nommé
 * (accepter, refuser) jusqu'à son échéance ; acceptée, elle attend les deux joueurs comme toute autre salle. Les
 * autres origines commencent directement en {@code WAITING_PLAYERS} ({@link RoomOrigin}).
 *
 * <p>Le créateur est l'initiateur ; le second humain devient participant lors de son
 * <b>apparition</b> dans la salle ({@code JoinRoomCommand}). La présence est observée par le
 * serveur, jamais déclarée par l'interface : le BFF émet l'entrée et la sortie selon l'abonnement
 * du joueur au canal de présence du salon, et chaque présence porte la session qui l'a ouverte
 * ({@code presenceId}) — une sortie ne s'applique qu'à sa propre session. Le bot d'une salle
 * {@code vsBot} est présent dès la création. Aucune saga ne simule une action utilisateur, et
 * aucun port externe n'est interrogé dans les handlers. Le statut suit l'avancement de la préparation ({@link RoomStatus}) ; une préparation
 * engagée ne peut plus être quittée, mais l'initiateur peut toujours annuler. Un état terminal est
 * purgé par la saga après rétention, puis {@code markDeleted}.
 */
@Aggregate
public class RoomAggregate {

    private static final Logger logger = LoggerFactory.getLogger(RoomAggregate.class);

    @AggregateIdentifier private String roomId;
    private boolean topicAvailabilityConfirmed;
    private String topicId;
    private RoomOrigin origin;
    private String sourceGameId;
    private String initiatorId;
    private String opponentId;
    private String participantId;
    private RoomStatus status;
    private boolean initiatorPresent;
    private boolean participantPresent;

    /** Session temps réel qui porte la présence de chaque joueur (nulle : ancien client, bot). */
    private String initiatorPresenceId;

    private String participantPresenceId;

    /** Échéance de l'étape en cours : une expiration plus précoce (deadline tardive) est ignorée. */
    private Instant expiresAt;

    /** Prochaine relance possible de l'invitation. */
    private Instant invitationRemindableAt;

    /** Dernier appel de chaque joueur présent vers l'autre, absent (cadence anti-spam). */
    private Instant initiatorRemindedAt;

    private Instant participantRemindedAt;

    protected RoomAggregate() {}

    /**
     * Ouvre une salle. L'invité doit correspondre à l'origine : un défi, une revanche ou un appariement nomment
     * l'autre joueur, un salon partagé n'en nomme pas. {@code vsBot} marque le second joueur comme bot : sa
     * présence est appliquée <b>dans la même unité de travail</b> que la création (aucune commande d'apparition
     * supplémentaire, le round-trip de la saga créait plusieurs minutes de lag sous charge).
     */
    @CommandHandler
    public RoomAggregate(RoomCommand.CreateRoomCommand command) {
        requirePlayerId(command.initiatorId(), command.roomId());
        requireTopicId(command.topicId(), command.roomId());
        requireConsistentInvitation(command);

        RoomStatus initialStatus =
                command.origin().requiresAcceptance() ? RoomStatus.INVITED : RoomStatus.WAITING_PLAYERS;
        logger.info(
                "Creating room: roomId={}, topicId={}, initiatorId={}, opponentId={}, origin={}, status={}",
                command.roomId(),
                command.topicId(),
                command.initiatorId(),
                command.opponentId(),
                command.origin(),
                initialStatus);
        Instant now = Instant.now();
        apply(
                new RoomEvent.RoomCreatedEvent(
                        command.roomId(),
                        command.topicId(),
                        command.initiatorId(),
                        command.opponentId(),
                        command.vsBot(),
                        command.botDifficulty(),
                        command.origin(),
                        command.sourceGameId(),
                        initialStatus,
                        RoomDeadline.expiryAtCreation(command.origin(), now),
                        now));
        if (command.vsBot()) {
            apply(
                    new RoomEvent.RoomEnteredEvent(
                            command.roomId(),
                            JoutioConstants.SYSTEM_USER_ID,
                            null,
                            now,
                            null,
                            humanPlayers(command.initiatorId(), command.opponentId(), null)));
        }
    }

    /** L'invité accepte le défi (idempotent) : la salle attend désormais les deux joueurs. */
    @CommandHandler
    public void handle(RoomCommand.AcceptRoomInvitationCommand command) {
        requireInvitee(command.playerId());
        if (status == RoomStatus.WAITING_PLAYERS) {
            return;
        }
        requireInvitation();
        Instant now = Instant.now();
        logger.info("Invitation accepted: roomId={}, opponentId={}", roomId, opponentId);
        apply(
                new RoomEvent.RoomAcceptedEvent(
                        roomId,
                        topicId,
                        initiatorId,
                        opponentId,
                        origin,
                        now.plus(RoomDeadline.ROOM_EXPIRY_DURATION),
                        now));
    }

    /** L'invité refuse le défi (idempotent) : la salle se termine. */
    @CommandHandler
    public void handle(RoomCommand.DeclineRoomInvitationCommand command) {
        requireInvitee(command.playerId());
        if (status == RoomStatus.DECLINED) {
            return;
        }
        requireInvitation();
        logger.info("Invitation declined: roomId={}, opponentId={}", roomId, opponentId);
        apply(new RoomEvent.RoomDeclinedEvent(roomId, topicId, initiatorId, opponentId, origin, Instant.now()));
    }

    /**
     * Apparition d'un joueur dans la salle : présence temps réel et, pour le second humain,
     * enregistrement implicite comme participant. Idempotent ; quand les deux sont présents, la
     * salle passe en préparation ({@code FETCHING_PLAYERS}). Un joueur déjà présent qui revient
     * par une autre session (reconnexion, autre onglet) en reprend la présence tant que la salle
     * attend : la sortie de l'ancienne session ne le fera plus sortir.
     */
    @CommandHandler
    public void handle(RoomCommand.JoinRoomCommand command) {
        requirePlayerId(command.playerId(), roomId);
        if (isClosed()) {
            return;
        }
        if (status == RoomStatus.INVITED) {
            throw new RoomExceptions.RoomInvitationPendingProblem(roomId);
        }

        boolean known =
                command.playerId().equals(initiatorId) || command.playerId().equals(participantId);
        boolean alreadyPresent =
                command.playerId().equals(initiatorId) ? initiatorPresent : participantPresent;
        if (known && alreadyPresent) {
            if (takesOverPresence(command.playerId(), command.presenceId())) {
                apply(
                        new RoomEvent.RoomEnteredEvent(
                                roomId,
                                command.playerId(),
                                command.player(),
                                Instant.now(),
                                command.presenceId(),
                                humanPlayers(initiatorId, opponentId, participantId)));
            }
            return;
        }
        if (!known && StringUtils.isNotBlank(participantId)) {
            throw new RoomExceptions.RoomAlreadyFullProblem(roomId);
        }
        if (!known
                && StringUtils.isNotBlank(opponentId)
                && !command.playerId().equals(opponentId)) {
            throw new RoomExceptions.RoomNotInvitedProblem(roomId, command.playerId());
        }

        Instant now = Instant.now();
        logger.info("Player entering room: roomId={}, playerId={}", roomId, command.playerId());
        apply(
                new RoomEvent.RoomEnteredEvent(
                        roomId,
                        command.playerId(),
                        command.player(),
                        now,
                        command.presenceId(),
                        humanPlayers(initiatorId, opponentId, participantId, command.playerId())));
        if (bothPresent()) {
            // La préparation démarre immédiatement ; la partie sera créée dès les questions
            // tirées (plus de compte à rebours de lancement).
            apply(new RoomEvent.RoomAllPlayersPresentEvent(roomId, now));
        }
    }

    /**
     * Sortie <b>non destructive</b> avant l'engagement : le joueur n'est plus présent mais le
     * participant reste enregistré (retour possible). Constatée par le serveur, elle ne s'applique
     * qu'à la présence ouverte par la même session et reste sans effet une fois la préparation
     * lancée (le duel est engagé). Une sortie sans session vient du constat hors ligne
     * ({@code OFFLINE}). La salle ne se ferme que par {@code CancelRoomCommand} (initiateur) ou
     * expiration.
     */
    @CommandHandler
    public void handle(RoomCommand.LeaveRoomCommand command) {
        if (isClosed()) {
            return;
        }
        requireParticipant(command.playerId());
        if (status != RoomStatus.WAITING_PLAYERS) {
            return;
        }
        boolean observed = command.presenceId() != null;
        if (!isPresent(command.playerId())) {
            return;
        }
        if (observed && !command.presenceId().equals(presenceIdOf(command.playerId()))) {
            logger.debug(
                    "Stale room leave ignored: roomId={}, playerId={}, presenceId={}",
                    roomId,
                    command.playerId(),
                    command.presenceId());
            return;
        }
        logger.info(
                "Player leaving room: roomId={}, playerId={}, reason={}",
                roomId,
                command.playerId(),
                command.reason());
        apply(
                new RoomEvent.RoomLeftEvent(
                        roomId,
                        command.playerId(),
                        Instant.now(),
                        command.presenceId(),
                        command.reason(),
                        humanPlayers(initiatorId, opponentId, participantId)));
    }

    /**
     * Relance selon l'étape ({@link RoomReminderRules}) :
     * <ul>
     *   <li>invitation : le lanceur renvoie son défi à l'invité, au plus toutes les 10 min ;</li>
     *   <li>attente des joueurs : un joueur présent appelle l'autre humain, absent, au plus toutes les 10 s.</li>
     * </ul>
     * Une préparation engagée ou une salle terminale l'ignore (le second joueur vient d'arriver).
     */
    @CommandHandler
    public void handle(RoomCommand.RemindRoomCommand command) {
        Instant now = Instant.now();
        if (status == RoomStatus.INVITED) {
            remindInvitation(command.playerId(), now);
        } else if (status == RoomStatus.WAITING_PLAYERS) {
            remindAbsentPlayer(command.playerId(), now);
        }
    }

    private void remindInvitation(String playerId, Instant now) {
        if (!initiatorId.equals(playerId)) {
            throw new RoomExceptions.RoomReminderNotAllowedProblem(roomId, playerId);
        }
        if (now.isBefore(invitationRemindableAt)) {
            throw new RoomExceptions.RoomReminderCooldownProblem(roomId, invitationRemindableAt);
        }
        logger.info("Invitation reminded: roomId={}, initiatorId={}", roomId, initiatorId);
        apply(reminded(playerId, opponentId, RoomReminderStage.INVITATION, now));
    }

    private void remindAbsentPlayer(String playerId, Instant now) {
        requireParticipant(playerId);
        if (!isPresent(playerId)) {
            throw new RoomExceptions.RoomReminderNotAllowedProblem(roomId, playerId);
        }
        String targetId = absentOpponent(playerId);
        if (targetId == null) {
            throw new RoomExceptions.RoomReminderUnavailableProblem(roomId);
        }
        Instant lastRemindedAt = playerId.equals(initiatorId) ? initiatorRemindedAt : participantRemindedAt;
        if (lastRemindedAt != null) {
            Instant remindableAt = RoomReminderRules.nextReminderAt(RoomReminderStage.PRESENCE, lastRemindedAt);
            if (now.isBefore(remindableAt)) {
                throw new RoomExceptions.RoomReminderCooldownProblem(roomId, remindableAt);
            }
        }
        logger.info("Absent player reminded: roomId={}, playerId={}, targetId={}", roomId, playerId, targetId);
        apply(reminded(playerId, targetId, RoomReminderStage.PRESENCE, now));
    }

    private RoomEvent.RoomRemindedEvent reminded(
            String playerId, String targetId, RoomReminderStage stage, Instant now) {
        return new RoomEvent.RoomRemindedEvent(
                roomId,
                playerId,
                targetId,
                stage,
                topicId,
                sourceGameId,
                expiresAt,
                now,
                RoomReminderRules.nextReminderAt(stage, now));
    }

    /** L'initiateur annule la salle, y compris pendant l'invitation ou la préparation. */
    @CommandHandler
    public void handle(RoomCommand.CancelRoomCommand command) {
        if (isClosed()) {
            return;
        }
        if (!command.playerId().equals(initiatorId)) {
            throw new RoomExceptions.PlayerNotInRoomProblem(roomId, command.playerId());
        }
        apply(
                new RoomEvent.RoomCancelledEvent(
                        roomId, initiatorId, command.reason(), roomPlayers(), Instant.now()));
    }

    /** Commande interne (saga) : les profils sont résolus ; avance la préparation (monotone). */
    @CommandHandler
    public void handle(RoomCommand.MarkRoomPlayersLoadedCommand command) {
        if (status != RoomStatus.FETCHING_PLAYERS) {
            return;
        }
        logger.info("Players loaded in room: roomId={}", roomId);
        apply(new RoomEvent.RoomPlayersLoadedEvent(roomId, Instant.now()));
    }

    /** Commande interne (saga) : l'historique des questions jouées est résolu (joutio-game). */
    @CommandHandler
    public void handle(RoomCommand.MarkRoomPlayedQuestionsLoadedCommand command) {
        if (status != RoomStatus.FETCHING_PLAYED_QUESTIONS) {
            return;
        }
        logger.info("Played questions loaded in room: roomId={}", roomId);
        apply(
                new RoomEvent.RoomPlayedQuestionsLoadedEvent(
                        roomId, command.playedQuestionIds(), Instant.now()));
    }

    /**
     * Commande interne (saga) : les questions sont tirées ; la saga lance la création de la partie.
     */
    @CommandHandler
    public void handle(RoomCommand.MarkRoomQuestionsLoadedCommand command) {
        if (status != RoomStatus.FETCHING_QUESTIONS) {
            return;
        }
        logger.info("Questions loaded in room: roomId={}, création de la partie", roomId);
        apply(new RoomEvent.RoomQuestionsLoadedEvent(roomId, command.questions(), Instant.now()));
    }

    /** Commande interne (saga) : le service game a accusé la création de la partie. */
    @CommandHandler
    public void handle(RoomCommand.MarkRoomGameCreatedCommand command) {
        if (status != RoomStatus.CREATING_GAME) {
            return;
        }
        requireGameId(command.gameId(), roomId);
        logger.info("Game created in room: roomId={}, gameId={}", roomId, command.gameId());
        apply(new RoomEvent.RoomGameCreatedEvent(roomId, command.gameId(), Instant.now()));
    }

    /** Succès : la partie a été créée et accusée ; la salle reste lisible jusqu'à sa purge. */
    @CommandHandler
    public void handle(RoomCommand.CompleteRoomCommand command) {
        requireGameId(command.gameId(), roomId);
        // Clôture uniquement après l'accusé de création (GAME_CREATED) ; idempotent si déjà close.
        if (status != RoomStatus.GAME_CREATED) {
            return;
        }
        // La partie n'est créée qu'une fois les deux joueurs réellement présents en salle.
        if (!bothPresent()) {
            throw new RoomExceptions.ParticipantNotPresentProblem(roomId);
        }
        logger.info("Completing room: roomId={}, gameId={}", roomId, command.gameId());
        apply(
                new RoomEvent.RoomCompletedEvent(
                        roomId, command.gameId(), origin, initiatorId, roomPlayers(), Instant.now()));
    }

    /**
     * Échec système : la partie n'a pas pu être préparée (questions insuffisantes, création KO).
     */
    @CommandHandler
    public void handle(RoomCommand.FailRoomCommand command) {
        if (isClosed()) {
            return;
        }
        logger.warn("Failing room: roomId={}, reason={}", roomId, command.reason());
        apply(new RoomEvent.RoomFailedEvent(roomId, command.reason(), Instant.now()));
    }

    /** Échéance de l'étape en cours ; une deadline antérieure à l'échéance (étape changée depuis) est ignorée. */
    @CommandHandler
    public void handle(RoomCommand.ExpireRoomCommand command) {
        Instant now = Instant.now();
        if (isClosed() || now.isBefore(expiresAt)) {
            return;
        }
        apply(new RoomEvent.RoomExpiredEvent(roomId, roomPlayers(), now));
    }

    @CommandHandler
    public void handle(RoomCommand.PurgeRoomCommand command) {
        logger.info("Purging room: roomId={}", roomId);
        apply(new RoomEvent.RoomPurgedEvent(roomId, Instant.now()));
    }

    // ---------------------------------------------------------------------

    // ── Validateurs (requireXxx : nommés, appelés en tête de handler) ──

    private static void requirePlayerId(String playerId, String roomId) {
        if (StringUtils.isBlank(playerId)) {
            throw new RoomExceptions.MissingPlayerIdentifierProblem(roomId);
        }
    }

    private static void requireTopicId(String topicId, String roomId) {
        if (StringUtils.isBlank(topicId)) {
            throw new RoomExceptions.MissingTopicIdentifierProblem(roomId);
        }
    }

    private static void requireGameId(String gameId, String roomId) {
        if (StringUtils.isBlank(gameId)) {
            throw new RoomExceptions.MissingGameIdentifierProblem(roomId);
        }
    }

    private static void requireConsistentInvitation(RoomCommand.CreateRoomCommand command) {
        boolean named = StringUtils.isNotBlank(command.opponentId());
        RoomOrigin origin = command.origin();
        boolean consistent =
                origin != null && (origin.isNominative() ? named : origin != RoomOrigin.SHARED || !named);
        if (!consistent) {
            throw new RoomExceptions.RoomInvalidInvitationProblem(command.roomId(), String.valueOf(origin));
        }
        if (named && command.opponentId().equals(command.initiatorId())) {
            throw new RoomExceptions.RoomSelfInvitationProblem(command.roomId());
        }
    }

    private void requireInvitee(String playerId) {
        if (StringUtils.isBlank(playerId) || !origin.requiresAcceptance() || !playerId.equals(opponentId)) {
            throw new RoomExceptions.RoomNotInvitedProblem(roomId, String.valueOf(playerId));
        }
    }

    private void requireInvitation() {
        if (status != RoomStatus.INVITED) {
            throw new RoomExceptions.RoomNotInvitationProblem(roomId, status.name());
        }
    }

    private void requireParticipant(String playerId) {
        if (StringUtils.isBlank(playerId)
                || (!playerId.equals(initiatorId) && !playerId.equals(participantId))) {
            throw new RoomExceptions.PlayerNotInRoomProblem(roomId, playerId);
        }
    }

    /** Présence temps réel d'un participant connu (initiateur ou second enregistré). */
    private boolean isPresent(String playerId) {
        return playerId.equals(initiatorId) ? initiatorPresent : participantPresent;
    }

    /**
     * L'autre humain de la salle, s'il est <b>absent</b> (invité pas encore apparu, participant sorti) ; {@code null}
     * quand il n'y a personne à appeler (salon partagé sans participant, adversaire présent, bot).
     */
    private String absentOpponent(String playerId) {
        boolean fromInitiator = playerId.equals(initiatorId);
        String targetId =
                fromInitiator ? (participantId != null ? participantId : opponentId) : initiatorId;
        if (StringUtils.isBlank(targetId) || JoutioConstants.SYSTEM_USER_ID.equals(targetId)) {
            return null;
        }
        boolean targetPresent = fromInitiator ? participantPresent : initiatorPresent;
        return targetPresent ? null : targetId;
    }

    private String presenceIdOf(String playerId) {
        return playerId.equals(initiatorId) ? initiatorPresenceId : participantPresenceId;
    }

    /** Une nouvelle session reprend la présence d'un joueur déjà présent, tant que la salle attend. */
    private boolean takesOverPresence(String playerId, String presenceId) {
        return presenceId != null
                && status == RoomStatus.WAITING_PLAYERS
                && !presenceId.equals(presenceIdOf(playerId));
    }

    private boolean bothPresent() {
        return initiatorPresent && participantPresent && StringUtils.isNotBlank(participantId);
    }

    private boolean isClosed() {
        return status.isTerminal();
    }

    private Set<String> roomPlayers() {
        return humanPlayers(initiatorId, opponentId, participantId);
    }

    // ============================ Event Sourcing ============================

    @EventSourcingHandler
    public void on(RoomEvent.RoomCreatedEvent event) {
        this.roomId = event.roomId();
        this.topicId = event.topicId();
        this.origin = event.origin();
        this.sourceGameId = event.sourceGameId();
        this.initiatorId = event.initiatorId();
        this.opponentId = event.opponentId();
        this.status = event.status();
        this.expiresAt = event.expiresAt();
        this.invitationRemindableAt =
                RoomReminderRules.nextReminderAt(RoomReminderStage.INVITATION, event.createdAt());
    }

    @EventSourcingHandler
    public void on(RoomEvent.RoomAcceptedEvent event) {
        this.status = RoomStatus.WAITING_PLAYERS;
        this.expiresAt = event.expiresAt();
    }

    @EventSourcingHandler
    public void on(RoomEvent.RoomDeclinedEvent event) {
        this.status = RoomStatus.DECLINED;
    }

    /**
     * Apparition : marque la présence ; le second humain (non initiateur) devient participant au
     * premier passage.
     */
    @EventSourcingHandler
    public void on(RoomEvent.RoomEnteredEvent event) {
        if (event.playerId().equals(initiatorId)) {
            this.initiatorPresent = true;
            this.initiatorPresenceId = event.presenceId();
            return;
        }
        if (StringUtils.isBlank(participantId)) {
            this.participantId = event.playerId();
        }
        if (event.playerId().equals(participantId)) {
            this.participantPresent = true;
            this.participantPresenceId = event.presenceId();
        }
    }

    @EventSourcingHandler
    public void on(RoomEvent.RoomAllPlayersPresentEvent event) {
        this.status = RoomStatus.FETCHING_PLAYERS;
    }

    @EventSourcingHandler
    public void on(RoomEvent.RoomPlayersLoadedEvent event) {
        this.status = RoomStatus.FETCHING_PLAYED_QUESTIONS;
    }

    @EventSourcingHandler
    public void on(RoomEvent.RoomPlayedQuestionsLoadedEvent event) {
        this.status = RoomStatus.FETCHING_QUESTIONS;
    }

    @EventSourcingHandler
    public void on(RoomEvent.RoomQuestionsLoadedEvent event) {
        this.status = RoomStatus.CREATING_GAME;
    }

    @EventSourcingHandler
    public void on(RoomEvent.RoomGameCreatedEvent event) {
        this.status = RoomStatus.GAME_CREATED;
    }

    @EventSourcingHandler
    public void on(RoomEvent.RoomLeftEvent event) {
        // Le participant reste enregistré : il peut revenir (ré-apparition dans la salle).
        if (event.playerId().equals(initiatorId)) {
            this.initiatorPresent = false;
            this.initiatorPresenceId = null;
        } else if (event.playerId().equals(participantId)) {
            this.participantPresent = false;
            this.participantPresenceId = null;
        }
    }

    @EventSourcingHandler
    public void on(RoomEvent.RoomRemindedEvent event) {
        if (event.stage() == RoomReminderStage.INVITATION) {
            this.invitationRemindableAt = event.remindableAt();
        } else if (event.playerId().equals(initiatorId)) {
            this.initiatorRemindedAt = event.remindedAt();
        } else {
            this.participantRemindedAt = event.remindedAt();
        }
    }

    @EventSourcingHandler
    public void on(RoomEvent.RoomCancelledEvent event) {
        this.status = RoomStatus.CANCELLED;
    }

    @EventSourcingHandler
    public void on(RoomEvent.RoomCompletedEvent event) {
        this.status = RoomStatus.COMPLETED;
    }

    @EventSourcingHandler
    public void on(RoomEvent.RoomFailedEvent event) {
        this.status = RoomStatus.FAILED;
    }

    @EventSourcingHandler
    public void on(RoomEvent.RoomExpiredEvent event) {
        this.status = RoomStatus.EXPIRED;
    }

    @EventSourcingHandler
    public void on(RoomEvent.RoomPurgedEvent event) {
        AggregateLifecycle.markDeleted();
    }

    @CommandHandler
    public void handle(RoomCommand.ConfirmRoomTopicAvailabilityCommand command) {
        if (status != RoomStatus.CREATING_GAME || topicAvailabilityConfirmed) return;
        apply(new RoomEvent.RoomTopicAvailabilityConfirmedEvent(roomId, Instant.now()));
    }

    @EventSourcingHandler
    public void on(RoomEvent.RoomTopicAvailabilityConfirmedEvent event) {
        topicAvailabilityConfirmed = true;
    }

    /** Joueurs humains du salon (sans le bot), dans l'ordre, pour les événements de présence. */
    private static Set<String> humanPlayers(String... ids) {
        Set<String> players = new LinkedHashSet<>();
        for (String id : ids) {
            if (StringUtils.isNotBlank(id) && !JoutioConstants.SYSTEM_USER_ID.equals(id)) {
                players.add(id);
            }
        }
        return Set.copyOf(players);
    }
}
