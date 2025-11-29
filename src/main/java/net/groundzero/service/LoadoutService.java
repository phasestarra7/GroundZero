/*// service/LoadoutService.java
package net.groundzero.service;

import net.groundzero.app.Core;
import net.groundzero.item.ItemType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.UUID;

public class LoadoutService {

    public void giveInitialLoadouts(Set<UUID> participants) {
        for (UUID id : participants) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;

            // Clear first
            p.getInventory().clear();

            // Give personal weapons (slot 0-3)
            p.getInventory().setItem(0, Core.itemRegistry.createItem(ItemType.ASSAULT, 1));
            p.getInventory().setItem(1, Core.itemRegistry.createItem(ItemType.AUTO, 1));
            p.getInventory().setItem(2, Core.itemRegistry.createItem(ItemType.SNIPER, 1));
            p.getInventory().setItem(3, Core.itemRegistry.createItem(ItemType.RPG, 1));

            // Console (slot 8)
            p.getInventory().setItem(8, Core.itemRegistry.createItem(ItemType.CONSOLE, 1));

            // Initialize weapon state (magazine + reserve ammo)
            initWeaponState(id);
        }
    }

    private void initWeaponState(UUID id) {
        // Assault: 30 mag, 90 reserve
        Core.weaponStateService.initWeapon(id, ItemType.ASSAULT.id, 30, 90);
        // Auto: 45 mag, 135 reserve
        Core.weaponStateService.initWeapon(id, ItemType.AUTO.id, 45, 135);
        // Sniper: 5 mag, 20 reserve
        Core.weaponStateService.initWeapon(id, ItemType.SNIPER.id, 5, 20);
        // RPG: 1 mag, 3 reserve
        Core.weaponStateService.initWeapon(id, ItemType.RPG.id, 1, 3);
    }
}*/ // TODO