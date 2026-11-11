package com.joutio.matchmaking.application.handler.event;

import com.joutio.matchmaking.domain.command.RoomCommand;
import com.joutio.matchmaking.domain.model.RoomLeaveReason;
import com.joutio.profile.domain.event.PresenceEvent.PlayerWentOfflineEvent;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Filet de sécurité de la présence en salle : un joueur hors ligne (plus aucune session temps
 * réel, y compris quand l'instance du BFF qui portait sa connexion est tombée sans prévenir)
 * n'est plus présent dans les salles qui l'attendent encore. Le BFF couvre les cas ordinaires
 * (écran quitté, connexion fermée) ; ce gestionnaire constate un fait système, il ne simule
 * aucune action du joueur. Une préparation engagée n'est pas interrompue (l'agrégat l'ignore).
 */
@Component
@ProcessingGroup("offline-room-presence")
public class OfflinePlayerRoomHandler {

    private static final Logger logger = LoggerFactory.getLogger(OfflinePlayerRoomHandler.class);

    /** Un rejeu d'historique (nouveau processeur, reset) ne doit pas faire sortir les présents. */
    static final Duration MAX_EVENT_AGE = Duration.ofMinutes(1);

    private final JdbcTemplate jdbcTemplate;
    private final CommandGateway commandGateway;

    public OfflinePlayerRoomHandler(JdbcTemplate jdbcTemplate, CommandGateway commandGateway) {
        this.jdbcTemplate = jdbcTemplate;
        this.commandGateway = commandGateway;
    }

    @EventHandler
    public void on(PlayerWentOfflineEvent event) {
        if (event.lastSeenAt() != null
                && event.lastSeenAt().isBefore(Instant.now().minus(MAX_EVENT_AGE))) {
            return;
        }
        String userId = event.userId();
        List<String> roomIds =
                jdbcTemplate.queryForList(
                        """
                        SELECT room_id FROM room_entry
                        WHERE status = 'WAITING_PLAYERS'
                          AND ((initiator_id = ? AND initiator_present)
                            OR (participant_id = ? AND participant_present))
                        """,
                        String.class,
                        userId,
                        userId);
        for (String roomId : roomIds) {
            logger.info("Offline player leaves room: roomId={}, playerId={}", roomId, userId);
            commandGateway
                    .send(
                            new RoomCommand.LeaveRoomCommand(
                                    roomId, userId, null, RoomLeaveReason.OFFLINE))
                    .exceptionally(
                            error -> {
                                logger.warn(
                                        "Offline room leave failed: roomId={}, playerId={}: {}",
                                        roomId,
                                        userId,
                                        error.getMessage());
                                return null;
                            });
        }
    }
}
