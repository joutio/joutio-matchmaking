package com.joutio.matchmaking.domain.aggregate;

import com.joutio.matchmaking.domain.model.RoomOrigin;

import com.joutio.axon.test.JoutioAxonMatchers;
import com.joutio.matchmaking.domain.command.RoomCommand;
import com.joutio.matchmaking.domain.event.RoomEvent;
import com.joutio.matchmaking.domain.exception.RoomExceptions;
import com.joutio.matchmaking.domain.model.RoomCancelReason;
import com.joutio.matchmaking.domain.model.RoomLeaveReason;
import com.joutio.matchmaking.domain.model.RoomPlayer;
import com.joutio.matchmaking.domain.model.RoomReminderStage;
import com.joutio.matchmaking.domain.model.RoomStatus;
import com.joutio.microservice.core.domain.constant.JoutioConstants;
import com.joutio.microservice.core.domain.model.i18n.Language;

import org.axonframework.test.aggregate.AggregateTestFixture;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Test Axon in-memory de {@link RoomAggregate} (salle : deux humains ou humain/bot).
 *
 * <p>Le BFF émet l'apparition ({@code JoinRoomCommand}, profil préparé par la façade) quand il
 * observe l'abonnement au canal de présence, avec la session qui la porte : présence et enregistrement du second participant ; le bot, lui, est présent dès la
 * création de la salle (appliqué par l'agrégat, aucun join système). Après l'apparition des deux
 * joueurs, la préparation avance par commandes de phase (les résultats préparent l'événement
 * suivant : historique et questions) et la salle ne peut plus être quittée (l'annulation reste
 * possible). La clôture exige l'accusé de création de la partie ({@code GAME_CREATED}).
 */
class RoomAggregateTest {

    private static final String ROOM_ID = "room-1";
    private static final String TOPIC = "topic-1";
    private static final String INITIATOR = "initiator-1";
    private static final String OPPONENT = "opponent-1";
    private static final String PARTICIPANT = "player-2";
    private static final String OTHER = "intruder";

    private static final RoomPlayer INITIATOR_PLAYER =
            new RoomPlayer(INITIATOR, "Alpha", Language.FR, 0, 200);
    private static final RoomPlayer OPPONENT_PLAYER =
            new RoomPlayer(OPPONENT, "Bravo", Language.EN, 0, 200);
    private static final RoomPlayer PARTICIPANT_PLAYER =
            new RoomPlayer(PARTICIPANT, "Charlie", Language.FR, 0, 200);
    private static final RoomPlayer OTHER_PLAYER =
            new RoomPlayer(OTHER, "Intruder", Language.FR, 0, 200);

    private final AggregateTestFixture<RoomAggregate> fixture =
            new AggregateTestFixture<>(RoomAggregate.class);

    @Test
    void createRoom_appliesCreatedEvent() {
        fixture.givenNoPriorActivity()
                .when(
                        new RoomCommand.CreateRoomCommand(
                                ROOM_ID, TOPIC, INITIATOR, null, false, null, RoomOrigin.SHARED, null))
                .expectEventsMatching(
                        JoutioAxonMatchers.singlePayloadMatching(
                                RoomEvent.RoomCreatedEvent.class,
                                e ->
                                        ((RoomEvent.RoomCreatedEvent) e).roomId().equals(ROOM_ID)
                                                && ((RoomEvent.RoomCreatedEvent) e)
                                                        .initiatorId()
                                                        .equals(INITIATOR)
                                                && ((RoomEvent.RoomCreatedEvent) e).opponentId()
                                                        == null
                                                && !((RoomEvent.RoomCreatedEvent) e).vsBot()));
    }

    @Test
    void createChallenge_startsInvited() {
        fixture.givenNoPriorActivity()
                .when(
                        new RoomCommand.CreateRoomCommand(
                                ROOM_ID, TOPIC, INITIATOR, OPPONENT, false, null, RoomOrigin.CHALLENGE, null))
                .expectEventsMatching(
                        JoutioAxonMatchers.singlePayloadMatching(
                                RoomEvent.RoomCreatedEvent.class,
                                e ->
                                        OPPONENT.equals(((RoomEvent.RoomCreatedEvent) e).opponentId())
                                                && ((RoomEvent.RoomCreatedEvent) e).status()
                                                        == RoomStatus.INVITED));
    }

    @Test
    void createBotRoom_carriesDifficultyAndBotFlag_andAppliesBotPresence() {
        fixture.givenNoPriorActivity()
                .when(
                        new RoomCommand.CreateRoomCommand(
                                ROOM_ID,
                                TOPIC,
                                INITIATOR,
                                JoutioConstants.SYSTEM_USER_ID,
                                true,
                                "HARD",
                                RoomOrigin.BOT, null))
                .expectEventsMatching(
                        JoutioAxonMatchers.hasPayloadMatching(
                                RoomEvent.RoomCreatedEvent.class,
                                e ->
                                        ((RoomEvent.RoomCreatedEvent) e).vsBot()
                                                && "HARD"
                                                        .equals(
                                                                ((RoomEvent.RoomCreatedEvent) e)
                                                                        .botDifficulty())))
                .expectEventsMatching(
                        JoutioAxonMatchers.hasPayloadMatching(
                                RoomEvent.RoomEnteredEvent.class,
                                e ->
                                        JoutioConstants.SYSTEM_USER_ID.equals(
                                                ((RoomEvent.RoomEnteredEvent) e).playerId())));
    }

    @Test
    void initiatorJoin_marksPresenceOnly_andCarriesPreparedProfile() {
        fixture.given(created())
                .when(new RoomCommand.JoinRoomCommand(ROOM_ID, INITIATOR, INITIATOR_PLAYER, null))
                .expectEventsMatching(
                        JoutioAxonMatchers.singlePayloadMatching(
                                RoomEvent.RoomEnteredEvent.class,
                                e ->
                                        INITIATOR.equals(
                                                        ((RoomEvent.RoomEnteredEvent) e).playerId())
                                                && INITIATOR_PLAYER.equals(
                                                        ((RoomEvent.RoomEnteredEvent) e)
                                                                .player())));
    }

    @Test
    void secondPlayerJoin_registersParticipantAndStartsReadyCheck() {
        fixture.given(created(), entered(INITIATOR_PLAYER))
                .when(new RoomCommand.JoinRoomCommand(ROOM_ID, PARTICIPANT, PARTICIPANT_PLAYER, null))
                .expectEventsMatching(
                        JoutioAxonMatchers.hasPayloadMatching(
                                RoomEvent.RoomEnteredEvent.class,
                                e ->
                                        PARTICIPANT.equals(
                                                ((RoomEvent.RoomEnteredEvent) e).playerId())))
                .expectEventsMatching(
                        JoutioAxonMatchers.hasPayloadMatching(
                                RoomEvent.RoomAllPlayersPresentEvent.class, e -> true));
    }

    @Test
    void joinTwice_isIdempotent() {
        fixture.given(created(), entered(INITIATOR_PLAYER))
                .when(new RoomCommand.JoinRoomCommand(ROOM_ID, INITIATOR, INITIATOR_PLAYER, null))
                .expectNoEvents();
    }

    @Test
    void nominalRoom_refusesNonInvitedPlayer() {
        fixture.given(createdWithOpponent())
                .when(new RoomCommand.JoinRoomCommand(ROOM_ID, OTHER, OTHER_PLAYER, null))
                .expectException(RoomExceptions.RoomNotInvitedProblem.class);
    }

    @Test
    void nominalRoom_letsInvitedPlayerJoin() {
        fixture.given(createdWithOpponent())
                .when(new RoomCommand.JoinRoomCommand(ROOM_ID, OPPONENT, OPPONENT_PLAYER, null))
                .expectEventsMatching(
                        JoutioAxonMatchers.hasPayloadMatching(
                                RoomEvent.RoomEnteredEvent.class,
                                e -> OPPONENT.equals(((RoomEvent.RoomEnteredEvent) e).playerId())));
    }

    @Test
    void botRoom_humanJoinCompletesPresence_andStartsPreparation() {
        fixture.given(createdBot(), enteredBot())
                .when(new RoomCommand.JoinRoomCommand(ROOM_ID, INITIATOR, INITIATOR_PLAYER, null))
                .expectEventsMatching(
                        JoutioAxonMatchers.hasPayloadMatching(
                                RoomEvent.RoomEnteredEvent.class,
                                e -> INITIATOR.equals(((RoomEvent.RoomEnteredEvent) e).playerId())))
                .expectEventsMatching(
                        JoutioAxonMatchers.hasPayloadMatching(
                                RoomEvent.RoomAllPlayersPresentEvent.class, e -> true));
    }

    @Test
    void sharedRoom_refusesThirdPlayer() {
        fixture.given(created(), entered(INITIATOR_PLAYER), entered(PARTICIPANT_PLAYER))
                .when(new RoomCommand.JoinRoomCommand(ROOM_ID, OTHER, OTHER_PLAYER, null))
                .expectException(RoomExceptions.RoomAlreadyFullProblem.class);
    }

    @Test
    void playersLoaded_advancesPreparation() {
        fixture.given(
                        created(),
                        entered(INITIATOR_PLAYER),
                        entered(PARTICIPANT_PLAYER),
                        allPresent())
                .when(new RoomCommand.MarkRoomPlayersLoadedCommand(ROOM_ID))
                .expectEventsMatching(
                        JoutioAxonMatchers.singlePayloadMatching(
                                RoomEvent.RoomPlayersLoadedEvent.class,
                                e ->
                                        ROOM_ID.equals(
                                                ((RoomEvent.RoomPlayersLoadedEvent) e).roomId())));
    }

    @Test
    void playersLoaded_ignoredWhenPlayersNotPresent() {
        fixture.given(created())
                .when(new RoomCommand.MarkRoomPlayersLoadedCommand(ROOM_ID))
                .expectNoEvents();
    }

    @Test
    void playedQuestions_advancesPreparation() {
        fixture.given(
                        created(),
                        entered(INITIATOR_PLAYER),
                        entered(PARTICIPANT_PLAYER),
                        allPresent(),
                        playersLoaded())
                .when(new RoomCommand.MarkRoomPlayedQuestionsLoadedCommand(ROOM_ID, Set.of("q-1")))
                .expectEventsMatching(
                        JoutioAxonMatchers.singlePayloadMatching(
                                RoomEvent.RoomPlayedQuestionsLoadedEvent.class,
                                e ->
                                        ROOM_ID.equals(
                                                        ((RoomEvent.RoomPlayedQuestionsLoadedEvent)
                                                                        e)
                                                                .roomId())
                                                && Set.of("q-1")
                                                        .equals(
                                                                ((RoomEvent
                                                                                        .RoomPlayedQuestionsLoadedEvent)
                                                                                e)
                                                                        .playedQuestionIds())));
    }

    @Test
    void playedQuestions_ignoredBeforePlayersLoaded() {
        fixture.given(
                        created(),
                        entered(INITIATOR_PLAYER),
                        entered(PARTICIPANT_PLAYER),
                        allPresent())
                .when(new RoomCommand.MarkRoomPlayedQuestionsLoadedCommand(ROOM_ID, Set.of("q-1")))
                .expectNoEvents();
    }

    @Test
    void questionsLoaded_advancesPreparation() {
        fixture.given(
                        created(),
                        entered(INITIATOR_PLAYER),
                        entered(PARTICIPANT_PLAYER),
                        allPresent(),
                        playersLoaded(),
                        playedQuestionsLoaded())
                .when(new RoomCommand.MarkRoomQuestionsLoadedCommand(ROOM_ID, List.of()))
                .expectEventsMatching(
                        JoutioAxonMatchers.singlePayloadMatching(
                                RoomEvent.RoomQuestionsLoadedEvent.class,
                                e ->
                                        ROOM_ID.equals(
                                                ((RoomEvent.RoomQuestionsLoadedEvent) e)
                                                        .roomId())));
    }

    @Test
    void questionsLoaded_ignoredBeforePlayersLoaded() {
        fixture.given(
                        created(),
                        entered(INITIATOR_PLAYER),
                        entered(PARTICIPANT_PLAYER),
                        allPresent())
                .when(new RoomCommand.MarkRoomQuestionsLoadedCommand(ROOM_ID, List.of()))
                .expectNoEvents();
    }

    @Test
    void leave_clearsPresenceBeforePreparation() {
        fixture.given(created(), entered(INITIATOR_PLAYER))
                .when(new RoomCommand.LeaveRoomCommand(ROOM_ID, INITIATOR, null, null))
                .expectEventsMatching(
                        JoutioAxonMatchers.singlePayloadMatching(
                                RoomEvent.RoomLeftEvent.class,
                                e -> INITIATOR.equals(((RoomEvent.RoomLeftEvent) e).playerId())));
    }

    @Test
    void rejoin_afterLeaveIsAllowedBeforePreparation() {
        fixture.given(
                        created(),
                        entered(INITIATOR_PLAYER),
                        new RoomEvent.RoomLeftEvent(ROOM_ID, INITIATOR, Instant.now(), null, null, Set.of()))
                .when(new RoomCommand.JoinRoomCommand(ROOM_ID, INITIATOR, INITIATOR_PLAYER, null))
                .expectEventsMatching(
                        JoutioAxonMatchers.hasPayloadMatching(
                                RoomEvent.RoomEnteredEvent.class,
                                e ->
                                        INITIATOR.equals(
                                                ((RoomEvent.RoomEnteredEvent) e).playerId())));
    }

    @Test
    void leave_ignoredOncePreparationStarted() {
        fixture.given(
                        created(),
                        entered(INITIATOR_PLAYER),
                        entered(PARTICIPANT_PLAYER),
                        allPresent())
                .when(new RoomCommand.LeaveRoomCommand(ROOM_ID, PARTICIPANT, null, RoomLeaveReason.OFFLINE))
                .expectNoEvents();
    }

    @Test
    void leave_whenAlreadyAbsent_isNoop() {
        fixture.given(created())
                .when(new RoomCommand.LeaveRoomCommand(ROOM_ID, INITIATOR, null, null))
                .expectNoEvents();
    }

    @Test
    void leave_refusedToNonParticipant() {
        fixture.given(created())
                .when(new RoomCommand.LeaveRoomCommand(ROOM_ID, OTHER, null, null))
                .expectException(RoomExceptions.PlayerNotInRoomProblem.class);
    }

    // ===================== Présence observée par le serveur (sessions) =====================

    @Test
    void join_recordsTheSessionOfThePresence() {
        fixture.given(created())
                .when(new RoomCommand.JoinRoomCommand(ROOM_ID, INITIATOR, INITIATOR_PLAYER, "s1"))
                .expectEventsMatching(
                        JoutioAxonMatchers.singlePayloadMatching(
                                RoomEvent.RoomEnteredEvent.class,
                                e -> "s1".equals(((RoomEvent.RoomEnteredEvent) e).presenceId())));
    }

    @Test
    void join_sameSessionAgain_isNoop() {
        fixture.given(created(), entered(INITIATOR_PLAYER, "s1"))
                .when(new RoomCommand.JoinRoomCommand(ROOM_ID, INITIATOR, INITIATOR_PLAYER, "s1"))
                .expectNoEvents();
    }

    @Test
    void join_fromAnotherSession_takesOverThePresence() {
        fixture.given(created(), entered(INITIATOR_PLAYER, "s1"))
                .when(new RoomCommand.JoinRoomCommand(ROOM_ID, INITIATOR, INITIATOR_PLAYER, "s2"))
                .expectEventsMatching(
                        JoutioAxonMatchers.singlePayloadMatching(
                                RoomEvent.RoomEnteredEvent.class,
                                e -> "s2".equals(((RoomEvent.RoomEnteredEvent) e).presenceId())));
    }

    @Test
    void leave_ofTheCurrentSession_clearsPresence() {
        fixture.given(created(), entered(INITIATOR_PLAYER, "s1"))
                .when(leave(INITIATOR, "s1", RoomLeaveReason.DISCONNECTED))
                .expectEventsMatching(
                        JoutioAxonMatchers.singlePayloadMatching(
                                RoomEvent.RoomLeftEvent.class,
                                e ->
                                        ((RoomEvent.RoomLeftEvent) e).reason()
                                                == RoomLeaveReason.DISCONNECTED));
    }

    @Test
    void leave_ofAStaleSession_isIgnored() {
        // Reconnexion sur une autre instance : la sortie différée de l'ancienne session arrive après.
        fixture.given(created(), entered(INITIATOR_PLAYER, "s1"), entered(INITIATOR_PLAYER, "s2"))
                .when(leave(INITIATOR, "s1", RoomLeaveReason.DISCONNECTED))
                .expectNoEvents();
    }

    @Test
    void observedLeave_oncePreparationStarted_isIgnored() {
        fixture.given(
                        created(),
                        entered(INITIATOR_PLAYER, "s1"),
                        entered(PARTICIPANT_PLAYER, "s2"),
                        allPresent())
                .when(leave(PARTICIPANT, "s2", RoomLeaveReason.LEFT))
                .expectSuccessfulHandlerExecution()
                .expectNoEvents();
    }

    @Test
    void offlineLeave_clearsPresenceWhateverTheSession() {
        fixture.given(created(), entered(INITIATOR_PLAYER, "s1"))
                .when(leave(INITIATOR, null, RoomLeaveReason.OFFLINE))
                .expectEventsMatching(
                        JoutioAxonMatchers.singlePayloadMatching(
                                RoomEvent.RoomLeftEvent.class,
                                e ->
                                        ((RoomEvent.RoomLeftEvent) e).reason()
                                                == RoomLeaveReason.OFFLINE));
    }

    @Test
    void offlineLeave_oncePreparationStarted_isIgnored() {
        fixture.given(
                        created(),
                        entered(INITIATOR_PLAYER, "s1"),
                        entered(PARTICIPANT_PLAYER, "s2"),
                        allPresent())
                .when(leave(PARTICIPANT, null, RoomLeaveReason.OFFLINE))
                .expectSuccessfulHandlerExecution()
                .expectNoEvents();
    }

    @Test
    void rejoin_afterAnObservedLeave_opensANewPresence() {
        fixture.given(
                        created(),
                        entered(INITIATOR_PLAYER, "s1"),
                        new RoomEvent.RoomLeftEvent(
                                ROOM_ID, INITIATOR, Instant.now(), "s1", RoomLeaveReason.LEFT, Set.of()))
                .when(new RoomCommand.JoinRoomCommand(ROOM_ID, INITIATOR, INITIATOR_PLAYER, "s3"))
                .expectEventsMatching(
                        JoutioAxonMatchers.singlePayloadMatching(
                                RoomEvent.RoomEnteredEvent.class,
                                e -> "s3".equals(((RoomEvent.RoomEnteredEvent) e).presenceId())));
    }

    @Test
    void gameCreated_advancesPreparation() {
        fixture.given(
                        created(),
                        entered(INITIATOR_PLAYER),
                        entered(PARTICIPANT_PLAYER),
                        allPresent(),
                        playersLoaded(),
                        playedQuestionsLoaded(),
                        questionsLoaded())
                .when(new RoomCommand.MarkRoomGameCreatedCommand(ROOM_ID, "game-1"))
                .expectEventsMatching(
                        JoutioAxonMatchers.singlePayloadMatching(
                                RoomEvent.RoomGameCreatedEvent.class,
                                e ->
                                        "game-1"
                                                .equals(
                                                        ((RoomEvent.RoomGameCreatedEvent) e)
                                                                .gameId())));
    }

    @Test
    void gameCreated_ignoredBeforeQuestionsLoaded() {
        fixture.given(
                        created(),
                        entered(INITIATOR_PLAYER),
                        entered(PARTICIPANT_PLAYER),
                        allPresent(),
                        playersLoaded())
                .when(new RoomCommand.MarkRoomGameCreatedCommand(ROOM_ID, "game-1"))
                .expectNoEvents();
    }

    @Test
    void complete_closesRoomWithoutImmediatePurge() {
        fixture.given(
                        created(),
                        entered(INITIATOR_PLAYER),
                        entered(PARTICIPANT_PLAYER),
                        allPresent(),
                        playersLoaded(),
                        playedQuestionsLoaded(),
                        questionsLoaded(),
                        gameCreated())
                .when(new RoomCommand.CompleteRoomCommand(ROOM_ID, "game-1"))
                .expectEventsMatching(
                        JoutioAxonMatchers.singlePayloadMatching(
                                RoomEvent.RoomCompletedEvent.class,
                                e -> "game-1".equals(((RoomEvent.RoomCompletedEvent) e).gameId())));
    }

    @Test
    void complete_ignoredWithoutGameCreatedAck() {
        fixture.given(
                        created(),
                        entered(INITIATOR_PLAYER),
                        entered(PARTICIPANT_PLAYER),
                        allPresent(),
                        playersLoaded(),
                        playedQuestionsLoaded(),
                        questionsLoaded())
                .when(new RoomCommand.CompleteRoomCommand(ROOM_ID, "game-1"))
                .expectNoEvents();
    }

    @Test
    void complete_refusedWhenBothPlayersNotPresent() {
        fixture.given(created(), gameCreated())
                .when(new RoomCommand.CompleteRoomCommand(ROOM_ID, "game-1"))
                .expectException(RoomExceptions.ParticipantNotPresentProblem.class);
    }

    @Test
    void cancel_byInitiator_closesRoom() {
        fixture.given(created())
                .when(new RoomCommand.CancelRoomCommand(ROOM_ID, INITIATOR, RoomCancelReason.PLAYER_CANCELLED))
                .expectEventsMatching(
                        JoutioAxonMatchers.hasPayloadMatching(
                                RoomEvent.RoomCancelledEvent.class,
                                e ->
                                        INITIATOR.equals(
                                                ((RoomEvent.RoomCancelledEvent) e).initiatorId())));
    }

    @Test
    void cancel_remainsAllowedDuringPreparation() {
        fixture.given(
                        created(),
                        entered(INITIATOR_PLAYER),
                        entered(PARTICIPANT_PLAYER),
                        allPresent(),
                        playersLoaded())
                .when(new RoomCommand.CancelRoomCommand(ROOM_ID, INITIATOR, RoomCancelReason.PLAYER_CANCELLED))
                .expectEventsMatching(
                        JoutioAxonMatchers.hasPayloadMatching(
                                RoomEvent.RoomCancelledEvent.class, e -> true));
    }

    @Test
    void cancel_refusedToSecondPlayer() {
        fixture.given(created(), entered(INITIATOR_PLAYER), entered(PARTICIPANT_PLAYER))
                .when(new RoomCommand.CancelRoomCommand(ROOM_ID, PARTICIPANT, RoomCancelReason.PLAYER_CANCELLED))
                .expectException(RoomExceptions.PlayerNotInRoomProblem.class);
    }

    @Test
    void fail_setsFailed() {
        fixture.given(created(), entered(INITIATOR_PLAYER), entered(PARTICIPANT_PLAYER))
                .when(new RoomCommand.FailRoomCommand(ROOM_ID, "TOPIC_NOT_AVAILABLE_IN_LANGUAGE"))
                .expectEventsMatching(
                        JoutioAxonMatchers.hasPayloadMatching(
                                RoomEvent.RoomFailedEvent.class,
                                e ->
                                        "TOPIC_NOT_AVAILABLE_IN_LANGUAGE"
                                                .equals(((RoomEvent.RoomFailedEvent) e).reason())));
    }

    @Test
    void purgeRoom_leavesAggregateDeleted_soItCannotBeReloaded() {
        fixture.given(created(), new RoomEvent.RoomPurgedEvent(ROOM_ID, Instant.now()))
                .when(new RoomCommand.JoinRoomCommand(ROOM_ID, PARTICIPANT, PARTICIPANT_PLAYER, null))
                .expectException(
                        org.axonframework.modelling.command.AggregateNotFoundException.class);
    }

    @Test
    void presenceReminder_notifiesAbsentNominalOpponent() {
        fixture.given(createdWithOpponent(), entered(INITIATOR_PLAYER))
                .when(new RoomCommand.RemindRoomCommand(ROOM_ID, INITIATOR))
                .expectEventsMatching(
                        JoutioAxonMatchers.singlePayloadMatching(
                                RoomEvent.RoomRemindedEvent.class,
                                e ->
                                        INITIATOR.equals(((RoomEvent.RoomRemindedEvent) e).playerId())
                                                && OPPONENT.equals(
                                                        ((RoomEvent.RoomRemindedEvent) e)
                                                                .targetId())));
    }

    @Test
    void presenceReminder_isSymmetric_earlyGuestNudgesAbsentInitiator() {
        fixture.given(createdWithOpponent(), entered(OPPONENT_PLAYER))
                .when(new RoomCommand.RemindRoomCommand(ROOM_ID, OPPONENT))
                .expectEventsMatching(
                        JoutioAxonMatchers.singlePayloadMatching(
                                RoomEvent.RoomRemindedEvent.class,
                                e ->
                                        OPPONENT.equals(((RoomEvent.RoomRemindedEvent) e).playerId())
                                                && INITIATOR.equals(
                                                        ((RoomEvent.RoomRemindedEvent) e)
                                                                .targetId())));
    }

    @Test
    void presenceReminder_refusedWhenRequesterNotPresent() {
        fixture.given(createdWithOpponent())
                .when(new RoomCommand.RemindRoomCommand(ROOM_ID, INITIATOR))
                .expectException(RoomExceptions.RoomReminderNotAllowedProblem.class);
    }

    @Test
    void presenceReminder_refusedWithoutNamedOpponent() {
        fixture.given(created(), entered(INITIATOR_PLAYER))
                .when(new RoomCommand.RemindRoomCommand(ROOM_ID, INITIATOR))
                .expectException(RoomExceptions.RoomReminderUnavailableProblem.class);
    }

    @Test
    void presenceReminder_refusedToOutsider() {
        fixture.given(createdWithOpponent(), entered(INITIATOR_PLAYER))
                .when(new RoomCommand.RemindRoomCommand(ROOM_ID, OTHER))
                .expectException(RoomExceptions.PlayerNotInRoomProblem.class);
    }

    @Test
    void presenceReminder_refusedWithinCooldown() {
        fixture.given(
                        createdWithOpponent(),
                        entered(INITIATOR_PLAYER),
                        presenceReminded(Instant.now()))
                .when(new RoomCommand.RemindRoomCommand(ROOM_ID, INITIATOR))
                .expectException(RoomExceptions.RoomReminderCooldownProblem.class);
    }

    @Test
    void presenceReminder_allowedOnceCooldownElapsed() {
        fixture.given(
                        createdWithOpponent(),
                        entered(INITIATOR_PLAYER),
                        presenceReminded(Instant.now().minusSeconds(11)))
                .when(new RoomCommand.RemindRoomCommand(ROOM_ID, INITIATOR))
                .expectEventsMatching(
                        JoutioAxonMatchers.singlePayloadMatching(
                                RoomEvent.RoomRemindedEvent.class,
                                e -> INITIATOR.equals(((RoomEvent.RoomRemindedEvent) e).playerId())));
    }

    @Test
    void presenceReminder_ignoredOncePreparationStarted() {
        fixture.given(
                        createdWithOpponent(),
                        entered(INITIATOR_PLAYER),
                        entered(OPPONENT_PLAYER),
                        allPresent())
                .when(new RoomCommand.RemindRoomCommand(ROOM_ID, INITIATOR))
                .expectNoEvents();
    }

    @Test
    void presenceReminder_ignoredWhenRoomClosed() {
        fixture.given(
                        createdWithOpponent(),
                        entered(INITIATOR_PLAYER),
                        new RoomEvent.RoomCancelledEvent(ROOM_ID, INITIATOR, RoomCancelReason.PLAYER_CANCELLED, Set.of(), Instant.now()))
                .when(new RoomCommand.RemindRoomCommand(ROOM_ID, INITIATOR))
                .expectNoEvents();
    }

    @Test
    void presenceReminder_botRoom_neverWizzable() {
        // Salle bot : le bot est présent dès la création, l'humain qui apparaît engage la
        // préparation dans la même unité de travail → plus rien à relancer.
        fixture.given(createdBot(), enteredBot(), entered(INITIATOR_PLAYER), allPresent())
                .when(new RoomCommand.RemindRoomCommand(ROOM_ID, INITIATOR))
                .expectNoEvents();
    }

    private static RoomEvent.RoomCreatedEvent created() {
        return new RoomEvent.RoomCreatedEvent(
                ROOM_ID,
                TOPIC,
                INITIATOR,
                null,
                false,
                null,
                RoomOrigin.SHARED,
                null,
                RoomStatus.WAITING_PLAYERS,
                Instant.now().plusSeconds(3600),
                Instant.now());
    }

    /** Salle nominative ouverte (appariement, ou défi déjà accepté). */
    private static RoomEvent.RoomCreatedEvent createdWithOpponent() {
        return new RoomEvent.RoomCreatedEvent(
                ROOM_ID,
                TOPIC,
                INITIATOR,
                OPPONENT,
                false,
                null,
                RoomOrigin.MATCHMAKING,
                null,
                RoomStatus.WAITING_PLAYERS,
                Instant.now().plusSeconds(3600),
                Instant.now());
    }

    /** Défi envoyé, en attente de la réponse de l'invité. */
    private static RoomEvent.RoomCreatedEvent invited(Instant createdAt) {
        return new RoomEvent.RoomCreatedEvent(
                ROOM_ID,
                TOPIC,
                INITIATOR,
                OPPONENT,
                false,
                null,
                RoomOrigin.CHALLENGE,
                null,
                RoomStatus.INVITED,
                createdAt.plusSeconds(3600),
                createdAt);
    }

    private static RoomEvent.RoomCreatedEvent invited() {
        return invited(Instant.now());
    }

    private static RoomEvent.RoomAcceptedEvent accepted() {
        return new RoomEvent.RoomAcceptedEvent(
                ROOM_ID, TOPIC, INITIATOR, OPPONENT, RoomOrigin.CHALLENGE, Instant.now().plusSeconds(86_400), Instant.now());
    }

    private static RoomEvent.RoomRemindedEvent presenceReminded(Instant at) {
        return new RoomEvent.RoomRemindedEvent(
                ROOM_ID,
                INITIATOR,
                OPPONENT,
                RoomReminderStage.PRESENCE,
                TOPIC,
                null,
                Instant.now().plusSeconds(3600),
                at,
                at.plusSeconds(10));
    }

    private static RoomEvent.RoomCreatedEvent createdBot() {
        return new RoomEvent.RoomCreatedEvent(
                ROOM_ID,
                TOPIC,
                INITIATOR,
                JoutioConstants.SYSTEM_USER_ID,
                true,
                "NORMAL",
                RoomOrigin.BOT,
                null,
                RoomStatus.WAITING_PLAYERS,
                Instant.now().plusSeconds(3600),
                Instant.now());
    }

    @org.junit.jupiter.api.Test
    void entering_carriesTheHumanPlayersOfTheRoom() {
        fixture.given(created(), entered(INITIATOR_PLAYER))
                .when(new RoomCommand.JoinRoomCommand(ROOM_ID, PARTICIPANT, PARTICIPANT_PLAYER, "s2"))
                .expectEventsMatching(
                        JoutioAxonMatchers.hasPayloadMatching(
                                RoomEvent.RoomEnteredEvent.class,
                                e -> ((RoomEvent.RoomEnteredEvent) e).roomPlayerIds().equals(Set.of(INITIATOR, PARTICIPANT))));
    }

    private static RoomEvent.RoomEnteredEvent entered(RoomPlayer player) {
        return new RoomEvent.RoomEnteredEvent(ROOM_ID, player.playerId(), player, Instant.now(), null, Set.of());
    }

    private static RoomEvent.RoomEnteredEvent entered(RoomPlayer player, String presenceId) {
        return new RoomEvent.RoomEnteredEvent(
                ROOM_ID, player.playerId(), player, Instant.now(), presenceId, Set.of());
    }

    private static RoomCommand.LeaveRoomCommand leave(
            String playerId, String presenceId, RoomLeaveReason reason) {
        return new RoomCommand.LeaveRoomCommand(ROOM_ID, playerId, presenceId, reason);
    }

    private static RoomEvent.RoomEnteredEvent enteredBot() {
        return new RoomEvent.RoomEnteredEvent(
                ROOM_ID, JoutioConstants.SYSTEM_USER_ID, null, Instant.now(), null, Set.of());
    }

    private static RoomEvent.RoomAllPlayersPresentEvent allPresent() {
        return new RoomEvent.RoomAllPlayersPresentEvent(ROOM_ID, Instant.now());
    }

    private static RoomEvent.RoomPlayersLoadedEvent playersLoaded() {
        return new RoomEvent.RoomPlayersLoadedEvent(ROOM_ID, Instant.now());
    }

    private static RoomEvent.RoomPlayedQuestionsLoadedEvent playedQuestionsLoaded() {
        return new RoomEvent.RoomPlayedQuestionsLoadedEvent(ROOM_ID, Set.of("q-1"), Instant.now());
    }

    private static RoomEvent.RoomQuestionsLoadedEvent questionsLoaded() {
        return new RoomEvent.RoomQuestionsLoadedEvent(ROOM_ID, List.of(), Instant.now());
    }

    private static RoomEvent.RoomGameCreatedEvent gameCreated() {
        return new RoomEvent.RoomGameCreatedEvent(ROOM_ID, "game-1", Instant.now());
    }

    @Test
    void availabilityConfirmationAfterCancellationDoesNotStartGame() {
        fixture.given(
                        created(),
                        entered(INITIATOR_PLAYER),
                        entered(PARTICIPANT_PLAYER),
                        allPresent(),
                        playersLoaded(),
                        playedQuestionsLoaded(),
                        questionsLoaded(),
                        new RoomEvent.RoomCancelledEvent(ROOM_ID, INITIATOR, RoomCancelReason.PLAYER_CANCELLED, Set.of(), Instant.now()))
                .when(new RoomCommand.ConfirmRoomTopicAvailabilityCommand(ROOM_ID))
                .expectNoEvents();
    }

    // ── Invitation (défi, revanche) ──

    @Test
    void createSharedRoom_withNamedOpponent_isRefused() {
        fixture.givenNoPriorActivity()
                .when(new RoomCommand.CreateRoomCommand(
                        ROOM_ID, TOPIC, INITIATOR, OPPONENT, false, null, RoomOrigin.SHARED, null))
                .expectException(RoomExceptions.RoomInvalidInvitationProblem.class);
    }

    @Test
    void createChallenge_withoutOpponent_isRefused() {
        fixture.givenNoPriorActivity()
                .when(new RoomCommand.CreateRoomCommand(
                        ROOM_ID, TOPIC, INITIATOR, null, false, null, RoomOrigin.CHALLENGE, null))
                .expectException(RoomExceptions.RoomInvalidInvitationProblem.class);
    }

    @Test
    void createChallenge_againstSelf_isRefused() {
        fixture.givenNoPriorActivity()
                .when(new RoomCommand.CreateRoomCommand(
                        ROOM_ID, TOPIC, INITIATOR, INITIATOR, false, null, RoomOrigin.CHALLENGE, null))
                .expectException(RoomExceptions.RoomSelfInvitationProblem.class);
    }

    @Test
    void createRematch_expiresAfterTenSeconds_andCarriesTheSourceGame() {
        fixture.givenNoPriorActivity()
                .when(new RoomCommand.CreateRoomCommand(
                        ROOM_ID, TOPIC, INITIATOR, OPPONENT, false, null, RoomOrigin.REMATCH, "game-0"))
                .expectEventsMatching(JoutioAxonMatchers.singlePayloadMatching(
                        RoomEvent.RoomCreatedEvent.class,
                        e -> {
                            RoomEvent.RoomCreatedEvent created = (RoomEvent.RoomCreatedEvent) e;
                            return "game-0".equals(created.sourceGameId())
                                    && created.status() == RoomStatus.INVITED
                                    && created.expiresAt().equals(created.createdAt().plusSeconds(10));
                        }));
    }

    @Test
    void invitation_refusesEveryJoin() {
        fixture.given(invited())
                .when(new RoomCommand.JoinRoomCommand(ROOM_ID, INITIATOR, INITIATOR_PLAYER, "s1"))
                .expectException(RoomExceptions.RoomInvitationPendingProblem.class);
    }

    @Test
    void accept_byInvitee_opensTheRoomForADay() {
        fixture.given(invited())
                .when(new RoomCommand.AcceptRoomInvitationCommand(ROOM_ID, OPPONENT))
                .expectEventsMatching(JoutioAxonMatchers.singlePayloadMatching(
                        RoomEvent.RoomAcceptedEvent.class,
                        e -> {
                            RoomEvent.RoomAcceptedEvent accepted = (RoomEvent.RoomAcceptedEvent) e;
                            return INITIATOR.equals(accepted.initiatorId())
                                    && accepted.expiresAt().isAfter(Instant.now().plusSeconds(86_000));
                        }));
    }

    @Test
    void accept_twice_isIdempotent() {
        fixture.given(invited(), accepted())
                .when(new RoomCommand.AcceptRoomInvitationCommand(ROOM_ID, OPPONENT))
                .expectNoEvents();
    }

    @Test
    void accept_byAnotherPlayer_isRefused() {
        fixture.given(invited())
                .when(new RoomCommand.AcceptRoomInvitationCommand(ROOM_ID, INITIATOR))
                .expectException(RoomExceptions.RoomNotInvitedProblem.class);
    }

    @Test
    void accept_ofADeclinedInvitation_isRefused() {
        fixture.given(invited(), declined())
                .when(new RoomCommand.AcceptRoomInvitationCommand(ROOM_ID, OPPONENT))
                .expectException(RoomExceptions.RoomNotInvitationProblem.class);
    }

    @Test
    void accept_ofASharedRoom_isRefused() {
        fixture.given(created())
                .when(new RoomCommand.AcceptRoomInvitationCommand(ROOM_ID, PARTICIPANT))
                .expectException(RoomExceptions.RoomNotInvitedProblem.class);
    }

    @Test
    void acceptedInvitation_letsTheInviteeJoin() {
        fixture.given(invited(), accepted())
                .when(new RoomCommand.JoinRoomCommand(ROOM_ID, OPPONENT, OPPONENT_PLAYER, "s2"))
                .expectEventsMatching(JoutioAxonMatchers.singlePayloadMatching(
                        RoomEvent.RoomEnteredEvent.class,
                        e -> OPPONENT.equals(((RoomEvent.RoomEnteredEvent) e).playerId())));
    }

    @Test
    void decline_byInvitee_endsTheRoom() {
        fixture.given(invited())
                .when(new RoomCommand.DeclineRoomInvitationCommand(ROOM_ID, OPPONENT))
                .expectEventsMatching(JoutioAxonMatchers.singlePayloadMatching(
                        RoomEvent.RoomDeclinedEvent.class,
                        e -> OPPONENT.equals(((RoomEvent.RoomDeclinedEvent) e).opponentId())));
    }

    @Test
    void decline_twice_isIdempotent() {
        fixture.given(invited(), declined())
                .when(new RoomCommand.DeclineRoomInvitationCommand(ROOM_ID, OPPONENT))
                .expectNoEvents();
    }

    @Test
    void decline_afterAcceptance_isRefused() {
        fixture.given(invited(), accepted())
                .when(new RoomCommand.DeclineRoomInvitationCommand(ROOM_ID, OPPONENT))
                .expectException(RoomExceptions.RoomNotInvitationProblem.class);
    }

    @Test
    void cancel_ofAnInvitation_carriesBothPlayers() {
        fixture.given(invited())
                .when(new RoomCommand.CancelRoomCommand(ROOM_ID, INITIATOR, RoomCancelReason.PLAYER_BLOCKED))
                .expectEventsMatching(JoutioAxonMatchers.singlePayloadMatching(
                        RoomEvent.RoomCancelledEvent.class,
                        e -> {
                            RoomEvent.RoomCancelledEvent cancelled = (RoomEvent.RoomCancelledEvent) e;
                            return cancelled.reason() == RoomCancelReason.PLAYER_BLOCKED
                                    && cancelled.roomPlayerIds().equals(Set.of(INITIATOR, OPPONENT));
                        }));
    }

    @Test
    void invitationReminder_isRefusedBeforeTenMinutes() {
        fixture.given(invited())
                .when(new RoomCommand.RemindRoomCommand(ROOM_ID, INITIATOR))
                .expectException(RoomExceptions.RoomReminderCooldownProblem.class);
    }

    @Test
    void invitationReminder_afterTenMinutes_resendsTheInvitation() {
        fixture.given(invited(Instant.now().minusSeconds(601)))
                .when(new RoomCommand.RemindRoomCommand(ROOM_ID, INITIATOR))
                .expectEventsMatching(JoutioAxonMatchers.singlePayloadMatching(
                        RoomEvent.RoomRemindedEvent.class,
                        e -> {
                            RoomEvent.RoomRemindedEvent reminded = (RoomEvent.RoomRemindedEvent) e;
                            return reminded.stage() == RoomReminderStage.INVITATION
                                    && OPPONENT.equals(reminded.targetId())
                                    && reminded.remindableAt().equals(reminded.remindedAt().plusSeconds(600));
                        }));
    }

    @Test
    void invitationReminder_byInvitee_isRefused() {
        fixture.given(invited(Instant.now().minusSeconds(601)))
                .when(new RoomCommand.RemindRoomCommand(ROOM_ID, OPPONENT))
                .expectException(RoomExceptions.RoomReminderNotAllowedProblem.class);
    }

    @Test
    void expiry_ofAnInvitation_endsTheRoom() {
        fixture.given(invited(Instant.now().minusSeconds(3601)))
                .when(new RoomCommand.ExpireRoomCommand(ROOM_ID))
                .expectEventsMatching(JoutioAxonMatchers.singlePayloadMatching(
                        RoomEvent.RoomExpiredEvent.class,
                        e -> ((RoomEvent.RoomExpiredEvent) e).roomPlayerIds().equals(Set.of(INITIATOR, OPPONENT))));
    }

    @Test
    void expiry_beforeTheDeadlineOfTheCurrentStage_isIgnored() {
        // Deadline de l'invitation qui arrive après l'acceptation : la salle attend désormais un jour.
        fixture.given(invited(Instant.now().minusSeconds(3601)), accepted())
                .when(new RoomCommand.ExpireRoomCommand(ROOM_ID))
                .expectNoEvents();
    }

    @Test
    void completion_carriesOriginAndInitiator() {
        fixture.given(
                        invited(),
                        accepted(),
                        entered(INITIATOR_PLAYER),
                        entered(OPPONENT_PLAYER),
                        allPresent(),
                        playersLoaded(),
                        playedQuestionsLoaded(),
                        questionsLoaded(),
                        gameCreated())
                .when(new RoomCommand.CompleteRoomCommand(ROOM_ID, "game-1"))
                .expectEventsMatching(JoutioAxonMatchers.singlePayloadMatching(
                        RoomEvent.RoomCompletedEvent.class,
                        e -> {
                            RoomEvent.RoomCompletedEvent completed = (RoomEvent.RoomCompletedEvent) e;
                            return completed.origin() == RoomOrigin.CHALLENGE
                                    && INITIATOR.equals(completed.initiatorId());
                        }));
    }

    private static RoomEvent.RoomDeclinedEvent declined() {
        return new RoomEvent.RoomDeclinedEvent(
                ROOM_ID, TOPIC, INITIATOR, OPPONENT, RoomOrigin.CHALLENGE, Instant.now());
    }
}
