package com.joutio.matchmaking.domain.model;

/**
 * Motifs d'échec d'une salle portés par {@code RoomFailedEvent.reason} (chaîne libre côté
 * contrat, valeurs stables ci-dessous). Un motif par étape de préparation et par nature d'échec
 * (erreur vs délai dépassé) : le motif suffit à savoir où chercher, et les clients
 * ({@code @joutio/client-domain}) en dérivent un message adapté. Un motif inconnu d'un client
 * retombe sur le message générique.
 */
public final class RoomFailureReason {

    /** Historique des questions jouées (joutio-game) en erreur. */
    public static final String PLAYED_QUESTIONS_FAILED = "PLAYED_QUESTIONS_FAILED";

    /** Historique des questions jouées (joutio-game) sans réponse dans le délai d'étape. */
    public static final String PLAYED_QUESTIONS_TIMEOUT = "PLAYED_QUESTIONS_TIMEOUT";

    /** Tirage des questions (joutio-theme) en erreur. */
    public static final String QUESTION_DRAW_FAILED = "QUESTION_DRAW_FAILED";

    /** Tirage des questions (joutio-theme) sans réponse dans le délai d'étape. */
    public static final String QUESTION_DRAW_TIMEOUT = "QUESTION_DRAW_TIMEOUT";

    /** Tirage abouti mais moins de questions que de manches dans les langues des joueurs. */
    public static final String TOPIC_NOT_AVAILABLE_IN_LANGUAGE = "TOPIC_NOT_AVAILABLE_IN_LANGUAGE";

    /** Le sujet n'est plus jouable (plus publié) au moment de lancer la partie. */
    public static final String TOPIC_UNAVAILABLE = "TOPIC_UNAVAILABLE";

    /** Vérification de disponibilité du sujet (joutio-theme) en erreur ou hors délai. */
    public static final String TOPIC_CHECK_FAILED = "TOPIC_CHECK_FAILED";

    /** Accusé de création de partie (joutio-game) absent. */
    public static final String GAME_CREATION_TIMEOUT = "GAME_CREATION_TIMEOUT";

    /** Erreur inattendue dans un handler de la saga salle. */
    public static final String ROOM_PREPARATION_FAILED = "ROOM_PREPARATION_FAILED";

    private RoomFailureReason() {}
}
