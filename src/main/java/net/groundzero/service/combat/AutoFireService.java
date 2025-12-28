package net.groundzero.service.combat;

import net.groundzero.app.Core;
import net.groundzero.item.ItemType;
import net.groundzero.item.WeaponType;
import net.groundzero.service.GameService;
import net.groundzero.service.combat.ProjectileService;
import net.groundzero.service.model.ModelType;
import net.groundzero.service.player.PlayerGameState;
import net.groundzero.service.tick.TickBus;
import net.groundzero.util.Notifier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auto Rifle firing and power management service.
 *
 * Mechanics:
 * - Overdrive ON: Power increases by autoOverloadGainPerTick every tick (capped at autoOverloadMax)
 * - Overdrive OFF: Power decreases by autoOverloadLossPerTick every tick (min 0)
 * - Auto Fire: After autoFireStartDelayTicks, fires every autoFireIntervalTicks
 * - Each shot consumes autoOverloadConsumePerShot power
 * - Cannot fire if Overdrive is ON or power is insufficient
 * - Spread/Recoil increases with each consecutive shot (shotCount * base value)
 *
 * Power is tracked per-player in PlayerGameState.autoOverdriveStack
 */
public final class AutoFireService implements TickBus.Tickable, GameService {

    private static final WeaponType WEAPON = WeaponType.AUTO;

    /**
     * Tracks auto-fire state per player.
     * Value: tick when firing should start (after delay)
     */
    private final Map<UUID, Integer> autoFireStartTick = new ConcurrentHashMap<>();

    /**
     * Tracks last fire tick per player to control fire rate
     */
    private final Map<UUID, Integer> lastFireTick = new ConcurrentHashMap<>();

    /**
     * Tracks consecutive shot count since auto-fire started.
     * Resets when auto-fire stops. Used for spread/recoil scaling.
     * Max value is magazine size (60).
     */
    private final Map<UUID, Integer> shotCount = new ConcurrentHashMap<>();

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

