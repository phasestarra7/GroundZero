package net.groundzero.item.handler;

import net.groundzero.app.Core;
import net.groundzero.service.combat.ProjectileService;
import net.groundzero.service.player.PlayerGameState;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class AssaultHandler implements ItemHandler {

    @Override
    public boolean onLeftClick(Player player, ItemStack item) {
        PlayerGameState state = Core.playerStates.getOrCreate(player.getUniqueId());

        // Check ammo
        if (state.getAssaultAmmo() <= 0) {
            Core.notifier.message(player, true, "Out of ammo!");
            return true;
        }

        // Consume ammo
        if (!state.consumeAssaultAmmo()) {
            Core.notifier.message(player, true, "Out of ammo!");
            return true;
        }

        // Spawn projectile
        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection();

        ProjectileService.ArrowOptions opt = new ProjectileService.ArrowOptions();
        opt.weaponId = "gz_assault";
        opt.baseDamage = Core.gameConfig.assaultDamage;
        opt.speed = Core.gameConfig.assaultProjectileSpeed;
        opt.spread = Core.gameConfig.assaultSpread;
        opt.gravity = true;
        opt.critical = false;
        opt.disallowPickup = true;
        opt.silent = true;

        Core.projectileService.spawnArrow(
                player.getUniqueId(),
                eyeLoc,
                direction,
                opt
        );

        // Update ActionBar immediately (ammo changed)
        Core.actionBarService.updateImmediately(player.getUniqueId());

        return true;
    }

    @Override
    public boolean onRightClick(Player player, ItemStack item) {
        // TODO: ADS mode toggle
        Core.notifier.message(player, false, "ADS mode (TODO)");
        return true;
    }

    @Override
    public String getActionBar(Player player, ItemStack item) {
        PlayerGameState state = Core.playerStates.getOrCreate(player.getUniqueId());
        int ammo = state.getAssaultAmmo();
        int max = Core.gameConfig.assaultMagazineSize;

        return String.format("&e[L]&f Fire Assault Rifle &a(Ammo: %d/%d) &e[R]&f Enter ADS Mode", ammo, max);
    }
}