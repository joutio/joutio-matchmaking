package com.joutio.matchmaking.application.handler.query;

import com.joutio.matchmaking.application.service.MyRoomsService;
import com.joutio.matchmaking.domain.exception.RoomExceptions;
import com.joutio.matchmaking.domain.model.MyRooms;
import com.joutio.matchmaking.domain.model.Room;
import com.joutio.matchmaking.domain.port.out.RoomEventStorePort;
import com.joutio.matchmaking.domain.port.out.RoomRepositoryPort;
import com.joutio.matchmaking.domain.query.RoomQuery;
import com.joutio.microservice.core.domain.model.event.EventEnvelope;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RoomQueryHandler {

    private final RoomRepositoryPort roomRepositoryPort;
    private final RoomEventStorePort roomEventStorePort;
    private final MyRoomsService myRooms;

    public RoomQueryHandler(RoomRepositoryPort roomRepositoryPort,
                            RoomEventStorePort roomEventStorePort,
                            MyRoomsService myRooms) {
        this.roomRepositoryPort = roomRepositoryPort;
        this.roomEventStorePort = roomEventStorePort;
        this.myRooms = myRooms;
    }

    @QueryHandler
    public Room handle(RoomQuery.GetRoomById query) {
        return roomRepositoryPort.findById(query.roomId())
                .orElseThrow(() -> new RoomExceptions.RoomNotFoundProblem(query.roomId()));
    }

    @QueryHandler
    public List<Room> handle(RoomQuery.GetMyOpenRooms query) {
        return roomRepositoryPort.findActiveByPlayerId(query.playerId());
    }

    @QueryHandler
    public MyRooms handle(RoomQuery.GetMyRooms query) {
        return myRooms.read(query.playerId());
    }

    @QueryHandler
    public List<EventEnvelope> handle(RoomQuery.GetRoomEventsQuery query) {
        return roomEventStorePort.findEventEnvelopesByRoomId(query.roomId());
    }
}
