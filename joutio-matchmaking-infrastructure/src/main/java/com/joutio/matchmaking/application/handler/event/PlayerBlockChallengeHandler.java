package com.joutio.matchmaking.application.handler.event;

import com.joutio.matchmaking.domain.command.ChallengeCommand;
import com.joutio.matchmaking.domain.model.Challenge;
import com.joutio.matchmaking.domain.port.out.ChallengeRepositoryPort;
import com.joutio.social.domain.event.PlayerBlockEvent;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Un blocage (social) met fin aux défis en attente entre les deux joueurs, dans les deux sens : chacun est annulé au
 * nom de son lanceur (l'invitation disparaît de l'inbox de l'invité).
 */
@Component
@ProcessingGroup("player-block-challenges")
public class PlayerBlockChallengeHandler {

    private static final Logger logger = LoggerFactory.getLogger(PlayerBlockChallengeHandler.class);

    private final ChallengeRepositoryPort challenges;
    private final CommandGateway commandGateway;

    public PlayerBlockChallengeHandler(ChallengeRepositoryPort challenges, CommandGateway commandGateway) {
        this.challenges = challenges;
        this.commandGateway = commandGateway;
    }

    @EventHandler
    public void on(PlayerBlockEvent.PlayerBlockedEvent event) {
        for (Challenge challenge : challenges.findPendingByPlayerId(event.blockerId())) {
            if (!event.blockedId().equals(challenge.challengerId()) && !event.blockedId().equals(challenge.opponentId())) {
                continue;
            }
            logger.info("Challenge cancelled by a block: challengeId={}", challenge.challengeId());
            commandGateway.send(new ChallengeCommand.CancelChallengeCommand(
                    challenge.challengeId(), challenge.challengerId()));
        }
    }
}
