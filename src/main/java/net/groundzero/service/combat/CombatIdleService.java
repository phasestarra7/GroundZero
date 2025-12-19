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
 *
 * Idle Timer Rules:
 * - On combat event (player-vs-player hit): reset both attacker and victim to -combatWindowTicks
 * - On death: reset to -respawnDelayTicks (so timer starts at 0 after respawn)
 * - Every tick: idleTicks += 1
 * - At warn threshold: notify player
 * - At penalty threshold: apply score penalties
 *
 * Note: If player dies and logs out, timer keeps running (they may accumulate penalties).
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
    }

    /* ===================== Combat Event Hook ===================== */

    /**
     * Called on player-vs-player combat event.
     * Resets both attacker and victim idle clocks to -combatWindowTicks.
     */
    public void onCombatEvent(UUID attacker, UUID victim) {
        final int negGrace = -Math.max(0, Core.gameConfig.combatWindowTicks);

        if (victim != null) {
            PlayerGameState state = Core.playerStates.getOrCreate(victim);
            state.resetIdleToGrace(negGrace);
        }

        if (attacker != null) {
            PlayerGameState state = Core.playerStates.getOrCreate(attacker);
            state.resetIdleToGrace(negGrace);
        }
    }

    /**
     * Called when player dies (any cause - player, mob, or environment).
     * Resets idle to -respawnDelayTicks so timer starts at 0 after respawn.
     */
    public void onDeath(UUID playerId) {
        if (playerId == null) return;

        final int negRespawn = -Math.max(0, Core.gameConfig.respawnDelayTicks);

        PlayerGameState state = Core.playerStates.getOrCreate(playerId);
        state.resetIdleToGrace(negRespawn);
    }

    /* ===================== Tick ===================== */

    @Override
    public void onTick(int currentTick) {
        if (!running) return;
        if (!Core.session.state().isIngame()) return;

        final int warnAt = Math.max(0, Core.gameConfig.campWarnTicks);
        final int firstAt = Math.max(1, Core.gameConfig.campFirstPenaltyTicks);
        final int interval = Math.max(1, Core.gameConfig.campPenaltyIntervalTicks);
        final double p = Math.max(0.0, Core.gameConfig.campPenaltyPercent);
        final int maxStacks = Math.max(1, Core.gameConfig.campMaxStacks);

        for (UUID id : Core.session.getParticipantsView()) {
            PlayerGameState state = Core.playerStates.getOrCreate(id);

            // Skip dead players (timer still advances, but no warnings/penalties while dead)
            // Actually, per requirement: timer keeps running even if dead
            // But we should skip penalties if player is dead
            // Let's just advance timer for everyone, but skip warn/penalty if dead

            // 1) Advance idle counter
            final int prev = state.getIdleTicks();
            final int now = prev + 1;
            state.setIdleTicks(now);

            // Skip warnings and penalties if player is dead
            if (state.isDead()) {
                continue;
            }

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
                int already = state.getCampingPenaltyStep();

                if (stepIndex > already) {
                    for (int s = already + 1; s <= stepIndex; s++) {
                        int eff = Math.min(s, maxStacks);
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
}