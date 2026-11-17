package com.joutio.matchmaking.infrastructure.in.scheduler;

import com.joutio.matchmaking.domain.command.MatchmakingCommand;
import com.joutio.matchmaking.domain.port.out.MatchmakingRepositoryPort;
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

class MatchmakingSweeperTest {

    private final MatchmakingRepositoryPort repository = mock(MatchmakingRepositoryPort.class);
    private final CommandGateway commandGateway = mock(CommandGateway.class);
    private final MatchmakingSweeper sweeper = new MatchmakingSweeper(repository, commandGateway);

    @Test
    void sweepExpiresOverdueSearchesAndPurgesForgottenTickets() {
        when(repository.findSearchingIdsWithDeadlineBefore(any(Instant.class))).thenReturn(List.of("overdue"));
        when(repository.findTerminalIdsBefore(any(Instant.class))).thenReturn(List.of("terminal"));
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));

        sweeper.sweep();

        ArgumentCaptor<Object> commands = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway, times(2)).send(commands.capture());
        assertThat(commands.getAllValues()).containsExactly(
                new MatchmakingCommand.ExpireMatchmakingCommand("overdue"),
                new MatchmakingCommand.PurgeMatchmakingCommand("terminal"));
    }

    @Test
    void sweepIsNoOpWithoutForgottenTickets() {
        when(repository.findSearchingIdsWithDeadlineBefore(any(Instant.class))).thenReturn(List.of());
        when(repository.findTerminalIdsBefore(any(Instant.class))).thenReturn(List.of());

        sweeper.sweep();

        verifyNoInteractions(commandGateway);
    }
}
