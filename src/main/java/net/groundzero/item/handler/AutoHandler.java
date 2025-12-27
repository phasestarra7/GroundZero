package net.groundzero.item.handler;

import net.groundzero.app.Core;
import net.groundzero.item.ItemType;
import net.groundzero.item.WeaponType;
import net.groundzero.service.player.PlayerGameState;
import net.groundzero.util.Notifier;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Auto Rifle handler.
 *
 * Actions:
 * - Left Click: Toggle Auto Fire mode
 *   - Only works when Overdrive is OFF
 *   - After delay, fires automatically every N ticks
 *   - Each shot consumes power
 *
 * - Right Click: Toggle Overdrive mode
 *   - While ON: Power increases every tick
 *   - While OFF: Power decreases every tick (min 0)
 *   - Cannot fire while Overdrive is ON
 *
 * Power (autoOverdriveStack) is the resource that:
 * - Builds up during Overdrive
 * - Gets consumed per shot
 * - Decays when not in Overdrive
 */
public final class AutoHandler implements ItemHandler {

    private static final WeaponType WEAPON = WeaponType.AUTO;

    @Override
    public boolean onLeftClick(Player player, ItemStack item) {
        UUID playerId = player.getUniqueId();
        PlayerGameState state = Core.playerStates.get(playerId);
        if (state == null) return true;

        // 0. Check cooldown (placeholder - auto has 0 cooldown)
        // if (Core.cooldownService.isOnCooldown(playerId, ItemType.AUTO, true)) {
        //     Core.notifier.messageOnly(player, true, "Weapon on cooldown");
        //     Core.notifier.sound(player, Sound.BLOCK_DISPENSER_FAIL, Notifier.PitchLevel.LOW);
        //     return true;
        // }

        // 1. If Overdrive is active, turn it off first
        if (state.isAutoOverdrive()) {
            state.setAutoOverdrive(false);
            Core.notifier.sound(player, Sound.BLOCK_BEACON_DEACTIVATE, Notifier.PitchLevel.MID);
        }

        // 2. Toggle Auto Fire mode
        if (state.isAutoFireMode()) {
            // Turn OFF
            state.setAutoFireMode(false);
            Core.autoFireService.stopAutoFire(playerId);
            Core.notifier.sound(player, Sound.BLOCK_NOTE_BLOCK_BASS, Notifier.PitchLevel.LOW);
        } else {
            // Check if has power to fire
            int power = state.getAutoOverdriveStack();
            if (power < Core.gameConfig.autoOverloadConsumePerShot) {
                Core.notifier.messageOnly(player, true, "Not enough power! Charge with Overdrive first.");
                Core.notifier.sound(player, Sound.BLOCK_DISPENSER_FAIL, Notifier.PitchLevel.LOW);
                return true;
            }

            // Check if reloading
            if (Core.reloadService.isReloading(playerId, WEAPON)) {
                Core.notifier.messageOnly(player, true, "Reloading...");
                Core.notifier.sound(player, Sound.BLOCK_DISPENSER_FAIL, Notifier.PitchLevel.LOW);
                return true;
            }

            // Check magazine
            int magazine = Core.reloadService.getMagazine(playerId, WEAPON);
            if (magazine <= 0) {
                int reserve = Core.reloadService.getReserve(playerId, WEAPON);
                if (reserve > 0) {
                    Core.reloadService.startReload(player, WEAPON);
                } else {
                    Core.notifier.messageOnly(player, true, "Out of ammo!");
                    Core.notifier.sound(player, Sound.BLOCK_DISPENSER_FAIL, Notifier.PitchLevel.LOW);
                }
                return true;
            }

            // Turn ON
            state.setAutoFireMode(true);
            Core.autoFireService.startAutoFire(playerId);
            Core.notifier.sound(player, Sound.BLOCK_NOTE_BLOCK_PLING, Notifier.PitchLevel.HIGH);
        }

        // 3. Start cooldown (auto has 0 cooldown - no effect)
        // Core.cooldownService.startCooldown(playerId, ItemType.AUTO, true, Core.gameConfig.autoCooldownTicksL);

        // 4. Update ActionBar
        Core.actionBarService.updateImmediately(playerId);

        return true;
    }

    @Override
    public boolean onRightClick(Player player, ItemStack item) {
        UUID playerId = player.getUniqueId();
        PlayerGameState state = Core.playerStates.get(playerId);
        if (state == null) return true;

        // 0. Check cooldown (placeholder - auto overdrive has 0 cooldown)
        // if (Core.cooldownService.isOnCooldown(playerId, ItemType.AUTO, false)) {
        //     Core.notifier.messageOnly(player, true, "Overdrive on cooldown");
        //     Core.notifier.sound(player, Sound.BLOCK_DISPENSER_FAIL, Notifier.PitchLevel.LOW);
        //     return true;
        // }

        // 1. Toggle Overdrive
        if (state.isAutoOverdrive()) {
            // Turn OFF Overdrive
            state.setAutoOverdrive(false);
            Core.notifier.sound(player, Sound.BLOCK_BEACON_DEACTIVATE, Notifier.PitchLevel.MID);
        } else {
            // Turn ON Overdrive
            // If auto-fire is active, stop it
            if (state.isAutoFireMode()) {
                state.setAutoFireMode(false);
                Core.autoFireService.stopAutoFire(playerId);
            }

            state.setAutoOverdrive(true);
            Core.notifier.sound(player, Sound.BLOCK_BEACON_ACTIVATE, Notifier.PitchLevel.MID);
        }

        // 2. Start cooldown (auto overdrive has 0 cooldown - no effect)
        // Core.cooldownService.startCooldown(playerId, ItemType.AUTO, false, Core.gameConfig.autoCooldownTicksR);

        // 3. Update ActionBar
        Core.actionBarService.updateImmediately(playerId);

        return true;
    }
}