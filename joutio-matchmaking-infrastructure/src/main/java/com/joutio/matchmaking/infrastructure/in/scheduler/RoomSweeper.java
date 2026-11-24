package com.joutio.matchmaking.infrastructure.in.scheduler;

import com.joutio.matchmaking.application.saga.RoomFailureRecorder;
import com.joutio.matchmaking.domain.command.RoomCommand;
import com.joutio.matchmaking.domain.model.RoomDeadline;
import com.joutio.matchmaking.domain.model.RoomFailureReason;
import com.joutio.matchmaking.domain.port.out.RoomRepositoryPort;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Filet des salles dont la deadline en mémoire a été perdue avec son pod (rollout, arrêt) :
 * <ul>
 *   <li>invitation ou salle en attente dont l'échéance est passée depuis plus de
 *       {@link RoomDeadline#ROOM_SWEEP_GRACE} → {@code ExpireRoomCommand} ;</li>
 *   <li>salle bloquée en préparation au-delà de {@link RoomDeadline#ROOM_PREPARATION_STALL} →
 *       {@code FailRoomCommand(ROOM_PREPARATION_STALLED)} ;</li>
 *   <li>salle terminale non purgée après rétention + {@link RoomDeadline#ROOM_SWEEP_GRACE} →
 *       {@code PurgeRoomCommand}.</li>
 * </ul>
 * Commandes idempotentes : une salle déjà close ou supprimée est ignorée. Planifié par
 * {@link SweepTaskConfiguration}.
 */
@Component
public class RoomSweeper {

    private static final Logger logger = LoggerFactory.getLogger(RoomSweeper.class);

    private final RoomRepositoryPort roomRepositoryPort;
    private final CommandGateway commandGateway;
    private final RoomFailureRecorder failureRecorder;

    public RoomSweeper(RoomRepositoryPort roomRepositoryPort,
                       CommandGateway commandGateway,
                       RoomFailureRecorder failureRecorder) {
        this.roomRepositoryPort = roomRepositoryPort;
        this.commandGateway = commandGateway;
        this.failureRecorder = failureRecorder;
    }

    public void sweep() {
        Instant now = Instant.now();
        roomRepositoryPort.findOpenIdsExpiredBefore(now.minus(RoomDeadline.ROOM_SWEEP_GRACE))
                .forEach(roomId -> send(new RoomCommand.ExpireRoomCommand(roomId), roomId));
        roomRepositoryPort.findStalledPreparationIdsBefore(now.minus(RoomDeadline.ROOM_PREPARATION_STALL))
                .forEach(roomId -> {
                    failureRecorder.record(roomId, RoomFailureReason.ROOM_PREPARATION_STALLED, "step=sweeper", null);
                    send(new RoomCommand.FailRoomCommand(roomId, RoomFailureReason.ROOM_PREPARATION_STALLED), roomId);
                });
        roomRepositoryPort.findTerminalIdsBefore(now
                        .minus(RoomDeadline.ROOM_RETENTION_DURATION)
                        .minus(RoomDeadline.ROOM_SWEEP_GRACE))
                .forEach(roomId -> send(new RoomCommand.PurgeRoomCommand(roomId), roomId));
    }

    private void send(RoomCommand command, String roomId) {
        commandGateway.send(command).exceptionally(error -> {
            logger.debug("Balayage salle ignoré: roomId={} ({})", roomId, error.getMessage());
            return null;
        });
    }
}
