package com.joutio.matchmaking.domain.port.out;

import com.joutio.microservice.core.domain.model.event.EventEnvelope;

import java.util.List;

public interface MatchmakingEventStorePort {

    List<EventEnvelope> findEventEnvelopesByMatchmakingId(String matchmakingId);
}
