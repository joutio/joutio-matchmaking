package com.joutio.matchmaking.application.saga;

import com.joutio.matchmaking.domain.model.RoomOrigin;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Component;

/**
 * Compteur produit des défis ({@code joutio_room_invitations_total{origin, outcome}}) : envoyés, puis acceptés,
 * refusés, annulés ou expirés sans réponse. Les événements de salle ne disent pas tous s'ils concernent une invitation
 * (une annulation peut viser un salon partagé) : la saga, qui connaît l'étape, est la seule à pouvoir compter juste.
 */
@Component
public class RoomInvitationRecorder {

    static final String METRIC = "joutio.room.invitations";

    public enum Outcome { SENT, ACCEPTED, DECLINED, CANCELLED, EXPIRED }

    private final MeterRegistry meterRegistry;

    public RoomInvitationRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void record(RoomOrigin origin, Outcome outcome) {
        Counter.builder(METRIC)
                .description("Défis et revanches, par issue")
                .tag("origin", origin.name())
                .tag("outcome", outcome.name())
                .register(meterRegistry)
                .increment();
    }
}
