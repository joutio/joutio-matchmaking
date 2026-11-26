package com.joutio.matchmaking.application.service;

import com.joutio.matchmaking.domain.model.MyRooms;
import com.joutio.matchmaking.domain.model.Room;
import com.joutio.matchmaking.domain.port.out.MyRoomsVersionPort;
import com.joutio.matchmaking.domain.port.out.RoomRepositoryPort;
import com.joutio.matchmaking.domain.query.RoomQuery.GetMyRooms;
import com.joutio.microservice.core.domain.constant.JoutioConstants;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * Salles d'un joueur en vue vivante ({@link GetMyRooms}) : lecture, et émission à chaque écriture d'une salle.
 *
 * <p>{@link #changed(Room)} s'appelle dans la projection, après l'écriture : la version de chaque joueur de la salle
 * est incrémentée dans la même transaction, puis ses salles sont relues (la ligne de version reste verrouillée, donc
 * deux segments qui écrivent pour le même joueur se suivent). L'émission part au commit de l'unité de travail.</p>
 */
@Service
public class MyRoomsService {

    private final RoomRepositoryPort rooms;
    private final MyRoomsVersionPort versions;
    private final QueryUpdateEmitter emitter;

    public MyRoomsService(RoomRepositoryPort rooms, MyRoomsVersionPort versions, QueryUpdateEmitter emitter) {
        this.rooms = rooms;
        this.versions = versions;
        this.emitter = emitter;
    }

    public MyRooms read(String playerId) {
        return new MyRooms(playerId, versions.current(playerId), rooms.findActiveByPlayerId(playerId));
    }

    /** Une salle vient d'être écrite : nouvelles salles de chacun de ses joueurs humains. */
    public void changed(Room room) {
        Stream.of(room.initiatorId(), room.opponentId(), room.participantId())
                .filter(Objects::nonNull)
                .filter(playerId -> !JoutioConstants.SYSTEM_USER_ID.equals(playerId))
                .distinct()
                .forEach(playerId -> {
                    MyRooms mine = new MyRooms(playerId, versions.increment(playerId),
                            rooms.findActiveByPlayerId(playerId));
                    emitter.emit(GetMyRooms.class, query -> query.playerId().equals(playerId), mine);
                });
    }
}
