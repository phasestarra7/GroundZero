package net.groundzero.item.handler;

import net.groundzero.app.Core;
import net.groundzero.item.ItemType;
import net.groundzero.item.WeaponType;
import net.groundzero.service.combat.ProjectileFlagService;
import net.groundzero.service.combat.ProjectileService;
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
 * RPG handler.
 *
 * Actions:
 * - Left Click: Fire explosive rocket
 *   - Arrow with FLAG_RPG_EXPLOSIVE
 *   - On hit (entity/block): TntService.detonateTntAtLocation()
 *   - Arrow damage ignored; explosion handles all damage
 *
 * - Right Click: Rocket jump
 *   - Consumes ammo
 *   - Applies velocity in opposite direction of eye direction
 *   - Looking down → propels upward
 *   - Looking forward → propels backward
 *
 * Feedback:
 * - All feedback via sound only
 * - ActionBar updated by ActionBarService every tick
 */
public final class RpgHandler implements ItemHandler {

    private static final WeaponType WEAPON = WeaponType.RPG;

    @Override
    public boolean onLeftClick(Player player, ItemStack item) {
        UUID playerId = player.getUniqueId();

        // 0. Check cooldown
        if (Core.cooldownService.isOnCooldown(playerId, ItemType.RPG, true)) {
            Core.notifier.sound(player, Sound.BLOCK_DISPENSER_FAIL, Notifier.PitchLevel.LOW);
            return true;
        }

        // 1. Check if currently reloading
        if (Core.reloadService.isReloading(playerId, WEAPON)) {
            Core.notifier.sound(player, Sound.BLOCK_DISPENSER_FAIL, Notifier.PitchLevel.LOW);
            return true;
        }

        // 2. Check magazine
        int magazine = Core.reloadService.getMagazine(playerId, WEAPON);
        if (magazine <= 0) {
            int reserve = Core.reloadService.getReserve(playerId, WEAPON);
            if (reserve > 0) {
                Core.reloadService.startReload(player, WEAPON);
            } else {
                // Empty click sound
                Core.notifier.sound(player, Sound.UI_BUTTON_CLICK, Notifier.PitchLevel.LOW);
            }
            return true;
        }

        // 3. Consume ammo
        if (!Core.reloadService.consumeMagazine(playerId, WEAPON)) {
            Core.notifier.sound(player, Sound.UI_BUTTON_CLICK, Notifier.PitchLevel.LOW);
            return true;
        }

        // 4. Fire explosive rocket
        fireRocket(player);

        // 5. Apply recoil
        double recoilPitch = Core.gameConfig.rpgRecoilPitch;
        double recoilYaw = Core.gameConfig.rpgRecoilYaw;
        int recoveryTicks = Core.gameConfig.rpgRecoilRecoveryTicks;

        Core.recoilService.applyRecoil(player, recoilPitch, recoilYaw, recoveryTicks);

        // 6. Start cooldown
        Core.cooldownService.startCooldown(playerId, ItemType.RPG, true, Core.gameConfig.rpgCooldownTicksL);

        // 7. Auto-reload if magazine empty (RPG has 1 shot magazine)
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

        // 0. Check cooldown
        if (Core.cooldownService.isOnCooldown(playerId, ItemType.RPG, false)) {
            Core.notifier.sound(player, Sound.BLOCK_DISPENSER_FAIL, Notifier.PitchLevel.LOW);
            return true;
        }

        // 1. Check if currently reloading
        if (Core.reloadService.isReloading(playerId, WEAPON)) {
            Core.notifier.sound(player, Sound.BLOCK_DISPENSER_FAIL, Notifier.PitchLevel.LOW);
            return true;
        }

        // 2. Check magazine
        int magazine = Core.reloadService.getMagazine(playerId, WEAPON);
        if (magazine <= 0) {
            int reserve = Core.reloadService.getReserve(playerId, WEAPON);
            if (reserve > 0) {
                Core.reloadService.startReload(player, WEAPON);
            } else {
                // Empty click sound
                Core.notifier.sound(player, Sound.UI_BUTTON_CLICK, Notifier.PitchLevel.LOW);
            }
            return true;
        }

        // 3. Consume ammo
        if (!Core.reloadService.consumeMagazine(playerId, WEAPON)) {
            Core.notifier.sound(player, Sound.UI_BUTTON_CLICK, Notifier.PitchLevel.LOW);
            return true;
        }

        // 4. Perform rocket jump
        performRocketJump(player);

        // 5. Start cooldown
        Core.cooldownService.startCooldown(playerId, ItemType.RPG, false, Core.gameConfig.rpgCooldownTicksR);

        // 6. Auto-reload if magazine empty
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

    /* ==================== Fire Rocket ==================== */

    private void fireRocket(Player player) {
        UUID playerId = player.getUniqueId();
        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection();

        // Build ArrowOptions with FLAG_RPG_EXPLOSIVE
        ProjectileService.ArrowOptions opt = new ProjectileService.ArrowOptions();

        // Kinematics
        opt.speed = Core.gameConfig.rpgProjectileSpeed;
        opt.spread = Core.gameConfig.rpgSpread;
        opt.gravity = true;

        // Vanilla-like feel
        opt.critical = false;
        opt.knockbackStrength = 0;
        opt.pierceLevel = 0;

        // Identity & damage (baseDamage used for explosion)
        opt.weaponId = "gz_rpg";
        opt.baseDamage = Core.gameConfig.rpgDamage;

        // Lifecycle / pickup
        opt.lifetimeTicks = 0;
        opt.disallowPickup = true;
        opt.persistent = false;
        opt.silent = true;

        // Cosmetics / debug
        opt.glowing = false;
        opt.debugName = null;

        // FLAGS: Set RPG explosive flag
        opt.flags = ProjectileFlagService.FLAG_RPG_EXPLOSIVE;

        // Spawn arrow
        Arrow arrow = Core.projectileService.spawnArrow(playerId, eyeLoc, direction, opt);

        // Attach visual model
        if (arrow != null) {
            arrow.setVisibleByDefault(false);
            Core.projectileModelService.attachModel(arrow, ModelType.RPG_ROCKET);
        }

        // Fire sound
        Core.notifier.sound(player, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, Notifier.PitchLevel.LOW);
    }

    /* ==================== Rocket Jump ==================== */

    private void performRocketJump(Player player) {
        // Get eye direction and invert it
        Vector eyeDirection = player.getEyeLocation().getDirection();
        Vector jumpDirection = eyeDirection.multiply(-1);

        // Scale by configured velocity multiplier
        double velocityMultiplier = Core.gameConfig.rpgRocketJumpVelocity;
        Vector jumpVelocity = jumpDirection.multiply(velocityMultiplier);

        // Apply velocity
        player.setVelocity(jumpVelocity);

        // Rocket jump sound (explosion-like)
        Core.notifier.sound(player, Sound.ENTITY_GENERIC_EXPLODE, Notifier.PitchLevel.MID);

        // Optional: Apply self-damage (configurable)
        if (Core.gameConfig.rpgRocketJumpSelfDamage > 0) {
            Core.damageService.applyCustomDamage(
                    player.getUniqueId(),
                    player,
                    Core.gameConfig.rpgRocketJumpSelfDamage
            );
        }
    }
}