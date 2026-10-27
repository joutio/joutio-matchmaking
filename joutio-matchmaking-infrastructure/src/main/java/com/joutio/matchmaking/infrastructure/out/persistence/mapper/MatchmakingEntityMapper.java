package com.joutio.matchmaking.infrastructure.out.persistence.mapper;

import com.joutio.matchmaking.domain.model.Matchmaking;
import com.joutio.matchmaking.domain.model.MatchmakingCandidate;
import com.joutio.matchmaking.infrastructure.out.persistence.entity.MatchmakingEntity;
import com.joutio.microservice.core.domain.model.i18n.Language;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public final class MatchmakingEntityMapper {

    private MatchmakingEntityMapper() {
    }

    public static String encodeLanguages(Set<Language> languages) {
        if (languages == null || languages.isEmpty()) {
            return "";
        }
        return languages.stream().map(Language::code).sorted().collect(Collectors.joining(","));
    }

    public static Set<Language> decodeLanguages(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .filter(code -> !code.isBlank())
                .map(Language::fromCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static MatchmakingCandidate toCandidate(MatchmakingEntity entity) {
        return new MatchmakingCandidate(
                entity.getMatchmakingId(),
                entity.getPlayerId(),
                decodeLanguages(entity.getLanguages()),
                entity.getCreatedAt());
    }

    public static Matchmaking toDomain(MatchmakingEntity entity) {
        return Matchmaking.builder()
                .matchmakingId(entity.getMatchmakingId())
                .playerId(entity.getPlayerId())
                .topicId(entity.getTopicId())
                .rating(entity.getRating())
                .languages(decodeLanguages(entity.getLanguages()))
                .opponentId(entity.getOpponentId())
                .roomId(entity.getRoomId())
                .vsBot(entity.isVsBot())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .deadlineAt(entity.getDeadlineAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
