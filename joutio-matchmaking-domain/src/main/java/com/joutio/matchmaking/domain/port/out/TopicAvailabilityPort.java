package com.joutio.matchmaking.domain.port.out;

import java.util.concurrent.CompletableFuture;

public interface TopicAvailabilityPort {
    CompletableFuture<Boolean> playable(String topicId);
}
