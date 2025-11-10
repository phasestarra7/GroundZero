package net.groundzero.service;

import net.groundzero.app.Core;
import net.groundzero.game.GameState;
import net.groundzero.service.tick.TickBus;
import org.bukkit.Bukkit;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Combat-idle tracker (tick-based, 1-tick resolution).
 *
 * Rules:
 * - Each player has an "idleTicks" counter.
 * - On combat events (hit), both attacker and victim are reset to a negative grace:
 *      idleTicks = -combatWindowTicks (e.g., -200 for 10s)
 * - Every server tick, idleTicks += 1.
 * - When idleTicks crosses warn threshold, you may notify (optional).
 * - When idleTicks >= firstPenaltyTicks, apply penalties stepwise:
 *      stepIndex = 1 at firstPenaltyTicks, then +1 every penaltyIntervalTicks.
 *      applied step is clamped to [0..maxStacks].
 *      deduction = currentScore * (penaltyPercent * min(stepIndex, maxStacks)).
 *      Example with p = 5%:
 *          step1: -5%, step2: -10%, step3+: -15% each time.
 *
 * Config (ticks, not seconds):
 * - Core.gameConfig.combatWindowTicks        (grace as negative reset)
 * - Core.gameConfig.campWarnTicks            (warn threshold, e.g., 90s -> 1800)
 * - Core.gameConfig.campFirstPenaltyTicks    (first penalty threshold, e.g., 120s -> 2400)
 * - Core.gameConfig.campPenaltyIntervalTicks (interval between penalties, e.g., 60s -> 1200)
 * - Core.gameConfig.campPenaltyPercent       (e.g., 0.05 = 5%)
 * - Core.gameConfig.campMaxStacks            (e.g., 3)
 *
 * Notes:
 * - This service does NOT end/clean sessions. Session handles lifecycle.
 * - Score floor is clamped to >= 0.0.
 * - Service owns its TickBus lifecycle; call start()/stop() from GameManager.
 */
public final class CombatIdleService implements TickBus.Tickable {

    /** Per-player idle ticks. Negative means within grace after a combat event. */
    private final Map<UUID, Integer> idleTicks = new ConcurrentHashMap<>();

    /** Per-player "warned" flag to ensure warn side-effects only once. */
    private final Set<UUID> warned = ConcurrentHashMap.newKeySet();

    /** Per-player penalty step already applied (0 = none, 1..maxStacks). */
    private final Map<UUID, Integer> appliedStep = new ConcurrentHashMap<>();

    private boolean running = false;

    /* ===================== Lifecycle ===================== */

    /** Register into TickBus (idempotent). */
    public void start() {
        if (running) return;
        running = true;
        Core.tickBus.register(this);
    }

    /** Unregister and clear state (idempotent). */
    public void stop() {
        if (!running) return;
        running = false;
        Core.tickBus.unregister(this);
        idleTicks.clear();
        warned.clear();
        appliedStep.clear();
    }

    /* ===================== Combat hook ===================== */

    /**
     * Called on every combat event (e.g., from DamageService.recordHit).
     * Resets both attacker and victim idle clocks to negative grace.
     */
    public void onCombatEvent(UUID attacker, UUID victim) {
        final int negGrace = negativeGraceTicks();
        if (victim != null) {
            idleTicks.put(victim, negGrace);
            warned.remove(victim);
            // Do not reset appliedStep here; penalties persist over long idles.
        }
        if (attacker != null) {
            idleTicks.put(attacker, negGrace);
            warned.remove(attacker);
        }
    }

    /* ===================== Tick ===================== */

    @Override
    public void onTick(int currentTick) {
        if (!running) return;
        if (!Core.session.state().isIngame()) return;

        final int warnAt      = Math.max(0, Core.gameConfig.campWarnTicks);
        final int firstAt     = Math.max(1, Core.gameConfig.campFirstPenaltyTicks);
        final int interval    = Math.max(1, Core.gameConfig.campPenaltyIntervalTicks);
        final double p        = Math.max(0.0, Core.gameConfig.campPenaltyPercent);
        final int maxStacks   = Math.max(1, Core.gameConfig.campMaxStacks);

        for (UUID id : Core.session.getParticipantsView()) {
            // 1) advance idle counter
            final int prev = idleTicks.getOrDefault(id, 0);
            final int now  = prev + 1;
            idleTicks.put(id, now);

            // 2) warn once when crossing warnAt
            if (prev < warnAt && now >= warnAt) {
                if (!warned.contains(id)) {
                    warned.add(id);
                    Core.notifier.message(Bukkit.getPlayer(id), true, "WARNING");
                    // TODO: Optional UI feedback (action bar / sound) to the player.
                    // e.g., Core.notifier.sound(id, Sound.BLOCK_NOTE_BLOCK_PLING, Notifier.PitchLevel.ERR);
                }
            }

            // 3) penalties after firstAt, then every interval
            if (now >= firstAt) {
                // stepIndex: 1 at firstAt, 2 at firstAt + interval, ...
                int stepIndex = 1 + ((now - firstAt) / interval);
                int already   = appliedStep.getOrDefault(id, 0);

                // Apply only when a NEW step has been reached
                if (stepIndex > already) {
                    // Apply steps in order until we catch up (handles large jumps)
                    for (int s = already + 1; s <= stepIndex; s++) {
                        int eff = Math.min(s, maxStacks); // clamp to maxStacks
                        double cur = Core.session.getScoreMap()
                                .getOrDefault(id, Core.gameConfig.baseScore);
                        double burn = Math.max(0.0, cur * (p * eff));
                        double next = Math.max(0.0, cur - burn);
                        Core.session.getScoreMap().put(id, next);
                        Core.notifier.message(Bukkit.getPlayer(id), true, stepIndex + " You lost " + burn);
                    }
                    appliedStep.put(id, stepIndex);
                }
            }
        }
    }

    /* ===================== Helpers ===================== */

    /** Convert config grace to negative ticks (never zero). */
    private int negativeGraceTicks() {
        int ticks = Math.max(1, Core.gameConfig.combatWindowTicks);
        int neg = -ticks;
        return (neg == 0 ? -1 : neg);
    }
}
