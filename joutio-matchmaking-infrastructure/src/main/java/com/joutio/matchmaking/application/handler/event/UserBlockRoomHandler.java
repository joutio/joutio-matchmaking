package com.joutio.matchmaking.application.handler.event;

import com.joutio.matchmaking.domain.command.RoomCommand;
import com.joutio.matchmaking.domain.model.Room;
import com.joutio.matchmaking.domain.model.RoomCancelReason;
import com.joutio.matchmaking.domain.port.out.RoomRepositoryPort;
import com.joutio.social.domain.event.UserBlockEvent;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Un blocage (social) met fin aux salles ouvertes entre les deux joueurs, dans les deux sens : défis en attente,
 * salles acceptées pas encore lancées et salons partagés que l'autre a déjà rejoints. Chacune est annulée au nom de
 * son initiateur.
 */
@Component
@ProcessingGroup("user-block-rooms")
public class UserBlockRoomHandler {

    private static final Logger logger = LoggerFactory.getLogger(UserBlockRoomHandler.class);

    private final RoomRepositoryPort rooms;
    private final CommandGateway commandGateway;

    public UserBlockRoomHandler(RoomRepositoryPort rooms, CommandGateway commandGateway) {
        this.rooms = rooms;
        this.commandGateway = commandGateway;
    }

    @EventHandler
    public void on(UserBlockEvent.UserBlockedEvent event) {
        for (Room room : rooms.findOpenBetween(event.blockerId(), event.blockedId())) {
            logger.info("Room cancelled by a block: roomId={}", room.roomId());
            commandGateway.send(new RoomCommand.CancelRoomCommand(
                    room.roomId(), room.initiatorId(), RoomCancelReason.PLAYER_BLOCKED));
        }
    }
}
