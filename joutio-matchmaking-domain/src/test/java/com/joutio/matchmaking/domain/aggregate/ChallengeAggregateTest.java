package com.joutio.matchmaking.domain.aggregate;

import com.joutio.axon.test.JoutioAxonMatchers;
import com.joutio.matchmaking.domain.command.ChallengeCommand;
import com.joutio.matchmaking.domain.event.ChallengeEvent;
import com.joutio.matchmaking.domain.exception.ChallengeExceptions;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

/**
 * Test Axon in-memory de {@link ChallengeAggregate} : intention asynchrone A → B,
 * sans présence, sans création de partie et sans validation de faisabilité (la salle prépare).
 */
class ChallengeAggregateTest {

    private static final String CHALLENGE_ID = "challenge-1";
    private static final String TOPIC = "topic-1";
    private static final String CHALLENGER = "challenger-1";
    private static final String OPPONENT = "opponent-1";
    private static final String OTHER = "other-1";

    private final AggregateTestFixture<ChallengeAggregate> fixture =
            new AggregateTestFixture<>(ChallengeAggregate.class);

    @Test
    void create_appliesCreatedEvent() {
        fixture.givenNoPriorActivity()
                .when(new ChallengeCommand.CreateChallengeCommand(CHALLENGE_ID, TOPIC, CHALLENGER, OPPONENT, null))
                .expectEventsMatching(JoutioAxonMatchers.singlePayloadMatching(
                        ChallengeEvent.ChallengeCreatedEvent.class,
                        e -> CHALLENGE_ID.equals(((ChallengeEvent.ChallengeCreatedEvent) e).challengeId())
                                && OPPONENT.equals(((ChallengeEvent.ChallengeCreatedEvent) e).opponentId())
                                && ((ChallengeEvent.ChallengeCreatedEvent) e).gameId() == null));
    }

    @Test
    void create_rematchCarriesSourceGameId() {
        fixture.givenNoPriorActivity()
                .when(new ChallengeCommand.CreateChallengeCommand(CHALLENGE_ID, TOPIC, CHALLENGER, OPPONENT, "game-1"))
                .expectEventsMatching(JoutioAxonMatchers.singlePayloadMatching(
                        ChallengeEvent.ChallengeCreatedEvent.class,
                        e -> "game-1".equals(((ChallengeEvent.ChallengeCreatedEvent) e).gameId())));
    }

    @Test
    void create_rematchExpiresAfterTenSeconds() {
        Instant before = Instant.now();
        fixture.givenNoPriorActivity()
                .when(new ChallengeCommand.CreateChallengeCommand(CHALLENGE_ID, TOPIC, CHALLENGER, OPPONENT, "game-1"))
                .expectEventsMatching(JoutioAxonMatchers.singlePayloadMatching(
                        ChallengeEvent.ChallengeCreatedEvent.class,
                        e -> isExpiryBetween(
                                ((ChallengeEvent.ChallengeCreatedEvent) e).expiresAt(),
                                before.plusSeconds(9),
                                before.plusSeconds(12))));
    }

    @Test
    void create_normalChallengeKeepsOneHourExpiry() {
        Instant before = Instant.now();
        fixture.givenNoPriorActivity()
                .when(new ChallengeCommand.CreateChallengeCommand(CHALLENGE_ID, TOPIC, CHALLENGER, OPPONENT, null))
                .expectEventsMatching(JoutioAxonMatchers.singlePayloadMatching(
                        ChallengeEvent.ChallengeCreatedEvent.class,
                        e -> isExpiryBetween(
                                ((ChallengeEvent.ChallengeCreatedEvent) e).expiresAt(),
                                before.plus(Duration.ofMinutes(59)),
                                before.plus(Duration.ofMinutes(61)))));
    }

    private static boolean isExpiryBetween(Instant expiry, Instant min, Instant max) {
        return !expiry.isBefore(min) && !expiry.isAfter(max);
    }

    @Test
    void cannotChallengeSelf() {
        fixture.givenNoPriorActivity()
                .when(new ChallengeCommand.CreateChallengeCommand(CHALLENGE_ID, TOPIC, CHALLENGER, CHALLENGER, null))
                .expectException(ChallengeExceptions.CannotChallengeSelfProblem.class);
    }

