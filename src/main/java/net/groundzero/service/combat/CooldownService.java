package net.groundzero.service.combat;

import net.groundzero.app.Core;
import net.groundzero.item.ItemType;
import net.groundzero.service.GameService;
import net.groundzero.service.player.PlayerGameState;

import java.util.UUID;

/**
 * Universal cooldown service for all items (except Console).
 *
 * Manages cooldown state for:
 * - Weapons (Assault, Auto, Sniper, RPG, Stun, Smoke)
 * - Support items (Medkit, Blocks, Bridge, Bunker, AntiExp, Pearl)
 * - Aerial support (Simple, Arrow, Cluster, Spreader, Carpet, Hack)
 * - Missiles (Simple, Poison, Bunker, HighExp, Nuclear, ABM)
 *
 * Left/Right separation:
 * - Items with both L/R actions (weapons, missiles) have separate cooldowns
 * - Items with single action use the appropriate side only
 *
 * Cooldown Flow:
 * 1. Handler checks isOnCooldown() before action
 * 2. If not on cooldown, handler executes and calls startCooldown()
 * 3. Cooldown expires when currentTick >= cooldownEndTick
 *
 * Note: cooldownEndTick = 0 means no cooldown active.
 */
public final class CooldownService implements GameService {

    @Override
    public void reset() {
        // State managed by PlayerGameState
    }

    /* ==================== Public API ==================== */

    /**
     * Check if item action is currently on cooldown.
     *
     * @param playerId    Player UUID
     * @param type        Item type
     * @param isLeftClick true for left-click action, false for right-click
     * @return true if on cooldown
     */
    public boolean isOnCooldown(UUID playerId, ItemType type, boolean isLeftClick) {
        if (playerId == null || type == null) return false;

        PlayerGameState state = Core.playerStates.get(playerId);
        if (state == null) return false;

        int currentTick = Core.tickBus.getCurrentTick();
        int cooldownEndTick = isLeftClick
                ? state.getLeftCooldownEndTick(type)
                : state.getRightCooldownEndTick(type);

        return cooldownEndTick > 0 && currentTick < cooldownEndTick;
    }

    /**
     * Get remaining cooldown ticks for item action.
     *
     * @return 0 if not on cooldown, otherwise remaining ticks
     */
    public int getRemainingCooldown(UUID playerId, ItemType type, boolean isLeftClick) {
        if (playerId == null || type == null) return 0;

        PlayerGameState state = Core.playerStates.get(playerId);
        if (state == null) return 0;

        int currentTick = Core.tickBus.getCurrentTick();
        int cooldownEndTick = isLeftClick
                ? state.getLeftCooldownEndTick(type)
                : state.getRightCooldownEndTick(type);

        if (cooldownEndTick <= 0 || currentTick >= cooldownEndTick) {
            return 0;
        }

        return cooldownEndTick - currentTick;
    }

    /**
     * Start cooldown for item action.
     *
     * @param playerId      Player UUID
     * @param type          Item type
     * @param isLeftClick   true for left-click action, false for right-click
     * @param cooldownTicks Duration in ticks (0 = no cooldown)
     */
    public void startCooldown(UUID playerId, ItemType type, boolean isLeftClick, int cooldownTicks) {
        if (playerId == null || type == null) return;

        PlayerGameState state = Core.playerStates.get(playerId);
        if (state == null) return;

        if (cooldownTicks <= 0) {
            // No cooldown
            if (isLeftClick) {
                state.setLeftCooldownEndTick(type, 0);
            } else {
                state.setRightCooldownEndTick(type, 0);
            }
            return;
        }

        int currentTick = Core.tickBus.getCurrentTick();
        int cooldownEndTick = currentTick + cooldownTicks;

        if (isLeftClick) {
            state.setLeftCooldownEndTick(type, cooldownEndTick);
        } else {
            state.setRightCooldownEndTick(type, cooldownEndTick);
        }
    }

    /**
     * Cancel cooldown for item action.
     */
    public void cancelCooldown(UUID playerId, ItemType type, boolean isLeftClick) {
        if (playerId == null || type == null) return;

        PlayerGameState state = Core.playerStates.get(playerId);
        if (state == null) return;

        if (isLeftClick) {
            state.setLeftCooldownEndTick(type, 0);
        } else {
            state.setRightCooldownEndTick(type, 0);
        }
    }

    /**
     * Cancel both left and right cooldowns for item.
     */
    public void cancelBothCooldowns(UUID playerId, ItemType type) {
        cancelCooldown(playerId, type, true);
        cancelCooldown(playerId, type, false);
    }

    /**
     * Cancel all cooldowns for player (all items, both actions).
     */
    public void cancelAllCooldowns(UUID playerId) {
        if (playerId == null) return;

        PlayerGameState state = Core.playerStates.get(playerId);
        if (state == null) return;

        state.clearAllCooldowns();
    }
}