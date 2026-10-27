package com.joutio.matchmaking.application.handler.query;

import com.joutio.matchmaking.domain.exception.RoomExceptions;
import com.joutio.matchmaking.domain.model.Room;
import com.joutio.matchmaking.domain.port.out.RoomEventStorePort;
import com.joutio.matchmaking.domain.port.out.RoomRepositoryPort;
import com.joutio.matchmaking.domain.query.RoomQuery;
import com.joutio.microservice.core.domain.model.notification.EventEnvelope;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RoomQueryHandler {

    private final RoomRepositoryPort roomRepositoryPort;
    private final RoomEventStorePort roomEventStorePort;

    public RoomQueryHandler(RoomRepositoryPort roomRepositoryPort,
                            RoomEventStorePort roomEventStorePort) {
        this.roomRepositoryPort = roomRepositoryPort;
        this.roomEventStorePort = roomEventStorePort;
    }

    @QueryHandler
    public Room handle(RoomQuery.GetRoomById query) {
        return roomRepositoryPort.findById(query.roomId())
                .orElseThrow(() -> new RoomExceptions.RoomNotFoundProblem(query.roomId()));
    }

    @QueryHandler
    public List<Room> handle(RoomQuery.GetMyOpenRooms query) {
        return roomRepositoryPort.findWaitingByPlayerId(query.playerId());
    }

    @QueryHandler
    public List<EventEnvelope> handle(RoomQuery.GetRoomEventsQuery query) {
        return roomEventStorePort.findEventEnvelopesByRoomId(query.roomId());
    }
}
