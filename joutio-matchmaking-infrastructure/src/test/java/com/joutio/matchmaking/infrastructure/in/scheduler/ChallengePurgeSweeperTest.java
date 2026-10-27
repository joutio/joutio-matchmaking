package com.joutio.matchmaking.infrastructure.in.scheduler;

import com.joutio.matchmaking.domain.command.ChallengeCommand;
import com.joutio.matchmaking.domain.port.out.ChallengeRepositoryPort;
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

class ChallengePurgeSweeperTest {

    private final ChallengeRepositoryPort repository = mock(ChallengeRepositoryPort.class);
    private final CommandGateway commandGateway = mock(CommandGateway.class);
    private final ChallengePurgeSweeper sweeper = new ChallengePurgeSweeper(repository, commandGateway);

    @Test
    void sweepPurgesTerminalChallenges() {
        when(repository.findTerminalIdsBefore(any(Instant.class))).thenReturn(List.of("c1", "c2"));
        when(commandGateway.send(any())).thenReturn(CompletableFuture.completedFuture(null));

        sweeper.sweep();

        ArgumentCaptor<Object> commands = ArgumentCaptor.forClass(Object.class);
        verify(commandGateway, times(2)).send(commands.capture());
        assertThat(commands.getAllValues()).allSatisfy(command ->
                assertThat(command).isInstanceOf(ChallengeCommand.PurgeChallengeCommand.class));
    }

    @Test
    void sweepIsNoOpWithoutTerminalChallenges() {
        when(repository.findTerminalIdsBefore(any(Instant.class))).thenReturn(List.of());

        sweeper.sweep();

        verifyNoInteractions(commandGateway);
    }
}
