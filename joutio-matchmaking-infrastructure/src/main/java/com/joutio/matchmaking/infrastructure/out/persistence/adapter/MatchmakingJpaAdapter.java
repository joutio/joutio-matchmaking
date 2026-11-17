package com.joutio.matchmaking.infrastructure.out.persistence.adapter;

import com.joutio.matchmaking.domain.model.Matchmaking;
import com.joutio.matchmaking.domain.model.MatchmakingCandidate;
import com.joutio.matchmaking.domain.port.out.MatchmakingPoolPort;
import com.joutio.matchmaking.domain.port.out.MatchmakingRepositoryPort;
import com.joutio.matchmaking.infrastructure.out.persistence.entity.MatchmakingEntity;
import com.joutio.matchmaking.infrastructure.out.persistence.mapper.MatchmakingEntityMapper;
import com.joutio.matchmaking.infrastructure.out.persistence.repository.MatchmakingClaimJpaRepository;
import com.joutio.matchmaking.infrastructure.out.persistence.repository.MatchmakingJpaRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Adaptateur JPA du pool d'appariement et du read model « ticket ». Le pool correspond aux
 * recherches {@code SEARCHING} sans ligne dans {@code matchmaking_claim}.
 */
@Component
public class MatchmakingJpaAdapter implements MatchmakingPoolPort, MatchmakingRepositoryPort {

    private final MatchmakingJpaRepository repository;
    private final MatchmakingClaimJpaRepository claimRepository;
    private final TransactionTemplate transactionTemplate;

    public MatchmakingJpaAdapter(MatchmakingJpaRepository repository,
                                 MatchmakingClaimJpaRepository claimRepository,
                                 TransactionTemplate transactionTemplate) {
        this.repository = repository;
        this.claimRepository = claimRepository;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MatchmakingCandidate> candidates(String topicId, int rating, int ratingWindow,
                                                 String excludePlayerId, int limit) {
        return repository
                .findCandidates(topicId, excludePlayerId, rating, rating - ratingWindow, rating + ratingWindow, Limit.of(limit))
                .stream()
                .map(MatchmakingEntityMapper::toCandidate)
                .toList();
    }

    /**
     * Les deux insertions partagent une transaction : si une seule passe (l'autre recherche est
     * déjà réclamée), elle est annulée et la paire reste libre pour un autre appariement.
     */
    @Override
    public boolean claimPair(String matchmakingId, String candidateMatchmakingId) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            int inserted = claimRepository.insertPair(matchmakingId, candidateMatchmakingId, Instant.now());
            if (inserted == 2) {
                return true;
            }
            status.setRollbackOnly();
            return false;
        }));
    }

    @Override
    @Transactional
    public void save(Matchmaking matchmaking) {
        MatchmakingEntity entity = repository.findById(matchmaking.matchmakingId())
                .orElseGet(MatchmakingEntity::new);
        entity.setMatchmakingId(matchmaking.matchmakingId());
        entity.setPlayerId(matchmaking.playerId());
        entity.setTopicId(matchmaking.topicId());
        entity.setRating(matchmaking.rating());
        entity.setLanguages(MatchmakingEntityMapper.encodeLanguages(matchmaking.languages()));
        entity.setOpponentId(matchmaking.opponentId());
        entity.setRoomId(matchmaking.roomId());
        entity.setVsBot(matchmaking.vsBot());
        entity.setStatus(matchmaking.status());
        entity.setCreatedAt(matchmaking.createdAt());
        entity.setDeadlineAt(matchmaking.deadlineAt());
        entity.setUpdatedAt(matchmaking.updatedAt());
        repository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Matchmaking> findById(String matchmakingId) {
        return repository.findById(matchmakingId).map(MatchmakingEntityMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findSearchingIdsWithDeadlineBefore(Instant before) {
        return repository.findSearchingIdsWithDeadlineBefore(before);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findTerminalIdsBefore(Instant before) {
        return repository.findTerminalIdsBefore(before);
    }

    @Override
    @Transactional
    public void deleteById(String matchmakingId) {
        repository.deleteById(matchmakingId);
        claimRepository.deleteById(matchmakingId);
    }
}
