package com.joutio.matchmaking.application.projection;

import com.joutio.matchmaking.domain.event.RoomEvent;
import com.joutio.matchmaking.domain.model.Room;
import com.joutio.matchmaking.domain.model.RoomReminderRules;
import com.joutio.matchmaking.domain.model.RoomReminderStage;
import com.joutio.matchmaking.domain.model.RoomStatus;
import com.joutio.matchmaking.application.service.MyRoomsService;
import com.joutio.matchmaking.domain.port.out.RoomRepositoryPort;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.UnaryOperator;

/**
 * Projection read-only de la salle. Tous les états terminaux (partie créée, refusée, annulée, expirée, échouée) sont
 * conservés le temps de la rétention, puis supprimés sur {@code RoomPurgedEvent}.
 */
@Component
@ProcessingGroup("room-projection")
public class RoomProjection {

    private final RoomRepositoryPort roomRepositoryPort;
    private final MyRoomsService myRooms;

    public RoomProjection(RoomRepositoryPort roomRepositoryPort, MyRoomsService myRooms) {
        this.roomRepositoryPort = roomRepositoryPort;
        this.myRooms = myRooms;
    }

    @EventHandler
    @Transactional
    public void on(RoomEvent.RoomCreatedEvent event) {
        save(Room.builder()
                .roomId(event.roomId())
                .topicId(event.topicId())
                .initiatorId(event.initiatorId())
                .opponentId(event.opponentId())
                .vsBot(event.vsBot())
                .origin(event.origin())
                .sourceGameId(event.sourceGameId())
                .status(event.status())
                .createdAt(event.createdAt())
                .expiresAt(event.expiresAt())
                .remindableAt(event.status() == RoomStatus.INVITED
                        ? RoomReminderRules.nextReminderAt(RoomReminderStage.INVITATION, event.createdAt())
                        : null)
                .updatedAt(event.createdAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(RoomEvent.RoomAcceptedEvent event) {
        update(event.roomId(), room -> room.toBuilder()
                .status(RoomStatus.WAITING_PLAYERS)
                .expiresAt(event.expiresAt())
                .remindableAt(null)
                .updatedAt(event.acceptedAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(RoomEvent.RoomDeclinedEvent event) {
        update(event.roomId(), room -> room.toBuilder()
                .status(RoomStatus.DECLINED)
                .updatedAt(event.declinedAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(RoomEvent.RoomRemindedEvent event) {
        if (event.stage() != RoomReminderStage.INVITATION) {
            return;
        }
        update(event.roomId(), room -> room.toBuilder()
                .remindableAt(event.remindableAt())
                .updatedAt(event.remindedAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(RoomEvent.RoomEnteredEvent event) {
        update(event.roomId(), room -> {
            boolean isInitiator = event.playerId().equals(room.initiatorId());
            String participantId = !isInitiator && room.participantId() == null
                    ? event.playerId()
                    : room.participantId();
            return room.toBuilder()
                    .participantId(participantId)
                    .initiatorPresent(room.initiatorPresent() || isInitiator)
                    .participantPresent(room.participantPresent()
                            || (!isInitiator && event.playerId().equals(participantId)))
                    .updatedAt(event.enteredAt())
                    .build();
        });
    }

    @EventHandler
    @Transactional
    public void on(RoomEvent.RoomAllPlayersPresentEvent event) {
        update(event.roomId(), room -> room.toBuilder()
                .status(RoomStatus.FETCHING_PLAYERS)
                .updatedAt(event.presentAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(RoomEvent.RoomPlayersLoadedEvent event) {
        update(event.roomId(), room -> room.toBuilder()
                .status(RoomStatus.FETCHING_PLAYED_QUESTIONS)
                .updatedAt(event.loadedAt())
                .build());
    }

    /** Historique des questions jouées résolu : le tirage filtré peut démarrer. */
    @EventHandler
    @Transactional
    public void on(RoomEvent.RoomPlayedQuestionsLoadedEvent event) {
        update(event.roomId(), room -> room.toBuilder()
                .status(RoomStatus.FETCHING_QUESTIONS)
                .updatedAt(event.loadedAt())
                .build());
    }

    /** Fin de préparation : la saga lance la création de la partie. */
    @EventHandler
    @Transactional
    public void on(RoomEvent.RoomQuestionsLoadedEvent event) {
        update(event.roomId(), room -> room.toBuilder()
                .status(RoomStatus.CREATING_GAME)
                .updatedAt(event.loadedAt())
                .build());
    }

    /** Le service game a accusé la création : le {@code gameId} est exposé avant la clôture. */
    @EventHandler
    @Transactional
    public void on(RoomEvent.RoomGameCreatedEvent event) {
        update(event.roomId(), room -> room.toBuilder()
                .status(RoomStatus.GAME_CREATED)
                .gameId(event.gameId())
                .updatedAt(event.gameCreatedAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(RoomEvent.RoomLeftEvent event) {
        update(event.roomId(), room -> room.toBuilder()
                .initiatorPresent(room.initiatorPresent()
                        && !event.playerId().equals(room.initiatorId()))
                .participantPresent(room.participantPresent()
                        && !event.playerId().equals(room.participantId()))
                .updatedAt(event.leftAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(RoomEvent.RoomCancelledEvent event) {
        update(event.roomId(), room -> room.toBuilder()
                .status(RoomStatus.CANCELLED)
                .updatedAt(event.cancelledAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(RoomEvent.RoomCompletedEvent event) {
        update(event.roomId(), room -> room.toBuilder()
                .status(RoomStatus.COMPLETED)
                .gameId(event.gameId())
                .updatedAt(event.completedAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(RoomEvent.RoomFailedEvent event) {
        update(event.roomId(), room -> room.toBuilder()
                .status(RoomStatus.FAILED)
                .updatedAt(event.failedAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(RoomEvent.RoomExpiredEvent event) {
        update(event.roomId(), room -> room.toBuilder()
                .status(RoomStatus.EXPIRED)
                .updatedAt(event.expiredAt())
                .build());
    }

    @EventHandler
    @Transactional
    public void on(RoomEvent.RoomPurgedEvent event) {
        roomRepositoryPort.deleteById(event.roomId());
    }

    private void update(String roomId, UnaryOperator<Room> transform) {
        roomRepositoryPort.findById(roomId).ifPresent(room -> save(transform.apply(room)));
    }

    /** Toute écriture d'une salle change les salles de ses joueurs (vue vivante {@code GetMyRooms}). */
    private void save(Room room) {
        roomRepositoryPort.save(room);
        myRooms.changed(room);
    }
}
