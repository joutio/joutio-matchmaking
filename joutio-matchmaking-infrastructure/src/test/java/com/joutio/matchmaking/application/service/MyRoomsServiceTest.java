package com.joutio.matchmaking.application.service;

import com.joutio.matchmaking.domain.model.MyRooms;
import com.joutio.matchmaking.domain.model.Room;
import com.joutio.matchmaking.domain.port.out.MyRoomsVersionPort;
import com.joutio.matchmaking.domain.port.out.RoomRepositoryPort;
import com.joutio.matchmaking.domain.query.RoomQuery.GetMyRooms;
import com.joutio.microservice.core.domain.constant.JoutioConstants;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MyRoomsServiceTest {

    private final RoomRepositoryPort rooms = mock(RoomRepositoryPort.class);
    private final MyRoomsVersionPort versions = mock(MyRoomsVersionPort.class);
    private final QueryUpdateEmitter emitter = mock(QueryUpdateEmitter.class);
    private final MyRoomsService service = new MyRoomsService(rooms, versions, emitter);

    @Test
    void reads_the_open_rooms_of_the_player_with_the_current_version() {
        Room room = Room.builder().roomId("r1").initiatorId("u1").build();
        when(rooms.findActiveByPlayerId("u1")).thenReturn(List.of(room));
        when(versions.current("u1")).thenReturn(4L);

        assertEquals(new MyRooms("u1", 4, List.of(room)), service.read("u1"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void emits_the_new_rooms_of_each_human_player_of_the_room_with_a_new_version() {
        Room room = Room.builder().roomId("r1").initiatorId("u1").opponentId("u2").participantId("u2").build();
        when(rooms.findActiveByPlayerId("u1")).thenReturn(List.of(room));
        when(rooms.findActiveByPlayerId("u2")).thenReturn(List.of());
        when(versions.increment("u1")).thenReturn(7L);
        when(versions.increment("u2")).thenReturn(2L);

        service.changed(room);

        ArgumentCaptor<Predicate<GetMyRooms>> filter = ArgumentCaptor.forClass(Predicate.class);
        ArgumentCaptor<MyRooms> mine = ArgumentCaptor.forClass(MyRooms.class);
        verify(emitter, times(2)).emit(eq(GetMyRooms.class), filter.capture(), mine.capture());
        assertEquals(List.of(new MyRooms("u1", 7, List.of(room)), new MyRooms("u2", 2, List.of())), mine.getAllValues());
        assertTrue(filter.getAllValues().get(0).test(new GetMyRooms("u1")));
        assertFalse(filter.getAllValues().get(0).test(new GetMyRooms("u2")));
    }

    @Test
    void never_versions_the_bot() {
        Room room = Room.builder().roomId("r1").initiatorId("u1").participantId(JoutioConstants.SYSTEM_USER_ID).build();

        service.changed(room);

        verify(versions, never()).increment(JoutioConstants.SYSTEM_USER_ID);
        verify(versions).increment("u1");
    }
}
