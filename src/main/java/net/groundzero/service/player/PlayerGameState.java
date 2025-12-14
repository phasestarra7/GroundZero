package net.groundzero.service.player;

import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralized per-player game state.
 * All player-specific runtime data lives here.
 *
 * This replaces scattered state across multiple services:
 * - Combat/Death state (was in PlayerService)
 * - Idle/Camping state (was in CombatIdleService)
 * - Weapon state (will be added when items are implemented)
 * - Support state (will be added when support items are implemented)
 */
public class PlayerGameState {

    /* ===== Combat & Death ===== */
    private boolean isDead = false;
    private BukkitTask respawnTask = null;

    /* ===== Idle & Camping Penalties ===== */
    private int idleTicks = 0;
    private boolean idleWarned = false;
    private int campingPenaltyStep = 0;

    /* ===== Hotbar Swap (Console) ===== */
    private boolean hotbarSwapped = false;

    /* =========================================================
       Combat & Death State
       ========================================================= */

    public boolean isDead() {
        return isDead;
    }

    public void markDead() {
        this.isDead = true;
    }

    public void markAlive() {
        this.isDead = false;
    }

    public BukkitTask getRespawnTask() {
        return respawnTask;
    }

    public void setRespawnTask(BukkitTask task) {
        this.respawnTask = task;
    }

    public void resetCombat() {
        this.isDead = false;
        this.respawnTask = null;
    }

    /* =========================================================
       Idle & Camping State
       ========================================================= */

    public int getIdleTicks() {
        return idleTicks;
    }

    public void setIdleTicks(int ticks) {
        this.idleTicks = ticks;
    }

    public void incrementIdleTicks() {
        this.idleTicks++;
    }

    public boolean isIdleWarned() {
        return idleWarned;
    }

    public void setIdleWarned(boolean warned) {
        this.idleWarned = warned;
    }

    public int getCampingPenaltyStep() {
        return campingPenaltyStep;
    }

    public void setCampingPenaltyStep(int step) {
        this.campingPenaltyStep = step;
    }

    public void resetIdle() {
        this.idleTicks = 0;
        this.idleWarned = false;
        this.campingPenaltyStep = 0;
    }

    /**
     * Reset idle state to negative grace (combat event happened).
     */
    public void resetIdleToGrace(int negativeGraceTicks) {
        this.idleTicks = negativeGraceTicks;
        this.idleWarned = false;
        this.campingPenaltyStep = 0;
    }

    /* =========================================================
       Hotbar Swap
       ========================================================= */

    public boolean isHotbarSwapped() {
        return hotbarSwapped;
    }

    public void setHotbarSwapped(boolean swapped) {
        this.hotbarSwapped = swapped;
    }

    public void toggleHotbarSwap() {
        this.hotbarSwapped = !this.hotbarSwapped;
    }

    /* =========================================================
       Reset (called on session end)
       ========================================================= */

    /**
     * Reset all state for this player.
     * Called when session ends or player is removed from game.
     */
    public void resetAll() {
        resetCombat();
        resetIdle();
        hotbarSwapped = false;
    }
}