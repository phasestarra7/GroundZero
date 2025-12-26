package net.groundzero.service.effect;

import net.groundzero.app.Core;
import net.groundzero.service.GameService;
import net.groundzero.service.player.PlayerGameState;
import net.groundzero.service.tick.TickBus;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Manages stacking effect sources (slowness, jump block).
 *
 * Design:
 * - Multiple sources can be active simultaneously
 * - Slowness: highest amplifier wins
 * - JumpBlock: uses GENERIC_JUMP_STRENGTH attribute (no visual glitch)
 * - On source change: recalculate and apply actual effects
 *
 * Darkness is handled separately (simple timed effect, no stacking management).
 */
public final class PlayerEffectService implements TickBus.Tickable, GameService {

    /**
     * Attribute modifier key for jump blocking.
     * Uses MULTIPLY_SCALAR_1 with -1 to make jump_strength = 0
     */
    private static final NamespacedKey JUMP_BLOCK_KEY = new NamespacedKey(Core.plugin, "gz_jump_block");

    private boolean running = false;

    /* ===================== Lifecycle ===================== */

    @Override
    public void start() {
        if (running) return;
        running = true;
        Core.tickBus.register(this);
    }

    @Override
    public void stop() {
        if (!running) return;
        running = false;
        Core.tickBus.unregister(this);

        // Clear all effects from players
        for (UUID id : Core.session.getParticipantsView()) {
            clearAllEffects(id);
        }
    }

    @Override
    public void reset() {
        // States are managed by PlayerGameState
    }

    /* ===================== Public API ===================== */

    /**
     * Add manual (toggle) source. Stays until explicitly removed.
     */
    public void addSource(UUID playerId, EffectSource source) {
        addSource(playerId, source, 0);
    }

    /**
     * Add timed source. Auto-expires after durationTicks.
     * @param durationTicks 0 = manual (no auto-expire), >0 = expires at currentTick + duration
     */
    public void addSource(UUID playerId, EffectSource source, int durationTicks) {
        if (playerId == null || source == null) return;

        PlayerGameState state = Core.playerStates.get(playerId);
        if (state == null) return;

        int endTick = 0;
        if (durationTicks > 0) {
            endTick = Core.tickBus.getCurrentTick() + durationTicks;
        }

        state.getEffectSources().put(source, endTick);
        recalculate(playerId);
    }

    /**
     * Remove a source.
     */
    public void removeSource(UUID playerId, EffectSource source) {
        if (playerId == null || source == null) return;

        PlayerGameState state = Core.playerStates.get(playerId);
        if (state == null) return;

        if (state.getEffectSources().remove(source) != null) {
            recalculate(playerId);
        }
    }

    /**
     * Check if source is active.
     */
    public boolean hasSource(UUID playerId, EffectSource source) {
        if (playerId == null || source == null) return false;

        PlayerGameState state = Core.playerStates.get(playerId);
        if (state == null) return false;

        return state.getEffectSources().containsKey(source);
    }

    /**
     * Remove all sources for a player.
     */
    public void clearAllSources(UUID playerId) {
        if (playerId == null) return;

        PlayerGameState state = Core.playerStates.get(playerId);
        if (state == null) return;

        state.getEffectSources().clear();
        recalculate(playerId);
    }

    /**
     * Apply darkness effect (simple, no stacking management).
     * @param durationTicks typically 40 (2 seconds)
     */
    public void applyDarkness(UUID playerId, int durationTicks) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return;

