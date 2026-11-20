package com.joutio.matchmaking.domain.model;

import java.time.Duration;
import java.time.Instant;

/**
 * Relance d'un défi envoyé : le lanceur rappelle son invitation à l'adversaire. Une relance au plus toutes les 10 min,
 * comptées depuis l'envoi puis depuis la relance précédente (au plus 5 relances sur la vie d'un défi d'une heure).
 */
public interface ChallengeReminderRules {

    Duration REMINDER_COOLDOWN_DURATION = Duration.ofMinutes(10);

    /** Instant à partir duquel le défi peut être relancé, depuis l'envoi ou la dernière relance. */
    static Instant remindableAt(Instant since) {
        return since.plus(REMINDER_COOLDOWN_DURATION);
    }
}
