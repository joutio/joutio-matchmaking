# AGENTS.md — joutio-matchmaking

Mise en relation des joueurs : **appariement public**, **défis nominatifs** et **salles**. La
salle est l'unique point de préparation d'une partie. Package `com.joutio.matchmaking`, base
`joutio_matchmaking`, port local 8090. Service headless.

Lire : `../../AGENTS.md`, [`docs/backend/axon.md`](../../docs/backend/axon.md) (préparation des
commandes, saga non bloquante), [`docs/architecture.md`](../../docs/architecture.md#salles-et-parties),
exposition dans [`docs/api/parties.md`](../../docs/api/parties.md).

## Agrégats

Les trois agrégats sont **éphémères** : un état terminal est gardé 2 min (`*_PURGE`), puis purgé
(`markDeleted()` dans l'`@EventSourcingHandler` du `*PurgedEvent`, ligne de projection supprimée).

- **`MatchmakingAggregate`** : un ticket par recherche.
  - Statuts `SEARCHING|COMPLETED|CANCELLED|EXPIRED|FAILED`.
  - Ne garde que l'identifiant, le joueur, le statut et la salle. Les critères et la
    `TopicLanguageCoverage` (préparée par le BFF) voyagent dans `MatchmakingStartedEvent`.
  - Annulation `PLAYER_CANCELLED` (bouton) ou `PLAYER_LEFT` (le BFF constate le départ).
- **`ChallengeAggregate`** : intention asynchrone « A défie B ».
  - Statuts `PENDING|ACCEPTED|DECLINED|CANCELLED|EXPIRED`.
  - TTL 1 h, 10 s pour une revanche (`gameId` d'une partie terminée), porté par l'événement
    (`expiresAt`).
  - Invariants de la commande seulement (pas d'auto-défi), aucune vérification de faisabilité.
  - Salle dérivée de façon déterministe (`ChallengeRoomId.of`).
  - **Relance** (`RemindChallengeCommand`) : lanceur seul, défi `PENDING`, 10 min depuis l'envoi puis depuis la
    relance précédente (`ChallengeReminderRules`) ; `remindableAt` dans le read model.
- **`RoomAggregate`** : deux humains, ou un humain et le bot (présent dès la création d'une salle
  `vsBot`).
  - Origine fixée à la création (`RoomOrigin` : `SHARED` par le BFF, `BOT`, `MATCHMAKING` par la saga d'appariement,
    `CHALLENGE` ou `REMATCH` par `ChallengeSaga` selon le `gameId` du défi) ; `origin` dans le read model.
  - Statut d'avancement : `WAITING_PLAYERS → FETCHING_PLAYERS → FETCHING_PLAYED_QUESTIONS →
    FETCHING_QUESTIONS → CREATING_GAME → GAME_CREATED → COMPLETED`, ou
    `CANCELLED|EXPIRED|FAILED`.
  - **Présence observée** : le BFF émet `JoinRoomCommand` (avec le snapshot `RoomPlayer` qu'il a
    préparé et le `presenceId` de la session STOMP) et `LeaveRoomCommand`
    (`LEFT|DISCONNECTED|OFFLINE`).
  - Une sortie ne vaut que pour sa session ; un retour par une autre session reprend la présence.
  - Avant les deux présences, une sortie n'est pas destructive. Ensuite, elle est sans effet ;
    seule l'annulation par l'initiateur reste possible.
  - **Relance** (`WizzRoomCommand`) : émetteur présent, cible humaine absente, 10 s par joueur
    (`RoomWizzRules`). Pur temps réel.
  - Salle non lancée : expire après 1 jour.

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
- **`ChallengeSaga`** :
  - expiration à `expiresAt` ;
  - à l'acceptation, création de la salle (une seule fois) ;
  - `RoomCreatedEvent` → `MarkChallengeRoomCreatedCommand` → `ChallengeRoomCreatedEvent` : le
    `roomId` du défi (projection) n'apparaît qu'à ce moment, jamais à l'acceptation ;
  - `RoomGameCreatedEvent` → `StartChallengeCommand` → `ChallengeStartedEvent`, qui sert au badge
    Défis de profile ;
  - `ChallengePurgeSweeper` sert de filet de purge.
- **`RoomSaga`** : seul créateur de partie.
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
  - `RoomSweeper` : salle en préparation depuis plus de 1 min → `ROOM_PREPARATION_STALLED` ; salle
    terminale non purgée → purge.
  - `MatchmakingSweeper` : recherche `SEARCHING` 30 s après son échéance → expirée ; ticket
    terminal non purgé → purge.
  - `ChallengePurgeSweeper` : défi terminal non purgé → purge.
- `OfflinePlayerRoomHandler` : sur `PlayerWentOfflineEvent` (profile), fait sortir le joueur
  (`OFFLINE`) des salles en attente.
- `UserBlockChallengeHandler` : sur `UserBlockedEvent` (social), annule les défis en attente entre les deux
  joueurs, au nom de leur lanceur.

## Contrats (`joutio-matchmaking-domain`)

- Requêtes : `MatchmakingQuery`, `ChallengeQuery`, `RoomQuery` (`GetRoomById`, `GetMyOpenRooms`,
  `GetRoomEventsQuery`).
- Événements utiles hors du service :
  - `ChallengeCreated/Reminded/Accepted/Declined/Cancelled/Expired/PurgedEvent` (notification) ;
  - `ChallengeStartedEvent` (profile) ;
  - `RoomEnteredEvent` et `RoomLeftEvent`, avec `roomPlayerIds` (BFF : pastille de présence) ;
  - `RoomWizzedEvent` (notification) ;
  - `RoomCompletedEvent` (notification).
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
