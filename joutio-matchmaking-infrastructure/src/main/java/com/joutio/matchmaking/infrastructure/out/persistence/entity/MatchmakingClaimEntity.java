package com.joutio.matchmaking.infrastructure.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Réclamation d'une recherche par un appariement. La clé primaire rend la réclamation exclusive
 * entre répliques ; la ligne existe indépendamment de {@code matchmaking_entry}, que la projection
 * peut ne pas avoir encore écrite.
 */
@Setter
@Getter
@Entity
@Table(name = "matchmaking_claim")
public class MatchmakingClaimEntity {

    @Id
    @Column(name = "matchmaking_id", nullable = false)
    private String matchmakingId;

    @Column(name = "claimed_at", nullable = false)
    private Instant claimedAt;
}
