package com.joutio.matchmaking.domain.model;

import java.time.Duration;
import java.time.Instant;

/**
 * Relance d'une salle, selon son étape ({@link RoomReminderStage}) :
 * <ul>
 *   <li>invitation : le lanceur renvoie son défi, au plus toutes les 10 min depuis l'envoi puis la relance
 *       précédente ;</li>
 *   <li>présence : un joueur présent appelle l'autre, absent, au plus toutes les 10 s par joueur.</li>
 * </ul>
 */
public interface RoomReminderRules {

    Duration INVITATION_COOLDOWN = Duration.ofMinutes(10);

    Duration PRESENCE_COOLDOWN = Duration.ofSeconds(10);

    static Instant nextReminderAt(RoomReminderStage stage, Instant since) {
        return since.plus(stage == RoomReminderStage.INVITATION ? INVITATION_COOLDOWN : PRESENCE_COOLDOWN);
    }
}
