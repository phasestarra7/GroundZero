package net.groundzero.service.combat;

import net.groundzero.app.Core;
import net.groundzero.item.WeaponType;
import net.groundzero.service.GameService;
import net.groundzero.service.player.PlayerGameState;
import net.groundzero.util.Notifier;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Weapon reload service.
 *
 * Manages reload state and timing for all magazine-based weapons.
 * Uses PlayerGameState for per-weapon magazine/reserve/reloadEndTick storage.
 *
 * Reload Flow:
 * 1. startReload() - Set reloadEndTick, play sound, update ActionBar
 * 2. Wait reloadTicks
 * 3. completeReload() - Transfer ammo, clear reloadEndTick, play sound
 */
public final class ReloadService implements GameService {

    @Override
    public void reset() {
        // State managed by PlayerGameState
    }

    /* ==================== Public API ==================== */

    /**
     * Check if weapon is currently reloading.
     */
    public boolean isReloading(UUID playerId, WeaponType weapon) {
        PlayerGameState state = Core.playerStates.get(playerId);
        if (state == null) return false;

        int currentTick = Core.tickBus.getCurrentTick();
        int reloadEndTick = getReloadEndTick(state, weapon);

        return reloadEndTick > 0 && currentTick < reloadEndTick;
    }

    /**
     * Get current magazine count.
     */
    public int getMagazine(UUID playerId, WeaponType weapon) {
        PlayerGameState state = Core.playerStates.get(playerId);
        if (state == null) return 0;
        return getMagazineInternal(state, weapon);
    }

    /**
     * Get current reserve count.
     */
    public int getReserve(UUID playerId, WeaponType weapon) {
        PlayerGameState state = Core.playerStates.get(playerId);
        if (state == null) return 0;
        return getReserveInternal(state, weapon);
    }

    /**
     * Consume one ammo from magazine.
     * @return true if consumed, false if empty
     */
    public boolean consumeMagazine(UUID playerId, WeaponType weapon) {
        PlayerGameState state = Core.playerStates.get(playerId);
        if (state == null) return false;

        int current = getMagazineInternal(state, weapon);
        if (current <= 0) return false;

        setMagazineInternal(state, weapon, current - 1);
        return true;
    }

    /**
     * Add ammo to magazine directly (from shop purchase, when magazine and reserve are both empty).
     * Allows immediate firing without reload.
     */
    public void addMagazine(UUID playerId, WeaponType weapon, int amount) {
        PlayerGameState state = Core.playerStates.get(playerId);
        if (state == null) return;

        int current = getMagazineInternal(state, weapon);
        setMagazineInternal(state, weapon, current + amount);
    }

    /**
     * Add ammo to reserve (from shop purchase).
     */
    public void addReserve(UUID playerId, WeaponType weapon, int amount) {
        PlayerGameState state = Core.playerStates.get(playerId);
        if (state == null) return;

        int current = getReserveInternal(state, weapon);
        setReserveInternal(state, weapon, current + amount);
    }

    /**
     * Start reload process.
     * @return true if reload started, false if cannot reload
     */
    public boolean startReload(Player player, WeaponType weapon) {
        if (player == null || !player.isOnline()) return false;

        UUID playerId = player.getUniqueId();
        PlayerGameState state = Core.playerStates.get(playerId);
        if (state == null) return false;

        int currentTick = Core.tickBus.getCurrentTick();

        // Already reloading?
        if (isReloading(playerId, weapon)) return false;

        // Magazine already full?
        int magazine = getMagazineInternal(state, weapon);
        int magazineSize = weapon.getMagazineSize();
        if (magazine >= magazineSize) return false;

        // No reserve ammo?
        int reserve = getReserveInternal(state, weapon);
        if (reserve <= 0) return false;

        // Start reload
        int reloadTicks = weapon.getReloadTicks();
        int reloadEndTick = currentTick + reloadTicks;

        setReloadEndTick(state, weapon, reloadEndTick);

        // Feedback
        Core.notifier.sound(player, Sound.ITEM_ARMOR_EQUIP_IRON, Notifier.PitchLevel.MID);
        Core.actionBarService.updateImmediately(playerId);

        // Schedule completion
        final int expectedEndTick = reloadEndTick;
        Core.schedulers.runLater(() -> {
            completeReload(playerId, weapon, expectedEndTick);
        }, reloadTicks);

        return true;
    }

