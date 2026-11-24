package com.joutio.matchmaking.infrastructure.in.scheduler;

import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import com.github.kagkarlsson.scheduler.task.schedule.FixedDelay;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Balayeurs planifiés en tâches récurrentes db-scheduler : une seule réplique exécute chaque
 * passage (verrou par ligne de {@code scheduled_tasks}), contrairement à {@code @Scheduled}.
 */
@Configuration
public class SweepTaskConfiguration {

    private static final Duration SWEEP_INTERVAL = Duration.ofMinutes(1);

    @Bean
    public RecurringTask<Void> roomSweepTask(RoomSweeper sweeper) {
        return Tasks.recurring("room-sweep", FixedDelay.of(SWEEP_INTERVAL))
                .execute((instance, context) -> sweeper.sweep());
    }

    @Bean
    public RecurringTask<Void> matchmakingSweepTask(MatchmakingSweeper sweeper) {
        return Tasks.recurring("matchmaking-sweep", FixedDelay.of(SWEEP_INTERVAL))
                .execute((instance, context) -> sweeper.sweep());
    }
}
