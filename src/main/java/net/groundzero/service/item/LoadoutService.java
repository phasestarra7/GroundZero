package net.groundzero.service.item;

import net.groundzero.app.Core;
import net.groundzero.item.ItemType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

/**
 * Manages player loadouts at game start
 */
public class LoadoutService {

    /**
     * Give initial loadout to all participants
     */
    public void giveInitialLoadouts(Set<UUID> participants) {
        for (UUID id : participants) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;

            giveInitialLoadout(id);
        }
    }

    /**
     * Give initial loadout to a single player
     */
    public void giveInitialLoadout(UUID playerId) {
        Player p = Bukkit.getPlayer(playerId);
        if (p == null) return;

        // Clear inventory
        p.getInventory().clear();

        // Give non-consumable weapons (slots 0-3)
        p.getInventory().setItem(0, Core.itemRegistry.createItem(ItemType.ASSAULT, 1));
        p.getInventory().setItem(1, Core.itemRegistry.createItem(ItemType.AUTO, 1));
        p.getInventory().setItem(2, Core.itemRegistry.createItem(ItemType.SNIPER, 1));
        p.getInventory().setItem(3, Core.itemRegistry.createItem(ItemType.RPG, 1));

        // Give console (slot 8)
        p.getInventory().setItem(8, Core.itemRegistry.createItem(ItemType.CONSOLE, 1));

        // TODO: Initialize weapon ammo state when weapon system is added
    }
}