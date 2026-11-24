package com.joutio.matchmaking.domain.exception;

import com.joutio.microservice.core.domain.exception.ProblemCategory;

import java.time.Instant;
import java.util.Map;

public final class RoomExceptions {

    private RoomExceptions() {}

    public static class RoomNotFoundProblem extends RoomProblem {
        public RoomNotFoundProblem(String roomId) {
            super(roomId, "urn:joutio:room:notFound",
                    ProblemCategory.BUSINESS_RESOURCE_MISSING,
                    "Salle introuvable", "La salle " + roomId + " n'existe pas", null);
        }
    }

    public static class RoomNotAvailableProblem extends RoomProblem {
        public RoomNotAvailableProblem(String roomId) {
            super(roomId, "urn:joutio:room:notAvailable",
                    "Salle non disponible", "Cette salle est déjà fermée ou annulée");
        }
    }

    public static class RoomAlreadyFullProblem extends RoomProblem {
        public RoomAlreadyFullProblem(String roomId) {
            super(roomId, "urn:joutio:room:alreadyFull",
                    "Salle complète", "Cette salle a déjà un second participant");
        }
    }

    public static class MissingPlayerIdentifierProblem extends RoomProblem {
        public MissingPlayerIdentifierProblem(String roomId) {
            super(roomId, "urn:joutio:room:missingPlayerId",
                    "Identifiant joueur manquant", "Un identifiant joueur valide est requis");
        }
    }

    public static class MissingTopicIdentifierProblem extends RoomProblem {
        public MissingTopicIdentifierProblem(String roomId) {
            super(roomId, "urn:joutio:room:missingTopicId",
                    "Identifiant topic manquant", "Un identifiant topic valide est requis");
        }
    }

    public static class MissingGameIdentifierProblem extends RoomProblem {
        public MissingGameIdentifierProblem(String roomId) {
            super(roomId, "urn:joutio:room:missingGameId",
                    "Identifiant partie manquant", "Un identifiant de partie valide est requis");
        }
    }

    public static class PlayerNotInRoomProblem extends RoomProblem {
        public PlayerNotInRoomProblem(String roomId, String playerId) {
            super(roomId, "urn:joutio:room:playerNotInRoom",
                    "Joueur absent", "Le joueur " + playerId + " n'est pas dans cette salle");
        }
    }

    public static class RoomNotInvitedProblem extends RoomProblem {
        public RoomNotInvitedProblem(String roomId, String playerId) {
            super(roomId, "urn:joutio:room:notInvited",
                    ProblemCategory.PERMISSION,
                    "Salle non adressée",
                    "Le joueur " + playerId + " n'est pas l'invité de cette salle",
                    Map.of("playerId", playerId));
        }
    }

    public static class ParticipantNotPresentProblem extends RoomProblem {
        public ParticipantNotPresentProblem(String roomId) {
            super(roomId, "urn:joutio:room:participantNotPresent",
                    "Second participant absent", "Un second participant est requis pour clore la salle");
        }
    }

    /** Personne n'entre tant que l'invité n'a pas accepté le défi. */
    public static class RoomInvitationPendingProblem extends RoomProblem {
        public RoomInvitationPendingProblem(String roomId) {
            super(roomId, "urn:joutio:room:invitationPending",
                    "Défi en attente", "L'invité n'a pas encore accepté ce défi");
        }
    }

    /** Accepter, refuser ou relancer une invitation qui n'est plus en attente. */
    public static class RoomNotInvitationProblem extends RoomProblem {
        public RoomNotInvitationProblem(String roomId, String status) {
            super(roomId, "urn:joutio:room:notInvitation",
                    ProblemCategory.BUSINESS_AGGREGATE,
                    "Défi résolu",
                    "Ce défi n'est plus en attente (" + status + ")",
                    Map.of("status", status));
        }
    }

    public static class RoomSelfInvitationProblem extends RoomProblem {
        public RoomSelfInvitationProblem(String roomId) {
            super(roomId, "urn:joutio:room:selfInvitation",
                    "Défi impossible", "Un joueur ne peut pas se défier lui-même");
        }
    }

    /** L'invité ne correspond pas à l'origine : un défi nomme son invité, un salon partagé n'en nomme pas. */
    public static class RoomInvalidInvitationProblem extends RoomProblem {
        public RoomInvalidInvitationProblem(String roomId, String origin) {
            super(roomId, "urn:joutio:room:invalidInvitation",
                    "Invité incohérent",
                    "L'invité de la salle ne correspond pas à son origine (" + origin + ")",
                    Map.of("origin", origin));
        }
    }

    /** Seul le lanceur relance son invitation ; en salle, seul un joueur présent appelle l'autre. */
    public static class RoomReminderNotAllowedProblem extends RoomProblem {
        public RoomReminderNotAllowedProblem(String roomId, String playerId) {
            super(roomId, "urn:joutio:room:reminderNotAllowed",
                    ProblemCategory.PERMISSION,
                    "Relance impossible",
                    "Le joueur " + playerId + " ne peut pas relancer cette salle",
                    Map.of("playerId", playerId));
        }
    }

    /** Rien à relancer : salon partagé sans participant, adversaire déjà présent ou bot. */
    public static class RoomReminderUnavailableProblem extends RoomProblem {
        public RoomReminderUnavailableProblem(String roomId) {
            super(roomId, "urn:joutio:room:reminderUnavailable",
                    "Aucun adversaire à relancer",
                    "Cette salle n'a pas d'adversaire humain absent à relancer");
        }
    }

    /** Cadence de la relance ({@code RoomReminderRules}) : prochaine relance à {@code remindableAt}. */
    public static class RoomReminderCooldownProblem extends RoomProblem {
        public RoomReminderCooldownProblem(String roomId, Instant remindableAt) {
            super(roomId, "urn:joutio:room:reminderCooldown",
                    "Relance trop rapide",
                    "Prochaine relance possible à " + remindableAt,
                    Map.of("remindableAt", remindableAt.toString()));
        }
    }
}
