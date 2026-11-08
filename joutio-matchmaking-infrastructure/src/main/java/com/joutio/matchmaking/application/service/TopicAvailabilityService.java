package com.joutio.matchmaking.application.service;

import com.joutio.matchmaking.domain.port.out.TopicAvailabilityPort;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import com.joutio.theme.domain.model.Topic;
import com.joutio.theme.domain.model.TopicStatus;
import com.joutio.theme.domain.query.TopicQuery;

import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class TopicAvailabilityService implements TopicAvailabilityPort {
    private final QueryGateway queries;

    public TopicAvailabilityService(QueryGateway queries) {
        this.queries = queries;
    }

    public CompletableFuture<Boolean> playable(String topicId) {
        return queries.query(
                        new TopicQuery.GetTopicByIdQuery(topicId),
                        QueryResponseTypes.instanceOf(Topic.class))
                .thenApply(t -> t.status() == TopicStatus.PUBLISHED);
    }
}
