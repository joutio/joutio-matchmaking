package com.joutio.matchmaking.domain.model;

/**
 * Statut d'une salle : invitation, attente des joueurs, préparation de la partie, puis état terminal.
 * <ul>
 *   <li>{@link #INVITED} — défi ou revanche envoyé à un joueur nommé, en attente de sa réponse
 *       (accepter ou refuser). Personne ne peut encore entrer.</li>
 *   <li>{@link #WAITING_PLAYERS} — ouverte, en attente de la présence des deux joueurs
 *       (sortie/rejointure autorisées).</li>
 *   <li>{@link #FETCHING_PLAYERS} — les deux joueurs sont présents, récupération des profils
 *       (sortie refusée : le duel est engagé).</li>
 *   <li>{@link #FETCHING_PLAYED_QUESTIONS} — récupération des questions déjà jouées (joutio-game).</li>
 *   <li>{@link #FETCHING_QUESTIONS} — tirage des questions.</li>
 *   <li>{@link #CREATING_GAME} — questions prêtes, la partie est en cours de création.</li>
 *   <li>{@link #GAME_CREATED} — la partie est créée (accusé du service game).</li>
 *   <li>{@link #COMPLETED} — partie créée ({@code gameId} porté par l'événement terminal).</li>
 *   <li>{@link #DECLINED} — l'invité a refusé le défi.</li>
 *   <li>{@link #CANCELLED} — salle annulée par l'initiateur (ou par un blocage).</li>
 *   <li>{@link #EXPIRED} — invitation sans réponse, ou salle jamais lancée.</li>
 *   <li>{@link #FAILED} — préparation ou création impossible.</li>
 * </ul>
 * Un état terminal est purgé par la saga après rétention, puis {@code markDeleted}.
 */
public enum RoomStatus {
    INVITED,
    WAITING_PLAYERS,
    FETCHING_PLAYERS,
    FETCHING_PLAYED_QUESTIONS,
    FETCHING_QUESTIONS,
    CREATING_GAME,
    GAME_CREATED,
    COMPLETED,
    DECLINED,
    CANCELLED,
    EXPIRED,
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == DECLINED || this == CANCELLED || this == EXPIRED || this == FAILED;
    }

    /** Ouverte côté joueurs : invitation en attente ou salle qui attend ses joueurs. */
    public boolean isOpen() {
        return this == INVITED || this == WAITING_PLAYERS;
    }
}
