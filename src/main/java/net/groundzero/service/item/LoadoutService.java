package net.groundzero.service.item;

import net.groundzero.app.Core;
import net.groundzero.item.ItemType;
import net.groundzero.service.player.PlayerGameState;
import org.bukkit.Bukkit;
import net.groundzero.service.GameService;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

/**
 * Manages player loadouts at game start.
 *
 * Initial loadout:
 * - Slot 0: Assault Rifle (0 ammo)
 * - Slot 1: Auto Rifle (0 ammo)
 * - Slot 2: Sniper Rifle (0 ammo)
 * - Slot 3: RPG (0 ammo)
 * - Slot 8: Console
 *
 * All weapons start with 0 ammo - players must purchase ammo from shop.
 */
public final class LoadoutService implements GameService {

    @Override
    public void reset() {
        // Stateless service, nothing to reset
    }

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

        // Give non-consumable weapons (slots 0-3) with inventory text
        p.getInventory().setItem(0, Core.itemRegistry.createItemForInventory(ItemType.ASSAULT, 1));
        p.getInventory().setItem(1, Core.itemRegistry.createItemForInventory(ItemType.AUTO, 1));
        p.getInventory().setItem(2, Core.itemRegistry.createItemForInventory(ItemType.SNIPER, 1));
        p.getInventory().setItem(3, Core.itemRegistry.createItemForInventory(ItemType.RPG, 1));

        // Give console (slot 8)
        p.getInventory().setItem(8, Core.itemRegistry.createItemForInventory(ItemType.CONSOLE, 1));

        // Initialize weapon ammo state (all start at 0 magazine, 0 reserve)
        PlayerGameState state = Core.playerStates.getOrCreate(playerId);

        state.setAssaultMagazine(0);
        state.setAssaultReserve(0);
        state.setAssaultReloadEndTick(0);

        state.setAutoMagazine(0);
        state.setAutoReserve(0);
        state.setAutoReloadEndTick(0);

        state.setSniperMagazine(0);
        state.setSniperReserve(0);
        state.setSniperReloadEndTick(0);

        state.setRpgMagazine(0);
        state.setRpgReserve(0);
        state.setRpgReloadEndTick(0); // TODO : revisit and check after fully implemented
    }
}