        // Note: Maps are NOT cleared here - only TickBus unregistration
        // Internal data is cleared in reset()
    }

    @Override
    public void reset() {
        autoFireStartTick.clear();
        lastFireTick.clear();
        shotCount.clear();
    }

    /* ===================== TickBus ===================== */

    @Override
    public void onTick(int currentTick) {
        if (!Core.session.state().isIngame()) return;

        for (UUID playerId : Core.session.getParticipantsView()) {
            PlayerGameState state = Core.playerStates.get(playerId);
            if (state == null) continue;
            if (state.isDead()) continue;

            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) continue;

            // 1. Update power based on Overdrive state
            updatePower(playerId, state);

            // 2. Handle auto-firing
            if (state.isAutoFireMode()) {
                handleAutoFire(player, playerId, state, currentTick);
            }
        }
    }

    /* ===================== Power Management ===================== */

    private void updatePower(UUID playerId, PlayerGameState state) {
        boolean isOverdrive = state.isAutoOverdrive();
        int currentPower = state.getAutoOverdriveStack();

        if (isOverdrive) {
            // Charging: gain power (capped at max)
            int maxPower = Core.gameConfig.autoOverloadMax;
            if (currentPower < maxPower) {
                int newPower = Math.min(maxPower, currentPower + Core.gameConfig.autoOverloadGainPerTick);
                state.setAutoOverdriveStack(newPower);
            }
        } else {
            // Not charging: lose power
            if (currentPower > 0) {
                int newPower = Math.max(0, currentPower - Core.gameConfig.autoOverloadLossPerTick);
                state.setAutoOverdriveStack(newPower);
            }
        }
    }

    /* ===================== Auto Fire ===================== */

    private void handleAutoFire(Player player, UUID playerId, PlayerGameState state, int currentTick) {
        // Check if Overdrive is ON - cannot fire while charging
        /*
        if (state.isAutoOverdrive()) {
            stopAutoFireInternal(playerId, state);
            Core.notifier.messageOnly(player, true, "Cannot fire while Overdrive is active!");
            Core.notifier.sound(player, Sound.BLOCK_DISPENSER_FAIL, Notifier.PitchLevel.LOW);
            return;
        }
        */
        // now it just deactivates overdrive for UX, so unnecessary

        // Check if we've reached the start delay
        Integer startTick = autoFireStartTick.get(playerId);
        if (startTick == null) {
            return;
        }

        if (currentTick < startTick) {
            // Still in delay period
            return;
        }

        // Check fire interval
        int lastFire = lastFireTick.getOrDefault(playerId, 0);
        if (currentTick - lastFire < Core.gameConfig.autoFireIntervalTicks) {
            return;
        }

        // Check if enough power
        int power = state.getAutoOverdriveStack();
        if (power < Core.gameConfig.autoOverloadConsumePerShot) {
            // Out of power - stop auto-fire
            stopAutoFireInternal(playerId, state);
            Core.notifier.messageOnly(player, true, "Out of power!");
            Core.notifier.sound(player, Sound.BLOCK_DISPENSER_FAIL, Notifier.PitchLevel.LOW);
            return;
        }

        // Check if reloading
        if (Core.reloadService.isReloading(playerId, WEAPON)) {
            // Stop auto-fire when reloading
            stopAutoFireInternal(playerId, state);
            return;
        }

        // Check magazine
        int magazine = Core.reloadService.getMagazine(playerId, WEAPON);
        if (magazine <= 0) {
            // Out of ammo - stop auto-fire and try reload
            stopAutoFireInternal(playerId, state);
            int reserve = Core.reloadService.getReserve(playerId, WEAPON);
            if (reserve > 0) {
                Core.reloadService.startReload(player, WEAPON);
            } else {
                Core.notifier.sound(player, Sound.BLOCK_DISPENSER_FAIL, Notifier.PitchLevel.LOW);
            }
            return;
        }

        // Fire!
        if (Core.reloadService.consumeMagazine(playerId, WEAPON)) {
            // Consume power
            state.setAutoOverdriveStack(power - Core.gameConfig.autoOverloadConsumePerShot);

            // Increment shot count (before firing, so first shot is count=1)
            int currentShotCount = shotCount.getOrDefault(playerId, 0) + 1;
            shotCount.put(playerId, currentShotCount);

            // Fire projectile with scaled spread
            fireProjectile(player, currentShotCount);

            // Apply scaled recoil
            double recoilPitch = Core.gameConfig.autoRecoilPitch * currentShotCount;
            double recoilYaw = Core.gameConfig.autoRecoilYaw * currentShotCount;
            int recoveryTicks = Core.gameConfig.autoRecoilRecoveryTicks;
            Core.recoilService.applyRecoil(player, recoilPitch, recoilYaw, recoveryTicks);

            // Update last fire tick
            lastFireTick.put(playerId, currentTick);

            // Check if magazine now empty - stop and auto-reload
            if (Core.reloadService.getMagazine(playerId, WEAPON) <= 0) {
                stopAutoFireInternal(playerId, state);
                if (Core.reloadService.getReserve(playerId, WEAPON) > 0) {
                    Core.schedulers.runLater(() -> {
                        if (player.isOnline() && Core.session.state().isIngame()) {
                            Core.reloadService.startReload(player, WEAPON);
                        }
                    }, 1L);
                }
            }
        }
    }

    /* ===================== Internal Stop ===================== */

    /**
     * Internal stop - clears tracking data and state flag
     */
    private void stopAutoFireInternal(UUID playerId, PlayerGameState state) {
        autoFireStartTick.remove(playerId);
        lastFireTick.remove(playerId);
        shotCount.remove(playerId);
        state.setAutoFireMode(false);
    }

    /* ===================== Public API ===================== */

    /**
     * Toggle auto-fire mode ON.
     * Called from AutoHandler.onLeftClick()
     */
    public void startAutoFire(UUID playerId) {
        int currentTick = Core.tickBus.getCurrentTick();
        int startTick = currentTick + Core.gameConfig.autoFireStartDelayTicks;
        autoFireStartTick.put(playerId, startTick);
        lastFireTick.put(playerId, 0);
        shotCount.put(playerId, 0); // Reset shot count
    }

    /**
     * Toggle auto-fire mode OFF.
     * Called from AutoHandler.onLeftClick()
     */
    public void stopAutoFire(UUID playerId) {
        autoFireStartTick.remove(playerId);
        lastFireTick.remove(playerId);
        shotCount.remove(playerId);
    }

    /**
     * Check if player is in auto-fire mode (internal tracking).
     */
    public boolean isAutoFiring(UUID playerId) {
        return autoFireStartTick.containsKey(playerId);
    }

    /**
     * Stop auto-fire for player (external call, e.g., on death/weapon switch).
     */
    public void cancelAutoFire(UUID playerId) {
        stopAutoFire(playerId);
        PlayerGameState state = Core.playerStates.get(playerId);
        if (state != null) {
            state.setAutoFireMode(false);
        }
    }

    /**
     * Get current shot count for player (for debugging/UI).
     */
    public int getShotCount(UUID playerId) {
        return shotCount.getOrDefault(playerId, 0);
    }

    /* ===================== Projectile ===================== */

    private void fireProjectile(Player player, int currentShotCount) {
        UUID playerId = player.getUniqueId();
        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection();

        // Build ArrowOptions
        ProjectileService.ArrowOptions opt = new ProjectileService.ArrowOptions();

        // Kinematics - spread scales with shot count
        opt.speed = Core.gameConfig.autoProjectileSpeed;
        opt.spread = Core.gameConfig.autoSpread * currentShotCount;
        opt.gravity = true;

        // Vanilla-like feel
        opt.critical = false;
        opt.knockbackStrength = 0;
        opt.pierceLevel = 0;

        // Identity & damage
        opt.weaponId = "gz_auto";
        opt.baseDamage = Core.gameConfig.autoDamage;

        // Lifecycle / pickup
        opt.lifetimeTicks = 0;
        opt.disallowPickup = true;
        opt.persistent = false;
        opt.silent = true;

        // Cosmetics / debug
        opt.glowing = false;
        opt.debugName = null;

        // Flags
        opt.flags = 0;

        // Spawn arrow
        Arrow arrow = Core.projectileService.spawnArrow(playerId, eyeLoc, direction, opt);

        // Attach visual model
        if (arrow != null) {
            arrow.setVisibleByDefault(false);
            Core.projectileModelService.attachModel(arrow, ModelType.AUTO_BULLET);
        }

        Core.notifier.sound(player, Sound.ENTITY_GENERIC_EXPLODE, Notifier.PitchLevel.HIGH);
    }
}