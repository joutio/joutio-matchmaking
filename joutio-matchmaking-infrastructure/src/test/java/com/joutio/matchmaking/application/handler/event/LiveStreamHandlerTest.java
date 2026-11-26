package com.joutio.matchmaking.application.handler.event;

import com.joutio.matchmaking.domain.event.MatchmakingEvent.MatchmakingCancelledEvent;
import com.joutio.matchmaking.domain.event.RoomEvent.RoomCancelledEvent;
import com.joutio.matchmaking.domain.query.MatchmakingQuery.GetMatchmakingEventsQuery;
import com.joutio.matchmaking.domain.query.RoomQuery.GetRoomEventsQuery;
import com.joutio.microservice.core.domain.model.event.EventEnvelope;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.queryhandling.QueryUpdateEmitter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LiveStreamHandlerTest {

    private static final Instant AT = Instant.parse("2026-11-26T10:00:00Z");

    private final QueryUpdateEmitter emitter = mock(QueryUpdateEmitter.class);
    private final LiveStreamHandler handler = new LiveStreamHandler(emitter);

    @Test
    @SuppressWarnings("unchecked")
    void emits_a_room_event_as_its_history_envelope_to_the_subscribers_of_the_same_room() {
        RoomCancelledEvent event = new RoomCancelledEvent("r1", "u1", null, Set.of("u1"), AT);

        handler.on(event, new GenericDomainEventMessage<>("RoomAggregate", "r1", 3, event));

        ArgumentCaptor<Predicate<GetRoomEventsQuery>> filter = ArgumentCaptor.forClass(Predicate.class);
        ArgumentCaptor<EventEnvelope> envelope = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(emitter).emit(eq(GetRoomEventsQuery.class), filter.capture(), envelope.capture());
        assertTrue(filter.getValue().test(new GetRoomEventsQuery("r1")));
        assertFalse(filter.getValue().test(new GetRoomEventsQuery("r2")));
        assertEquals(3, envelope.getValue().sequenceNumber());
        assertEquals(event, envelope.getValue().payload());
    }

    @Test
    @SuppressWarnings("unchecked")
    void emits_a_ticket_event_to_the_subscribers_of_the_same_ticket() {
        MatchmakingCancelledEvent event = new MatchmakingCancelledEvent("t1", "CANCELLED", AT);

        handler.on(event, new GenericDomainEventMessage<>("MatchmakingAggregate", "t1", 1, event));

        ArgumentCaptor<Predicate<GetMatchmakingEventsQuery>> filter = ArgumentCaptor.forClass(Predicate.class);
        ArgumentCaptor<EventEnvelope> envelope = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(emitter).emit(eq(GetMatchmakingEventsQuery.class), filter.capture(), envelope.capture());
        assertTrue(filter.getValue().test(new GetMatchmakingEventsQuery("t1")));
        assertFalse(filter.getValue().test(new GetMatchmakingEventsQuery("t2")));
        assertEquals("t1", envelope.getValue().aggregateId());
        assertEquals(1, envelope.getValue().sequenceNumber());
    }
}
