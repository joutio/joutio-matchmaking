package com.joutio.matchmaking.infrastructure.out.persistence.adapter;

import com.joutio.matchmaking.domain.model.Room;
import com.joutio.matchmaking.domain.model.RoomStatus;
import com.joutio.matchmaking.domain.port.out.RoomRepositoryPort;
import com.joutio.matchmaking.infrastructure.out.persistence.mapper.RoomEntityMapper;
import com.joutio.matchmaking.infrastructure.out.persistence.repository.RoomJpaRepository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class RoomRepositoryAdapter implements RoomRepositoryPort {

    private static final Set<RoomStatus> OPEN = EnumSet.of(RoomStatus.INVITED, RoomStatus.WAITING_PLAYERS);
    private static final Set<RoomStatus> PREPARING = EnumSet.of(
            RoomStatus.FETCHING_PLAYERS, RoomStatus.FETCHING_PLAYED_QUESTIONS,
            RoomStatus.FETCHING_QUESTIONS, RoomStatus.CREATING_GAME);
    private static final Set<RoomStatus> NOT_TERMINAL = EnumSet.copyOf(
            Arrays.stream(RoomStatus.values()).filter(status -> !status.isTerminal()).toList());
    private static final Set<RoomStatus> TERMINAL = EnumSet.complementOf(EnumSet.copyOf(NOT_TERMINAL));

    private final RoomJpaRepository roomJpaRepository;

    public RoomRepositoryAdapter(RoomJpaRepository roomJpaRepository) {
        this.roomJpaRepository = roomJpaRepository;
    }

    @Override
    @Transactional
    public void save(Room room) {
        roomJpaRepository.save(RoomEntityMapper.toEntity(room));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Room> findById(String roomId) {
        return roomJpaRepository.findById(roomId).map(RoomEntityMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Room> findActiveByPlayerId(String playerId) {
        return roomJpaRepository.findByPlayerIdAndStatusIn(playerId, NOT_TERMINAL).stream()
                .map(RoomEntityMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Room> findOpenBetween(String playerId, String otherPlayerId) {
        return roomJpaRepository.findBetween(playerId, otherPlayerId, OPEN).stream()
                .map(RoomEntityMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findOpenIdsExpiredBefore(Instant before) {
        return roomJpaRepository.findIdsByStatusExpiringBefore(OPEN, before);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findStalledPreparationIdsBefore(Instant before) {
        return roomJpaRepository.findIdsByStatusUpdatedBefore(PREPARING, before);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findTerminalIdsBefore(Instant before) {
        return roomJpaRepository.findIdsByStatusUpdatedBefore(TERMINAL, before);
    }

    @Override
    @Transactional
    public void deleteById(String roomId) {
        roomJpaRepository.deleteById(roomId);
    }
}
