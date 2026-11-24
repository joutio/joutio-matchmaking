package com.joutio.matchmaking.application.handler.event;

import com.joutio.matchmaking.domain.command.RoomCommand;
import com.joutio.matchmaking.domain.model.Room;
import com.joutio.matchmaking.domain.model.RoomCancelReason;
import com.joutio.matchmaking.domain.port.out.RoomRepositoryPort;
import com.joutio.social.domain.event.UserBlockEvent;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserBlockRoomHandlerTest {

    private final RoomRepositoryPort rooms = mock(RoomRepositoryPort.class);
    private final CommandGateway commandGateway = mock(CommandGateway.class);
    private final UserBlockRoomHandler handler = new UserBlockRoomHandler(rooms, commandGateway);

    @Test
    void block_cancelsEveryOpenRoomBetweenTheTwoPlayers_onBehalfOfItsInitiator() {
        when(rooms.findOpenBetween("alice", "bob")).thenReturn(List.of(
                Room.builder().roomId("sent").initiatorId("alice").opponentId("bob").build(),
                Room.builder().roomId("received").initiatorId("bob").opponentId("alice").build()));

        handler.on(blocked("alice", "bob"));

        verify(commandGateway).send(new RoomCommand.CancelRoomCommand("sent", "alice", RoomCancelReason.PLAYER_BLOCKED));
        verify(commandGateway).send(new RoomCommand.CancelRoomCommand("received", "bob", RoomCancelReason.PLAYER_BLOCKED));
    }

    @Test
    void block_withoutSharedRoom_sendsNothing() {
        when(rooms.findOpenBetween("alice", "bob")).thenReturn(List.of());

        handler.on(blocked("alice", "bob"));

        verifyNoInteractions(commandGateway);
    }

    private static UserBlockEvent.UserBlockedEvent blocked(String blockerId, String blockedId) {
        return new UserBlockEvent.UserBlockedEvent(blockerId + ":" + blockedId, blockerId, blockedId, java.time.Instant.now());
    }
}