    @Test
    void accept_byOpponent_appliesAccepted() {
        fixture.given(created())
                .when(new ChallengeCommand.AcceptChallengeCommand(CHALLENGE_ID, OPPONENT))
                .expectEventsMatching(JoutioAxonMatchers.hasPayloadMatching(
                        ChallengeEvent.ChallengeAcceptedEvent.class,
                        e -> OPPONENT.equals(((ChallengeEvent.ChallengeAcceptedEvent) e).opponentId())));
    }

    @Test
    void accept_twiceIsIdempotent() {
        fixture.given(created(), accepted())
                .when(new ChallengeCommand.AcceptChallengeCommand(CHALLENGE_ID, OPPONENT))
                .expectNoEvents();
    }

    @Test
    void accept_refusedToNonInvitedPlayer() {
        fixture.given(created())
                .when(new ChallengeCommand.AcceptChallengeCommand(CHALLENGE_ID, OTHER))
                .expectException(ChallengeExceptions.ChallengeNotInvitedProblem.class);
    }

    @Test
    void decline_byOpponent_appliesDeclined() {
        fixture.given(created())
                .when(new ChallengeCommand.DeclineChallengeCommand(CHALLENGE_ID, OPPONENT))
                .expectEventsMatching(JoutioAxonMatchers.hasPayloadMatching(
                        ChallengeEvent.ChallengeDeclinedEvent.class,
                        e -> OPPONENT.equals(((ChallengeEvent.ChallengeDeclinedEvent) e).opponentId())));
    }

    @Test
    void cancel_byChallenger_appliesCancelled() {
        fixture.given(created())
                .when(new ChallengeCommand.CancelChallengeCommand(CHALLENGE_ID, CHALLENGER))
                .expectEventsMatching(JoutioAxonMatchers.hasPayloadMatching(
                        ChallengeEvent.ChallengeCancelledEvent.class,
                        e -> CHALLENGER.equals(((ChallengeEvent.ChallengeCancelledEvent) e).challengerId())));
    }

    @Test
    void cancel_refusedToOpponent() {
        fixture.given(created())
                .when(new ChallengeCommand.CancelChallengeCommand(CHALLENGE_ID, OPPONENT))
                .expectException(ChallengeExceptions.PlayerNotChallengerProblem.class);
    }

    @Test
    void expire_appliesExpired() {
        fixture.given(created())
                .when(new ChallengeCommand.ExpireChallengeCommand(CHALLENGE_ID))
                .expectEventsMatching(JoutioAxonMatchers.hasPayloadMatching(
                        ChallengeEvent.ChallengeExpiredEvent.class, e -> true));
    }

    @Test
    void expire_isIgnoredWhenAlreadyResolved() {
        fixture.given(created(), accepted())
                .when(new ChallengeCommand.ExpireChallengeCommand(CHALLENGE_ID))
                .expectNoEvents();
    }

    @Test
    void purge_afterTerminal_appliesPurgedAndDeletesAggregate() {
        fixture.given(created(), accepted())
                .when(new ChallengeCommand.PurgeChallengeCommand(CHALLENGE_ID))
                .expectEventsMatching(JoutioAxonMatchers.hasPayloadMatching(
                        ChallengeEvent.ChallengePurgedEvent.class,
                        e -> CHALLENGE_ID.equals(((ChallengeEvent.ChallengePurgedEvent) e).challengeId())))
                .expectMarkedDeleted();
    }

    @Test
    void start_afterAcceptance_appliesStarted() {
        fixture.given(created(), accepted())
                .when(new ChallengeCommand.StartChallengeCommand(CHALLENGE_ID, "game-1"))
                .expectEventsMatching(JoutioAxonMatchers.singlePayloadMatching(
                        ChallengeEvent.ChallengeStartedEvent.class,
                        e -> "game-1".equals(((ChallengeEvent.ChallengeStartedEvent) e).gameId())
                                && CHALLENGER.equals(((ChallengeEvent.ChallengeStartedEvent) e).challengerId())));
    }

