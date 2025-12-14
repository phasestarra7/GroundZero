package net.groundzero.service.combat;

import net.groundzero.app.Core;
import net.groundzero.service.Resettable;
import net.groundzero.service.player.PlayerGameState;
import net.groundzero.service.tick.TickBus;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Combat-idle tracker (tick-based, 1-tick resolution).
 * Now uses PlayerGameState for all state management.
 *
 * Rules:
 * - Each player has an "idleTicks" counter.
 * - On combat events (hit), both attacker and victim are reset to negative grace.
 * - Every server tick, idleTicks += 1.
 * - When idleTicks crosses warn threshold, notify player.
 * - When idleTicks >= firstPenaltyTicks, apply penalties stepwise.
 */
public final class CombatIdleService implements TickBus.Tickable, Resettable {

    private boolean running = false;

    public CombatIdleService() {}

    /* ===================== Lifecycle ===================== */

    public void start() {
        if (running) return;
        running = true;
        Core.tickBus.register(this);
    }

    public void stop() {
        if (!running) return;
        reset();
    }

    @Override
    public void reset() {
        running = false;
        Core.tickBus.unregister(this);
        // State is managed by PlayerGameStateService
    }

    /* ===================== Combat hook ===================== */

    /**
     * Called on every combat event (e.g., from DamageService.recordHit).
     * Resets both attacker and victim idle clocks to negative grace.
     */
    public void onCombatEvent(UUID attacker, UUID victim) {
        final int negGrace = negativeGraceTicks();

        if (victim != null) {
            PlayerGameState state = Core.playerStates.getOrCreate(victim);
            state.resetIdleToGrace(negGrace);
        }

        if (attacker != null) {
            PlayerGameState state = Core.playerStates.getOrCreate(attacker);
            state.resetIdleToGrace(negGrace);
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
            PlayerGameState state = Core.playerStates.getOrCreate(id);

            // 1) Advance idle counter
            final int prev = state.getIdleTicks();
            final int now  = prev + 1;
            state.setIdleTicks(now);

            // 2) Warn once when crossing warnAt
            if (prev < warnAt && now >= warnAt) {
                if (!state.isIdleWarned()) {
                    state.setIdleWarned(true);
                    Player player = Bukkit.getPlayer(id);
                    if (player != null && player.isOnline()) {
                        Core.notifier.message(player, true, "WARNING: You are camping!");
                    }
                }
            }

            // 3) Penalties after firstAt, then every interval
            if (now >= firstAt) {
                int stepIndex = 1 + ((now - firstAt) / interval);
                int already   = state.getCampingPenaltyStep();

                // Apply only when a NEW step has been reached
                if (stepIndex > already) {
                    // Apply steps in order until we catch up
                    for (int s = already + 1; s <= stepIndex; s++) {
                        int eff = Math.min(s, maxStacks); // clamp to maxStacks
                        double cur = Core.session.getScoreMap()
                                .getOrDefault(id, Core.gameConfig.baseScore);
                        double burn = Math.max(0.0, cur * (p * eff));
                        double next = Math.max(0.0, cur - burn);
                        Core.session.getScoreMap().put(id, next);

                        Player player = Bukkit.getPlayer(id);
                        if (player != null && player.isOnline()) {
                            Core.notifier.message(player, true,
                                    "Camping penalty #" + s + " : -" + String.format("%.2f", burn) + " points");
                        }
                    }
                    state.setCampingPenaltyStep(stepIndex);
                }
            }
        }
    }

    /* ===================== Helpers ===================== */

    private int negativeGraceTicks() {
        int ticks = Math.max(1, Core.gameConfig.combatWindowTicks);
        int neg = -ticks;
        return (neg == 0 ? -1 : neg);
    }
}