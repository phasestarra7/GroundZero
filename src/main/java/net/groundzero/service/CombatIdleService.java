package net.groundzero.service;

import net.groundzero.app.Core;
import net.groundzero.service.tick.TickBus;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Idle timer based camping (combat-only).
 * Services own their TickBus lifecycle: start()/stop() handle register/unregister.
 */
public final class CombatIdleService implements TickBus.Tickable {

    private final Map<UUID, Integer> idleTicks = new ConcurrentHashMap<>();
    private boolean running = false;

    /** Register into TickBus (idempotent). */
    public void start() {
        if (running) return;
        running = true;
        Core.tickBus.register(this);
    }

    /** Unregister from TickBus (idempotent). */
    public void stop() {
        if (!running) return;
        running = false;
        Core.tickBus.unregister(this);
        // optional: keep idleTicks map across rounds or clear here
        // idleTicks.clear();
    }

    /** Convert grace ms to negative ticks (e.g., -200 for 10s). */
    private int negativeGraceTicks() {
        long ms = Math.max(0L, Core.gameConfig.combatWindowMillis);
        long ticks = Math.max(1L, (ms + 49L) / 50L);
        int neg = (int) -ticks;
        return neg == 0 ? -20 : neg;
    }

    /** Hook from combat events (e.g., DamageService.recordHit). */
    public void onCombatEvent(UUID attacker, UUID victim) {
        if (victim == null) return;
        idleTicks.put(victim, negativeGraceTicks());
    }

    @Override
    public void onTick(int currentTick) {
        if (!Core.session.state().isIngame()) return;
        if (currentTick % 20 != 0) return; // once per second

        final int warnTicks = Math.max(0, Core.gameConfig.campWarnSeconds) * 20;
        final int intervalTicks = Math.max(1, Core.gameConfig.campPenaltyIntervalSeconds) * 20;

        for (UUID id : Core.session.getParticipantsView()) {
            int prev = idleTicks.getOrDefault(id, 0);
            int now = prev + 20;
            idleTicks.put(id, now);

            // TODO: on crossing warn (prev < warn <= now), show actionbar "Engage!" + play sound

            if (now >= warnTicks) {
                int elapsed = now - warnTicks;
                if (elapsed % intervalTicks == 0) {
                    double percent = Math.max(0.0, Core.gameConfig.campPenaltyPercent);
                    double cur = Core.session.getScoreMap()
                            .getOrDefault(id, Core.gameConfig.baseScore);
                    double burn = Math.max(0.0, cur * percent);
                    Core.session.getScoreMap().put(id, Math.max(0.0, cur - burn));
                    // TODO: optional broadcast/log for penalty
                }
            }
        }
    }
}
