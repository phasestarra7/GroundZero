package net.groundzero.listener.player;

import net.groundzero.app.Core;
import net.groundzero.item.ItemType;
import net.groundzero.listener.BaseListener;
import net.groundzero.service.effect.EffectSource;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Handles hotbar slot changes.
 * - Updates ActionBar immediately
 * - Removes weapon-specific effect sources (ADS, Zoom) when switching away
 */
public final class PlayerItemHeldListener extends BaseListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemHeld(PlayerItemHeldEvent event) {
        if (!Core.session.state().isIngame()) return;

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 1tick delay - getItemInMainHand() returns previous item during event
        Core.schedulers.runLater(() -> {
            if (!player.isOnline() || !Core.session.state().isIngame()) return;

            // Update ActionBar
            Core.actionBarService.updateImmediately(playerId);

            // Get new item after slot change
            ItemStack newItem = player.getInventory().getItemInMainHand();
            ItemType newType = Core.itemRegistry.getType(newItem);

            // Remove ADS if not holding Assault
            if (newType != ItemType.ASSAULT) {
                Core.playerEffectService.removeSource(playerId, EffectSource.ASSAULT_ADS);
            }

            // Remove Zoom if not holding Sniper
            if (newType != ItemType.SNIPER) {
                Core.playerEffectService.removeSource(playerId, EffectSource.SNIPER_ZOOM);
            }
        }, 1L);
    }
}