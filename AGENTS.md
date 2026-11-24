# AGENTS.md — joutio-matchmaking

Mise en relation des joueurs : **appariement public** et **salles**. Un défi nominatif est une étape de la
salle (`INVITED`). La salle est l'unique point de préparation d'une partie. Package `com.joutio.matchmaking`, base
`joutio_matchmaking`, port local 8090. Service headless.

Lire : `../../AGENTS.md`, [`docs/backend/axon.md`](../../docs/backend/axon.md) (préparation des
commandes, saga non bloquante), [`docs/architecture.md`](../../docs/architecture.md#salles-et-parties),
exposition dans [`docs/api/parties.md`](../../docs/api/parties.md).

## Agrégats

Les deux agrégats sont **éphémères** : un état terminal est gardé 2 min (`*_PURGE`), puis purgé
(`markDeleted()` dans l'`@EventSourcingHandler` du `*PurgedEvent`, ligne de projection supprimée).

- **`MatchmakingAggregate`** : un ticket par recherche.
  - Statuts `SEARCHING|COMPLETED|CANCELLED|EXPIRED|FAILED`.
  - Ne garde que l'identifiant, le joueur, le statut et la salle. Les critères et la
    `TopicLanguageCoverage` (préparée par le BFF) voyagent dans `MatchmakingStartedEvent`.
  - Annulation `PLAYER_CANCELLED` (bouton) ou `PLAYER_LEFT` (le BFF constate le départ).
- **`RoomAggregate`** : deux humains, ou un humain et le bot (présent dès la création d'une salle
  `vsBot`).
  - L'origine (`RoomOrigin`), fixée à la création par le BFF ou la saga d'appariement, décide du second joueur :

    | Origine | Invité | Statut initial | Échéance |
    |---|---|---|---|
    | `CHALLENGE`, `REMATCH` (`sourceGameId`) | joueur nommé, qui accepte ou refuse | `INVITED` | 1 h, 10 s pour une revanche, puis 1 j une fois acceptée |
    | `SHARED` | aucun : le premier qui entre par le lien | `WAITING_PLAYERS` | 1 j |
    | `MATCHMAKING` | nommé, consentement donné par la recherche | `WAITING_PLAYERS` | 1 j |
    | `BOT` | joueur système, présent d'emblée | `WAITING_PLAYERS` | 1 j |

    L'agrégat refuse un invité incohérent avec l'origine et un défi contre soi-même.
  - Statut d'avancement : `INVITED → WAITING_PLAYERS → FETCHING_PLAYERS → FETCHING_PLAYED_QUESTIONS →
    FETCHING_QUESTIONS → CREATING_GAME → GAME_CREATED → COMPLETED`, ou
    `DECLINED|CANCELLED|EXPIRED|FAILED`.
  - **Invitation** : `AcceptRoomInvitationCommand` et `DeclineRoomInvitationCommand`, invité seul, idempotentes.
    Personne n'entre en `INVITED` (`RoomInvitationPendingProblem`). L'échéance de l'étape (`expiresAt`) est dans
    l'agrégat : une expiration plus précoce (deadline de l'invitation arrivée après l'acceptation) est ignorée.
  - **Présence observée** : le BFF émet `JoinRoomCommand` (avec le snapshot `RoomPlayer` qu'il a
    préparé et le `presenceId` de la session STOMP) et `LeaveRoomCommand`
    (`LEFT|DISCONNECTED|OFFLINE`).
  - Une sortie ne vaut que pour sa session ; un retour par une autre session reprend la présence.
  - Avant les deux présences, une sortie n'est pas destructive. Ensuite, elle est sans effet ;
    seule l'annulation par l'initiateur reste possible.
  - **Relance** (`RemindRoomCommand`, `RoomReminderRules`), selon l'étape (`RoomRemindedEvent.stage`) :
    - `INVITATION` : le lanceur renvoie son défi, toutes les 10 min au plus depuis l'envoi puis la relance
      précédente ; `remindableAt` dans le read model ;
    - `PRESENCE` (« wizz ») : un joueur présent appelle l'autre humain, absent, 10 s par joueur.
  - **Annulation** (`CancelRoomCommand`) au nom de l'initiateur, avec un motif `PLAYER_CANCELLED|PLAYER_BLOCKED`.

## Sagas

- **`MatchmakingSaga`** : deadline de 10 s.
  - Le **dernier arrivé** cherche des candidats dans `MatchmakingPoolPort` : même sujet, Elo ±400
    (`MatchmakingRules.RATING_WINDOW`), Elo le plus proche puis plus anciens d'abord.
  - Il filtre localement sur la couverture des langues (union ≥ 7 questions), réserve **la paire**
    (`claimPair` : deux lignes de `matchmaking_claim` dans une transaction, `on conflict do
    nothing`) et crée une salle nominative (fire-and-forget). Il n'en marque les deux tickets
    `MATCHED` qu'à l'accusé `RoomCreatedEvent` (deadline 30 s, échec → `FAILED`), pour que le client
    n'apprenne jamais un `roomId` avant que la salle existe. Une recherche déjà réservée, comme
    initiatrice ou comme candidate, ne peut plus être prise par un tiers.
  - Jamais avec un joueur en relation de blocage : le BFF passe `excludedPlayerIds` (social, dans les deux sens)
    dans `CreateMatchmakingCommand`.
  - Sans adversaire → `EXPIRED`, sans salle : le client décide (relance ou entraînement).
- **`RoomSaga`** : seul créateur de partie. L'expiration suit `expiresAt` de `RoomCreatedEvent`, puis celle de
  `RoomAcceptedEvent`. Elle compte les défis par issue (`RoomInvitationRecorder`,
  `joutio_room_invitations_total{origin, outcome}` : `SENT|ACCEPTED|DECLINED|CANCELLED|EXPIRED`).
  1. `RoomAllPlayersPresentEvent` : les profils sont déjà portés par les entrées.
  2. Historique des questions jouées : `PlayedQuestionsPort` → game, requête **non bloquante**.
  3. Tirage : `QuestionDrawPort` → theme, non bloquant. Préférence pour les questions jamais
     jouées, difficulté cible tirée de la maîtrise moyenne du sujet
     (`QuestionLevelRules.targetForTopicXp` : < 300 EASY, < 1 000 MEDIUM, < 2 500 HARD, sinon
     EXPERT).
  4. `CreateGameCommand`, assemblée par `GamePreparationService`, en fire-and-forget ; accusé
     attendu par `GameCreatedEvent` sous 30 s, sinon `GAME_CREATION_TIMEOUT`.

  Chaque callback envoie une commande `Mark*` qui porte le résultat, ou `FailRoomCommand` ; la
  saga ne se modifie jamais depuis un callback. Timeout de chaque étape : 10 s.
- **Échecs** (`RoomFailureReason`) : `PLAYED_QUESTIONS_FAILED|TIMEOUT`,
  `QUESTION_DRAW_FAILED|TIMEOUT`, `TOPIC_CHECK_FAILED`, `TOPIC_NOT_AVAILABLE_IN_LANGUAGE`,
  `GAME_CREATION_TIMEOUT`, `ROOM_PREPARATION_FAILED`, `ROOM_PREPARATION_STALLED` (balayeur). Ils
  passent tous par `RoomFailureRecorder` (log WARN `Échec de salle` et
  `joutio_room_failures_total{reason}`). Le motif est poussé au client (`ROOM_FAILED`).
- **Balayeurs** (tâches récurrentes db-scheduler, 1 min, une exécution dans le cluster ;
  `SweepTaskConfiguration`) : ils rattrapent les deadlines en mémoire perdues avec leur pod.
  - `RoomSweeper` : salle `INVITED` ou `WAITING_PLAYERS` dont l'échéance est passée depuis 10 min →
    expirée ; salle en préparation depuis plus de 1 min → `ROOM_PREPARATION_STALLED` ; salle terminale non
    purgée → purge.
  - `MatchmakingSweeper` : recherche `SEARCHING` 30 s après son échéance → expirée ; ticket
    terminal non purgé → purge.
- `OfflinePlayerRoomHandler` : sur `PlayerWentOfflineEvent` (profile), fait sortir le joueur
  (`OFFLINE`) des salles en attente.
- `UserBlockRoomHandler` : sur `UserBlockedEvent` (social), annule les salles `INVITED` ou `WAITING_PLAYERS` entre
  les deux joueurs (invité ou participant), au nom de leur initiateur (`PLAYER_BLOCKED`).

## Contrats (`joutio-matchmaking-domain`)

- Requêtes : `MatchmakingQuery`, `RoomQuery` (`GetRoomById`, `GetMyOpenRooms` : salles non terminales du
  joueur, invitations comprises, `GetRoomEventsQuery`).
- Événements utiles hors du service :
  - `RoomCreated/Accepted/Declined/Reminded/Cancelled/Expired/PurgedEvent` (notification, BFF) ;
  - `RoomCompletedEvent` (notification ; profile : badge Défis, par `origin` et `initiatorId`) ;
  - `RoomEnteredEvent` et `RoomLeftEvent`, avec `roomPlayerIds` (BFF : pastille de présence). `RoomCancelled`,
    `RoomExpired` et `RoomCompleted` portent aussi `roomPlayerIds`.
- Contrats consommés : `joutio-game-domain`, `joutio-theme-domain`, `joutio-profile-domain`,
  `joutio-identity-domain`, `joutio-social-domain`.

## Commandes

```bash
mvn -q -pl joutio-matchmaking-domain test
mvn -pl joutio-matchmaking-infrastructure test
```

## Pièges

- Ports sortants à **types locaux** (`DrawnQuestion`, `Set<String>`), jamais un type d'un autre
  `*-domain`, et toujours en `CompletableFuture`.
- Deux répliques : `room-saga`, `matchmaking-saga`, `room-projection` et `matchmaking-projection`
  sont segmentés (`application.yml`, [axon](../../docs/backend/axon.md#segments-et-répliques)).
  Aucun `@Scheduled` : un balayeur est une tâche récurrente db-scheduler.
- Le pool est lu dans `matchmaking-projection`, un autre processor que la saga : deux recherches
  très rapprochées peuvent encore se manquer si la projection est en retard.
