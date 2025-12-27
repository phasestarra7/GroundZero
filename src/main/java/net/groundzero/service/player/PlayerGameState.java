package net.groundzero.service.player;

import net.groundzero.item.ItemType;
import org.bukkit.scheduler.BukkitTask;

import net.groundzero.service.effect.EffectSource;
import java.util.EnumMap;
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
public final class PlayerGameState {

    /* ===== Combat & Death ===== */
    private boolean isDead = false;
    private BukkitTask respawnTask = null;

    /* ===== Idle & Camping Penalties ===== */
    private int idleTicks = 0;
    private boolean idleWarned = false;
    private int campingPenaltyStep = 0;

    /* ===== Hotbar Swap (Console) ===== */
    private boolean hotbarSwapped = false;

    /* ===== Effect Sources (ADS, Zoom, Concussive, etc.) ===== */
    private final Map<EffectSource, Integer> effectSources = new EnumMap<>(EffectSource.class);
    private boolean jumpBlocked = false;

    /* ===== Cooldown State (all items, left/right separate) ===== */
    private final Map<ItemType, Integer> leftCooldownEndTicks = new EnumMap<>(ItemType.class);
    private final Map<ItemType, Integer> rightCooldownEndTicks = new EnumMap<>(ItemType.class);

    /* ===== Assault Rifle ===== */
    private int assaultMagazine = 0;
    private int assaultReserve = 0;
    private int assaultCooldownEndTick = 0;
    private int assaultReloadEndTick = 0;
    // ADS mode managed in PlayerEffectService

    /* ===== Auto Rifle ===== */
    private int autoMagazine = 0;
    private int autoReserve = 0;
    private int autoCooldownEndTick = 0;
    private int autoReloadEndTick = 0;
    private boolean autoFireMode = false;
    private int autoOverdriveStack = 0;

    /* ===== Sniper Rifle ===== */
    private int sniperMagazine = 0;
    private int sniperReserve = 0;
    private int sniperCooldownEndTick = 0;
    private int sniperReloadEndTick = 0;
    // Scope mode managed in PlayerEffectService

    /* ===== RPG ===== */
    private int rpgMagazine = 0;
    private int rpgReserve = 0;
    private int rpgCooldownEndTick = 0;
    private int rpgReloadEndTick = 0;


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
       Effect Sources
       ========================================================= */

    /**
     * Get active effect sources map.
     * Key: EffectSource, Value: endTick (0 = manual, >0 = auto-expire tick)
     */
    // if you need ADS, ZOOM, CONCUSSIVE source : go to PlayerEffectService
    public Map<EffectSource, Integer> getEffectSources() { return effectSources; }
    public boolean isJumpBlocked() { return jumpBlocked; }
    public void setJumpBlocked(boolean blocked) { this.jumpBlocked = blocked; }

    /* =========================================================
       Cooldown State (all items)
       ========================================================= */

    public int getLeftCooldownEndTick(ItemType type) {
        return leftCooldownEndTicks.getOrDefault(type, 0);
    }

    public void setLeftCooldownEndTick(ItemType type, int tick) {
        if (type == null) return;
        leftCooldownEndTicks.put(type, tick);
    }

    public int getRightCooldownEndTick(ItemType type) {
        return rightCooldownEndTicks.getOrDefault(type, 0);
    }

    public void setRightCooldownEndTick(ItemType type, int tick) {
        if (type == null) return;
        rightCooldownEndTicks.put(type, tick);
    }

    public void clearAllCooldowns() {
        leftCooldownEndTicks.clear();
        rightCooldownEndTicks.clear();
    }

    /* =========================================================
       Assault Rifle
       ========================================================= */

    public int getAssaultMagazine() { return assaultMagazine; }
    public void setAssaultMagazine(int v) { this.assaultMagazine = Math.max(0, v); }

    public int getAssaultReserve() { return assaultReserve; }
    public void setAssaultReserve(int v) { this.assaultReserve = Math.max(0, v); }

    public int getAssaultReloadEndTick() { return assaultReloadEndTick; }
    public void setAssaultReloadEndTick(int v) { this.assaultReloadEndTick = v; }

    public boolean isAssaultReloading(int currentTick) {
        return assaultReloadEndTick > 0 && currentTick < assaultReloadEndTick;
    }

    /* =========================================================
       Auto Rifle
       ========================================================= */

    public int getAutoMagazine() { return autoMagazine; }
    public void setAutoMagazine(int v) { this.autoMagazine = Math.max(0, v); }

    public int getAutoReserve() { return autoReserve; }
    public void setAutoReserve(int v) { this.autoReserve = Math.max(0, v); }

    public int getAutoReloadEndTick() { return autoReloadEndTick; }
    public void setAutoReloadEndTick(int v) { this.autoReloadEndTick = v; }

    public boolean isAutoReloading(int currentTick) {
        return autoReloadEndTick > 0 && currentTick < autoReloadEndTick;
    }

    /* =========================================================
       Sniper Rifle
       ========================================================= */

    public int getSniperMagazine() { return sniperMagazine; }
    public void setSniperMagazine(int v) { this.sniperMagazine = Math.max(0, v); }

    public int getSniperReserve() { return sniperReserve; }
    public void setSniperReserve(int v) { this.sniperReserve = Math.max(0, v); }

    public int getSniperReloadEndTick() { return sniperReloadEndTick; }
    public void setSniperReloadEndTick(int v) { this.sniperReloadEndTick = v; }

    public boolean isSniperReloading(int currentTick) {
        return sniperReloadEndTick > 0 && currentTick < sniperReloadEndTick;
    }

    /* =========================================================
       RPG
       ========================================================= */

    public int getRpgMagazine() { return rpgMagazine; }
    public void setRpgMagazine(int v) { this.rpgMagazine = Math.max(0, v); }

    public int getRpgReserve() { return rpgReserve; }
    public void setRpgReserve(int v) { this.rpgReserve = Math.max(0, v); }

    public int getRpgReloadEndTick() { return rpgReloadEndTick; }
    public void setRpgReloadEndTick(int v) { this.rpgReloadEndTick = v; }

    public boolean isRpgReloading(int currentTick) {
        return rpgReloadEndTick > 0 && currentTick < rpgReloadEndTick;
    }

    /* =========================================================
       Reset (called on session end)
       ========================================================= */

    /**
     * Reset all weapon ammo state.
     */
    public void resetAmmo() {
        // Assault
        this.assaultMagazine = 0;
        this.assaultReserve = 0;
        this.assaultReloadEndTick = 0;
    }

    /**
     * Reset all state for this player.
     * Called when session ends or player is removed from game.
     */
    public void resetAll() {
        resetCombat();
        resetIdle();
        resetAmmo();
        hotbarSwapped = false;
        effectSources.clear();
        jumpBlocked = false;
        clearAllCooldowns();
    }
}