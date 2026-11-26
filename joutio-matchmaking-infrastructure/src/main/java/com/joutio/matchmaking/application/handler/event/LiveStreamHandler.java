package com.joutio.matchmaking.application.handler.event;

import com.joutio.matchmaking.domain.event.MatchmakingEvent;
import com.joutio.matchmaking.domain.event.RoomEvent;
import com.joutio.matchmaking.domain.query.MatchmakingQuery.GetMatchmakingEventsQuery;
import com.joutio.matchmaking.domain.query.RoomQuery.GetRoomEventsQuery;
import com.joutio.microservice.core.domain.model.event.EventEnvelope;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.springframework.stereotype.Component;

/**
 * Flux des salles et des tickets en vue vivante : chaque événement est émis aux abonnés de {@link GetRoomEventsQuery}
 * ou de {@link GetMatchmakingEventsQuery} du même agrégat, sous la forme de l'historique ({@link EventEnvelope},
 * versionné par sa séquence).
 *
 * <p>Groupe local ({@code joutio.axon.local-groups}) : le handler tourne dans l'unité de travail de la commande, et
 * l'émission part à son commit, sans passer par Kafka.</p>
 */
@Component
@ProcessingGroup("matchmaking-live")
public class LiveStreamHandler {

    private final QueryUpdateEmitter emitter;

    public LiveStreamHandler(QueryUpdateEmitter emitter) {
        this.emitter = emitter;
    }

    @EventHandler
    public void on(RoomEvent event, DomainEventMessage<?> message) {
        emitter.emit(GetRoomEventsQuery.class, query -> query.roomId().equals(event.roomId()), envelope(event, message));
    }

    @EventHandler
    public void on(MatchmakingEvent event, DomainEventMessage<?> message) {
        emitter.emit(GetMatchmakingEventsQuery.class,
                query -> query.matchmakingId().equals(event.matchmakingId()), envelope(event, message));
    }

    private static EventEnvelope envelope(Object event, DomainEventMessage<?> message) {
        return EventEnvelope.of(message.getAggregateIdentifier(), message.getSequenceNumber(), message.getTimestamp(),
                event);
    }
}