    @Test
    void start_twiceIsIdempotent() {
        fixture.given(created(), accepted(), started())
                .when(new ChallengeCommand.StartChallengeCommand(CHALLENGE_ID, "game-2"))
                .expectNoEvents();
    }

    @Test
    void start_beforeAcceptanceIsIgnored() {
        // Fait dérivé de la salle : un défi non accepté ne fait jamais échouer la saga.
        fixture.given(created())
                .when(new ChallengeCommand.StartChallengeCommand(CHALLENGE_ID, "game-1"))
                .expectNoEvents();
    }

    @Test
    void remind_byChallengerAfterCooldown_appliesReminded() {
        fixture.given(createdAt(Instant.now().minus(Duration.ofMinutes(11))))
                .when(new ChallengeCommand.RemindChallengeCommand(CHALLENGE_ID, CHALLENGER))
                .expectEventsMatching(JoutioAxonMatchers.singlePayloadMatching(
                        ChallengeEvent.ChallengeRemindedEvent.class,
                        e -> {
                            var reminded = (ChallengeEvent.ChallengeRemindedEvent) e;
                            return OPPONENT.equals(reminded.opponentId())
                                    && reminded.remindableAt().equals(reminded.remindedAt().plus(Duration.ofMinutes(10)));
                        }));
    }

    @Test
    void remind_withinTenMinutesOfSending_isRejected() {
        fixture.given(created())
                .when(new ChallengeCommand.RemindChallengeCommand(CHALLENGE_ID, CHALLENGER))
                .expectException(ChallengeExceptions.ChallengeReminderCooldownProblem.class);
    }

    @Test
    void remind_withinTenMinutesOfPreviousReminder_isRejected() {
        Instant sent = Instant.now().minus(Duration.ofMinutes(15));
        Instant reminded = Instant.now().minus(Duration.ofMinutes(2));
        fixture.given(createdAt(sent), new ChallengeEvent.ChallengeRemindedEvent(CHALLENGE_ID, TOPIC, CHALLENGER,
                        OPPONENT, null, sent.plusSeconds(3600), reminded, reminded.plus(Duration.ofMinutes(10))))
                .when(new ChallengeCommand.RemindChallengeCommand(CHALLENGE_ID, CHALLENGER))
                .expectException(ChallengeExceptions.ChallengeReminderCooldownProblem.class);
    }

    @Test
    void remind_byOpponent_isRejected() {
        fixture.given(createdAt(Instant.now().minus(Duration.ofMinutes(11))))
                .when(new ChallengeCommand.RemindChallengeCommand(CHALLENGE_ID, OPPONENT))
                .expectException(ChallengeExceptions.PlayerNotChallengerProblem.class);
    }

    @Test
    void remind_resolvedChallenge_isRejected() {
        fixture.given(createdAt(Instant.now().minus(Duration.ofMinutes(11))), accepted())
                .when(new ChallengeCommand.RemindChallengeCommand(CHALLENGE_ID, CHALLENGER))
                .expectException(ChallengeExceptions.ChallengeNotPendingProblem.class);
    }

    private static ChallengeEvent.ChallengeCreatedEvent createdAt(Instant createdAt) {
        return new ChallengeEvent.ChallengeCreatedEvent(
                CHALLENGE_ID, TOPIC, CHALLENGER, OPPONENT, null, createdAt.plusSeconds(3600), createdAt);
    }

    private static ChallengeEvent.ChallengeCreatedEvent created() {
        return new ChallengeEvent.ChallengeCreatedEvent(
                CHALLENGE_ID, TOPIC, CHALLENGER, OPPONENT, null,
                Instant.now().plusSeconds(3600), Instant.now());
    }

    private static ChallengeEvent.ChallengeAcceptedEvent accepted() {
        return new ChallengeEvent.ChallengeAcceptedEvent(
                CHALLENGE_ID, TOPIC, CHALLENGER, OPPONENT, Instant.now());
    }

    private static ChallengeEvent.ChallengeStartedEvent started() {
        return new ChallengeEvent.ChallengeStartedEvent(
                CHALLENGE_ID, CHALLENGER, OPPONENT, "game-1", Instant.now());
    }
}
