package com.joutio.matchmaking.application.handler.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.joutio.matchmaking.domain.command.RoomCommand;
import com.joutio.matchmaking.domain.model.RoomLeaveReason;
import com.joutio.profile.domain.event.PresenceEvent.PlayerWentOfflineEvent;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

class OfflinePlayerRoomHandlerTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final CommandGateway commandGateway = mock(CommandGateway.class);
    private final OfflinePlayerRoomHandler handler =
            new OfflinePlayerRoomHandler(jdbcTemplate, commandGateway);

    @Test
    void offlinePlayer_leavesEveryWaitingRoomWhereHeIsPresent() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("p1"), eq("p1")))
                .thenReturn(List.of("r1", "r2"));
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));

        handler.on(new PlayerWentOfflineEvent("p1", Instant.now()));

        verify(commandGateway)
                .send(new RoomCommand.LeaveRoomCommand("r1", "p1", null, RoomLeaveReason.OFFLINE));
        verify(commandGateway)
                .send(new RoomCommand.LeaveRoomCommand("r2", "p1", null, RoomLeaveReason.OFFLINE));
    }

    @Test
    void replayedOldEvent_isIgnored() {
        handler.on(new PlayerWentOfflineEvent("p1", Instant.now().minusSeconds(3600)));

        verify(commandGateway, never()).send(any());
    }
}
