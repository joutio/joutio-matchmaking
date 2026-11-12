package com.joutio.matchmaking.application.saga;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Observabilité des échecs de préparation d'une salle : une ligne WARN par échec, avec le motif,
 * le contexte de l'étape et la <b>cause déballée</b> (sans elle, une requête inter-service en
 * erreur ne laissait que « Failing room … reason=… »), et le compteur
 * {@code joutio_room_failures_total{reason}} pour Prometheus/Grafana.
 *
 * <p>N'émet aucune commande : la saga reste seule à envoyer {@code FailRoomCommand}.
 */
@Component
public class RoomFailureRecorder {

    private static final Logger logger = LoggerFactory.getLogger(RoomFailureRecorder.class);

    static final String METRIC = "joutio.room.failures";

    private final MeterRegistry meterRegistry;

    public RoomFailureRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Consigne un échec. {@code context} décrit l'étape (sujet, langues, nombre tiré…) au format
     * {@code clé=valeur} ; {@code cause} peut être {@code null} (échec métier sans exception).
     */
    public void record(String roomId, String reason, String context, Throwable cause) {
        Throwable root = unwrap(cause);
        if (root == null) {
            logger.warn("Échec de salle: roomId={}, reason={}, {}", roomId, reason, context);
        } else {
            logger.warn(
                    "Échec de salle: roomId={}, reason={}, {}, cause={}: {}",
                    roomId,
                    reason,
                    context,
                    root.getClass().getName(),
                    root.getMessage(),
                    root);
        }
        Counter.builder(METRIC)
                .description("Salles échouées pendant la préparation, par motif")
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    /** Vrai si l'échec est un délai d'étape dépassé ({@code orTimeout}). */
    public static boolean isTimeout(Throwable failure) {
        return unwrap(failure) instanceof TimeoutException;
    }

    /** Retire les enveloppes {@link CompletionException}/{@link ExecutionException}. */
    static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
