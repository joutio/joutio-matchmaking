package com.joutio.matchmaking.infrastructure.in.scheduler;

import com.joutio.matchmaking.application.saga.RoomFailureRecorder;
import com.joutio.matchmaking.domain.command.RoomCommand;
import com.joutio.matchmaking.domain.model.RoomFailureReason;
import com.joutio.matchmaking.domain.port.out.RoomRepositoryPort;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RoomSweeperTest {

    private final RoomRepositoryPort repository = mock(RoomRepositoryPort.class);
    private final CommandGateway commandGateway = mock(CommandGateway.class);
    private final RoomFailureRecorder failureRecorder = mock(RoomFailureRecorder.class);
    private final RoomSweeper sweeper = new RoomSweeper(repository, commandGateway, failureRecorder);

    @Test
    void sweepFailsStalledRoomsAndPurgesForgottenTerminalRooms() {
        when(repository.findStalledPreparationIdsBefore(any(Instant.class))).thenReturn(List.of("stalled"));
        when(repository.findTerminalIdsBefore(any(Instant.class))).thenReturn(List.of("terminal"));
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));

        sweeper.sweep();

        ArgumentCaptor<Object> commands = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway, times(2)).send(commands.capture());
        assertThat(commands.getAllValues()).containsExactly(
                new RoomCommand.FailRoomCommand("stalled", RoomFailureReason.ROOM_PREPARATION_STALLED),
                new RoomCommand.PurgeRoomCommand("terminal"));
        verify(failureRecorder).record("stalled", RoomFailureReason.ROOM_PREPARATION_STALLED, "step=sweeper", null);
    }

    @Test
    void sweepIsNoOpWithoutForgottenRooms() {
        when(repository.findStalledPreparationIdsBefore(any(Instant.class))).thenReturn(List.of());
        when(repository.findTerminalIdsBefore(any(Instant.class))).thenReturn(List.of());

        sweeper.sweep();

        verifyNoInteractions(commandGateway);
    }
}
