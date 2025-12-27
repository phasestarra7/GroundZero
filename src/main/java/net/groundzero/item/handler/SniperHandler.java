package net.groundzero.item.handler;

import net.groundzero.app.Core;
import net.groundzero.item.ItemType;
import net.groundzero.item.WeaponType;
import net.groundzero.service.combat.ProjectileService;
import net.groundzero.service.effect.EffectSource;
import net.groundzero.service.model.ModelType;
import net.groundzero.util.Notifier;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * Sniper Rifle handler.
 *
 * Actions:
 * - Left Click: Fire (should be scoped)
 * - Right Click: Toggle scope
 *
 * Scope:
 * - Slowness + Jump block (via PlayerEffectService)
 */

public class SniperHandler implements ItemHandler {

    private static final WeaponType WEAPON = WeaponType.SNIPER;

    @Override
    public boolean onLeftClick(Player player, ItemStack item) {
        UUID playerId = player.getUniqueId();

        // 0. Check cooldown
        if (Core.cooldownService.isOnCooldown(playerId, ItemType.SNIPER, true)) {
            Core.notifier.messageOnly(player, true, "Weapon on cooldown");
            Core.notifier.sound(player, Sound.BLOCK_DISPENSER_FAIL, Notifier.PitchLevel.LOW);
            return true;
        }

        // 1. Check if scoped
        boolean isScoped = Core.playerEffectService.hasSource(playerId, EffectSource.SNIPER_SCOPED);
        if (!isScoped) {
            Core.notifier.messageOnly(player, true, "You should be scoped to fire!");
            Core.notifier.sound(player, Sound.BLOCK_DISPENSER_FAIL, Notifier.PitchLevel.LOW);
            return true;
        }

        // 2. Check if currently reloading
        if (Core.reloadService.isReloading(playerId, WEAPON)) {
            Core.notifier.messageOnly(player, true, "Reloading...");
            Core.notifier.sound(player, Sound.BLOCK_DISPENSER_FAIL, Notifier.PitchLevel.LOW);
            return true;
        }

        // 3. Check magazine
        // this won't happen actually though, if last ammo is used, auto reload
        // and if both magazine and reserve is empty, ammo is directly passed to magazine not reserve
        int magazine = Core.reloadService.getMagazine(playerId, WEAPON);
        if (magazine <= 0) {
            // Try to reload if reserve available
            int reserve = Core.reloadService.getReserve(playerId, WEAPON);
            if (reserve > 0) {
                Core.reloadService.startReload(player, WEAPON);
            } else {
                Core.notifier.messageOnly(player, true, "Out of ammo!");
                Core.notifier.sound(player, Sound.BLOCK_DISPENSER_FAIL, Notifier.PitchLevel.LOW);
            }
            return true;
        }

        // 4. Consume ammo
        if (!Core.reloadService.consumeMagazine(playerId, WEAPON)) {
            Core.notifier.messageOnly(player, true, "Out of ammo!");
            Core.notifier.sound(player, Sound.BLOCK_DISPENSER_FAIL, Notifier.PitchLevel.LOW);
            return true;
        }

        // 5. Fire projectile
        fireProjectile(player);

        // 6. Apply recoil (explicit values from config)
        double recoilPitch = Core.gameConfig.sniperRecoilPitch;
        double recoilYaw = Core.gameConfig.sniperRecoilYaw;
        int recoveryTicks = Core.gameConfig.sniperRecoilRecoveryTicks;
        Core.recoilService.applyRecoil(player, recoilPitch, recoilYaw, recoveryTicks);

        // 7. Start cooldown
        Core.cooldownService.startCooldown(playerId, ItemType.SNIPER, true, Core.gameConfig.sniperCooldownTicksL);

        // 8. Update ActionBar
        Core.actionBarService.updateImmediately(playerId);

        // 9. Auto-reload if magazine empty
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

        // 0. Check cooldown (sniper scope has 0 cooldown - placeholder for pattern)
        // if (Core.cooldownService.isOnCooldown(playerId, ItemType.SNIPER, false)) {
        //     Core.notifier.messageOnly(player, true, "Scope on cooldown");
        //     Core.notifier.sound(player, Sound.BLOCK_DISPENSER_FAIL, Notifier.PitchLevel.LOW);
        //     return true;
        // }

        // 1. Toggle scope
        if (Core.playerEffectService.hasSource(playerId, EffectSource.SNIPER_SCOPED)) {
            // Scope off
            Core.playerEffectService.removeSource(playerId, EffectSource.SNIPER_SCOPED);
            Core.notifier.sound(player, Sound.ITEM_SPYGLASS_STOP_USING, Notifier.PitchLevel.MID);
        } else {
            // Scope on
            Core.playerEffectService.addSource(playerId, EffectSource.SNIPER_SCOPED);
            Core.notifier.sound(player, Sound.ITEM_SPYGLASS_USE, Notifier.PitchLevel.MID);
        }

        // 2. Start cooldown (sniper scope has 0 cooldown - no effect)
        // Core.cooldownService.startCooldown(playerId, ItemType.SNIPER, false, Core.gameConfig.sniperCooldownTicksR);

        // 3. Update ActionBar
        Core.actionBarService.updateImmediately(player.getUniqueId());
        return true;
    }

    /* ==================== Projectile ==================== */

    private void fireProjectile(Player player) {
        UUID playerId = player.getUniqueId();
        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection();

        // Build ArrowOptions with all parameters explicit
        ProjectileService.ArrowOptions opt = new ProjectileService.ArrowOptions();

        // Kinematics
        opt.speed = Core.gameConfig.sniperProjectileSpeed;
        opt.spread = Core.gameConfig.sniperSpread;
        opt.gravity = true;

        // Vanilla-like feel
        opt.critical = false;
        opt.knockbackStrength = 0;
        opt.pierceLevel = 0;

        // Identity & damage
        opt.weaponId = "gz_sniper";
        opt.baseDamage = Core.gameConfig.sniperDamage;

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
            // Make arrow invisible (model will be visible instead)
            arrow.setVisibleByDefault(false);

            // Attach bullet model
            Core.projectileModelService.attachModel(arrow, ModelType.SNIPER_BULLET);
        }

        Core.notifier.sound(player, Sound.ENTITY_GENERIC_EXPLODE, Notifier.PitchLevel.MID);
    }
}