    /**
     * Cancel reload (e.g., on death, weapon switch).
     */
    public void cancelReload(UUID playerId, WeaponType weapon) {
        PlayerGameState state = Core.playerStates.get(playerId);
        if (state == null) return;

        setReloadEndTick(state, weapon, 0);
    }

    /**
     * Cancel all weapon reloads for player.
     */
    public void cancelAllReloads(UUID playerId) {
        PlayerGameState state = Core.playerStates.get(playerId);
        if (state == null) return;

        for (WeaponType weapon : WeaponType.values()) {
            setReloadEndTick(state, weapon, 0);
        }
    }

    /* ==================== Internal: Reload Completion ==================== */

    private void completeReload(UUID playerId, WeaponType weapon, int expectedEndTick) {
        // Verify game state
        if (!Core.session.state().isIngame()) return;

        PlayerGameState state = Core.playerStates.get(playerId);
        if (state == null) return;

        // Verify this reload wasn't cancelled
        if (getReloadEndTick(state, weapon) != expectedEndTick) return;

        // Clear reload state
        setReloadEndTick(state, weapon, 0);

        // Transfer ammo
        int magazineSize = weapon.getMagazineSize();
        int currentMag = getMagazineInternal(state, weapon);
        int reserve = getReserveInternal(state, weapon);

        int needed = magazineSize - currentMag;
        int toLoad = Math.min(needed, reserve);

        setMagazineInternal(state, weapon, currentMag + toLoad);
        setReserveInternal(state, weapon, reserve - toLoad);

        // Feedback
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            Core.notifier.sound(player, Sound.BLOCK_IRON_DOOR_CLOSE, Notifier.PitchLevel.HIGH);
            Core.actionBarService.updateImmediately(playerId);
        }
    }

    /* ==================== Internal: State Accessors ==================== */

    private int getMagazineInternal(PlayerGameState state, WeaponType weapon) {
        return switch (weapon) {
            case ASSAULT -> state.getAssaultMagazine();
            case AUTO -> state.getAutoMagazine();
            case SNIPER -> state.getSniperMagazine();
            case RPG -> state.getRpgMagazine();
        };
    }

    private void setMagazineInternal(PlayerGameState state, WeaponType weapon, int value) {
        switch (weapon) {
            case ASSAULT -> state.setAssaultMagazine(value);
            case AUTO -> state.setAutoMagazine(value);
            case SNIPER -> state.setSniperMagazine(value);
            case RPG -> state.setRpgMagazine(value);
        }
    }

    private int getReserveInternal(PlayerGameState state, WeaponType weapon) {
        return switch (weapon) {
            case ASSAULT -> state.getAssaultReserve();
            case AUTO -> state.getAutoReserve();
            case SNIPER -> state.getSniperReserve();
            case RPG -> state.getRpgReserve();
        };
    }

    private void setReserveInternal(PlayerGameState state, WeaponType weapon, int value) {
        switch (weapon) {
            case ASSAULT -> state.setAssaultReserve(value);
            case AUTO -> state.setAutoReserve(value);
            case SNIPER -> state.setSniperReserve(value);
            case RPG -> state.setRpgReserve(value);
        }
    }

    private int getReloadEndTick(PlayerGameState state, WeaponType weapon) {
        return switch (weapon) {
            case ASSAULT -> state.getAssaultReloadEndTick();
            case AUTO -> state.getAutoReloadEndTick();
            case SNIPER -> state.getSniperReloadEndTick();
            case RPG -> state.getRpgReloadEndTick();
        };
    }

    private void setReloadEndTick(PlayerGameState state, WeaponType weapon, int value) {
        switch (weapon) {
            case ASSAULT -> state.setAssaultReloadEndTick(value);
            case AUTO -> state.setAutoReloadEndTick(value);
            case SNIPER -> state.setSniperReloadEndTick(value);
            case RPG -> state.setRpgReloadEndTick(value);
        }
    }
}