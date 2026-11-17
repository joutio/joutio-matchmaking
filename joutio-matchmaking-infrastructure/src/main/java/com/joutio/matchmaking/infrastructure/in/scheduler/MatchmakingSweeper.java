package com.joutio.matchmaking.infrastructure.in.scheduler;

import com.joutio.matchmaking.domain.command.MatchmakingCommand;
import com.joutio.matchmaking.domain.model.MatchmakingDeadline;
import com.joutio.matchmaking.domain.port.out.MatchmakingRepositoryPort;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Filet des recherches dont la deadline en mémoire a été perdue avec son pod :
 * <ul>
 *   <li>recherche encore {@code SEARCHING} après son échéance +
 *       {@link MatchmakingDeadline#MATCHMAKING_EXPIRY_GRACE} → {@code ExpireMatchmakingCommand} ;</li>
 *   <li>ticket terminal non purgé après rétention + {@link MatchmakingDeadline#MATCHMAKING_SWEEP_GRACE}
 *       → {@code PurgeMatchmakingCommand}.</li>
 * </ul>
 * Commandes idempotentes. Planifié par {@link SweepTaskConfiguration}.
 */
@Component
public class MatchmakingSweeper {

    private static final Logger logger = LoggerFactory.getLogger(MatchmakingSweeper.class);

    private final MatchmakingRepositoryPort matchmakingRepositoryPort;
    private final CommandGateway commandGateway;

    public MatchmakingSweeper(MatchmakingRepositoryPort matchmakingRepositoryPort, CommandGateway commandGateway) {
        this.matchmakingRepositoryPort = matchmakingRepositoryPort;
        this.commandGateway = commandGateway;
    }

    public void sweep() {
        Instant now = Instant.now();
        matchmakingRepositoryPort
                .findSearchingIdsWithDeadlineBefore(now.minus(MatchmakingDeadline.MATCHMAKING_EXPIRY_GRACE))
                .forEach(matchmakingId -> {
                    logger.warn("Recherche restée ouverte après échéance, expiration forcée: matchmakingId={}",
                            matchmakingId);
                    send(new MatchmakingCommand.ExpireMatchmakingCommand(matchmakingId), matchmakingId);
                });
        matchmakingRepositoryPort.findTerminalIdsBefore(now
                        .minus(MatchmakingDeadline.MATCHMAKING_RETENTION_DURATION)
                        .minus(MatchmakingDeadline.MATCHMAKING_SWEEP_GRACE))
                .forEach(matchmakingId -> send(new MatchmakingCommand.PurgeMatchmakingCommand(matchmakingId),
                        matchmakingId));
    }

    private void send(MatchmakingCommand command, String matchmakingId) {
        commandGateway.send(command).exceptionally(error -> {
            logger.debug("Balayage recherche ignoré: matchmakingId={} ({})", matchmakingId, error.getMessage());
            return null;
        });
    }
}
