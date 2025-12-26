package net.groundzero.item.handler;

import net.groundzero.app.Core;
import net.groundzero.item.WeaponType;
import net.groundzero.service.combat.ProjectileService;
import net.groundzero.service.effect.EffectSource;
import net.groundzero.util.Notifier;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * Assault Rifle handler.
 *
 * Actions:
 * - Left Click: Fire (or start reload if magazine empty)
 * - Right Click: Toggle ADS mode
 *
 * ADS Mode:
 * - Slowness + Jump block (via PlayerEffectService)
 * - Zero spread
 * - Reduced recoil (50%)
 */
public class AssaultHandler implements ItemHandler {

    private static final WeaponType WEAPON = WeaponType.ASSAULT;

    @Override
    public boolean onLeftClick(Player player, ItemStack item) {
        UUID playerId = player.getUniqueId();

        // 1. Check if currently reloading
        if (Core.reloadService.isReloading(playerId, WEAPON)) {
            Core.notifier.sound(player, Sound.BLOCK_DISPENSER_FAIL, Notifier.PitchLevel.LOW);
            return true;
        }

        // 2. Check magazine
        // this won't happen actually though, if last ammo is used, auto reload
        // and if both magazine and reserve is empty, ammo is directly passed to magazine not reserve
        int magazine = Core.reloadService.getMagazine(playerId, WEAPON);
        if (magazine <= 0) {
            // Try to reload if reserve available
            int reserve = Core.reloadService.getReserve(playerId, WEAPON);
            if (reserve > 0) {
                Core.reloadService.startReload(player, WEAPON);
            } else {
                Core.notifier.sound(player, Sound.BLOCK_DISPENSER_FAIL, Notifier.PitchLevel.LOW);
            }
            return true;
        }

        // 3. Consume ammo
        if (!Core.reloadService.consumeMagazine(playerId, WEAPON)) {
            Core.notifier.sound(player, Sound.BLOCK_DISPENSER_FAIL, Notifier.PitchLevel.LOW);
            return true;
        }

        // 4. Check ADS state
        boolean isADS = Core.playerEffectService.hasSource(playerId, EffectSource.ASSAULT_ADS);

        // 5. Fire projectile
        fireProjectile(player, playerId, isADS);

        // 6. Apply recoil (explicit values from config)
        double recoilPitch = Core.gameConfig.assaultRecoilPitch;
        double recoilYaw = Core.gameConfig.assaultRecoilYaw;
        int recoveryTicks = Core.gameConfig.assaultRecoilRecoveryTicks;

        if (isADS) {
            // 50% recoil reduction in ADS mode
            recoilPitch *= 0.5;
            recoilYaw *= 0.5;
        }

        Core.recoilService.applyRecoil(player, recoilPitch, recoilYaw, recoveryTicks);

        // 7. Update ActionBar
        Core.actionBarService.updateImmediately(playerId);

        // 8. Auto-reload if magazine empty
        if (Core.reloadService.getMagazine(playerId, WEAPON) <= 0) {
            if (Core.reloadService.getReserve(playerId, WEAPON) > 0) {
                Core.schedulers.runLater(() -> {
                    if (player.isOnline() && Core.session.state().isIngame()) {
                        Core.reloadService.startReload(player, WEAPON);
                    }
                }, 1L);
            }
        }

        return true;
    }

    @Override
    public boolean onRightClick(Player player, ItemStack item) {
        UUID playerId = player.getUniqueId();

        // Toggle ADS
        if (Core.playerEffectService.hasSource(playerId, EffectSource.ASSAULT_ADS)) {
            // Turn off ADS
            Core.playerEffectService.removeSource(playerId, EffectSource.ASSAULT_ADS);
            Core.notifier.sound(player, Sound.ITEM_SPYGLASS_STOP_USING, Notifier.PitchLevel.MID);
        } else {
            // Turn on ADS
            Core.playerEffectService.addSource(playerId, EffectSource.ASSAULT_ADS);
            Core.notifier.sound(player, Sound.ITEM_SPYGLASS_USE, Notifier.PitchLevel.MID);
        }

        return true;
    }

    /* ==================== Projectile ==================== */

    private void fireProjectile(Player player, UUID playerId, boolean isADS) {
        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection();

        // Build ArrowOptions with all parameters explicit
        ProjectileService.ArrowOptions opt = new ProjectileService.ArrowOptions();

        // Kinematics
        opt.speed = Core.gameConfig.assaultProjectileSpeed;
        opt.spread = isADS ? 0.0 : Core.gameConfig.assaultSpread;
        opt.gravity = true;

        // Vanilla-like feel
        opt.critical = false;
        opt.knockbackStrength = 0;
        opt.pierceLevel = 0;

        // Identity & damage
        opt.weaponId = "gz_assault";
        opt.baseDamage = Core.gameConfig.assaultDamage;

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

        Core.projectileService.spawnArrow(playerId, eyeLoc, direction, opt);
        Core.notifier.sound(player, Sound.ENTITY_GENERIC_EXPLODE, Notifier.PitchLevel.HIGH);
    }
}