        player.addPotionEffect(new PotionEffect(
                PotionEffectType.DARKNESS,
                durationTicks,
                0,  // amplifier 0 = level 1
                false,  // ambient
                true,   // particles
                true    // icon
        ));
    }

    /* ===================== Tick (Expiration Check) ===================== */

    @Override
    public void onTick(int currentTick) {
        if (!running) return;
        if (!Core.session.state().isIngame()) return;

        // Expiration check only - jump blocking is now attribute-based
        for (UUID id : Core.session.getParticipantsView()) {
            PlayerGameState state = Core.playerStates.get(id);
            if (state == null) continue;

            Map<EffectSource, Integer> sources = state.getEffectSources();
            if (sources.isEmpty()) continue;

            boolean anyExpired = false;
            Iterator<Map.Entry<EffectSource, Integer>> it = sources.entrySet().iterator();

            while (it.hasNext()) {
                Map.Entry<EffectSource, Integer> entry = it.next();
                int endTick = entry.getValue();

                // endTick > 0 means timed source
                if (endTick > 0 && currentTick >= endTick) {
                    it.remove();
                    anyExpired = true;
                }
            }

            if (anyExpired) {
                recalculate(id);
            }
        }
    }

    /* ===================== Internal: Recalculate & Apply ===================== */

    /**
     * Recalculate and apply actual effects based on active sources.
     */
    private void recalculate(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return;

        PlayerGameState state = Core.playerStates.get(playerId);
        if (state == null) return;

        Map<EffectSource, Integer> sources = state.getEffectSources();

        // No sources → clear all
        if (sources.isEmpty()) {
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            removeJumpBlock(player);
            state.setJumpBlocked(false);
            return;
        }

        // Calculate from all active sources
        int maxSlowness = -1;
        boolean anyJumpBlock = false;

        for (EffectSource src : sources.keySet()) {
            if (src.slownessAmplifier > maxSlowness) {
                maxSlowness = src.slownessAmplifier;
            }
            if (src.blocksJump) {
                anyJumpBlock = true;
            }
        }

        int effectDuration = Core.gameConfig.matchDurationTicks;

        // Apply slowness
        if (maxSlowness >= 0) {
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOWNESS,
                    effectDuration,
                    maxSlowness,
                    false,  // ambient
                    false,  // particles (hide for cleaner UI)
                    true    // icon
            ));
        } else {
            player.removePotionEffect(PotionEffectType.SLOWNESS);
        }

        // Apply jump block via attribute
        if (anyJumpBlock) {
            applyJumpBlock(player);
        } else {
            removeJumpBlock(player);
        }
        state.setJumpBlocked(anyJumpBlock);
    }

    /* ===================== Jump Block via Attribute ===================== */

    /**
     * Apply jump block using GENERIC_JUMP_STRENGTH attribute.
     * Sets jump_strength to 0 using a -1 multiplier.
     *
     * Formula: base * (1 + modifier) = 0.42 * (1 + (-1)) = 0
     */
    private void applyJumpBlock(Player player) {
        AttributeInstance jumpAttr = player.getAttribute(Attribute.GENERIC_JUMP_STRENGTH);
        if (jumpAttr == null) return;

        // Remove existing modifier first (idempotent)
        jumpAttr.removeModifier(JUMP_BLOCK_KEY);

        // Add modifier: -1 with MULTIPLY_SCALAR_1 → base * (1 + -1) = 0
        jumpAttr.addModifier(new AttributeModifier(
                JUMP_BLOCK_KEY,
                -1.0,
                AttributeModifier.Operation.MULTIPLY_SCALAR_1
        ));
    }

    /**
     * Remove jump block modifier, restoring normal jump.
     */
    private void removeJumpBlock(Player player) {
        AttributeInstance jumpAttr = player.getAttribute(Attribute.GENERIC_JUMP_STRENGTH);
        if (jumpAttr == null) return;

        jumpAttr.removeModifier(JUMP_BLOCK_KEY);
    }

    /* ===================== Cleanup ===================== */

    /**
     * Clear all effects for a player (used on stop/death/quit).
     */
    private void clearAllEffects(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            player.removePotionEffect(PotionEffectType.DARKNESS);
            removeJumpBlock(player);
        }

        PlayerGameState state = Core.playerStates.get(playerId);
        if (state != null) {
            state.getEffectSources().clear();
            state.setJumpBlocked(false);
        }
    }
}