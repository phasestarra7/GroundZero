package net.groundzero.listener.player;

import net.groundzero.app.Core;
import net.groundzero.item.ItemType;
import net.groundzero.item.handler.ItemHandler;
import net.groundzero.listener.BaseListener;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Routes player item interactions to appropriate handlers
 */
public class ItemInteractionListener extends BaseListener implements Listener {

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // No item in hand
        if (item == null) return;

        // Only process during game
        if (!Core.session.state().isIngame()) return;

        // Get item type from PDC
        ItemType type = Core.itemRegistry.getType(item);
        if (type == null) return; // Not a GZ item

        // Get handler
        ItemHandler handler = Core.itemRegistry.getHandler(type);
        if (handler == null) return; // No handler registered

        Action action = event.getAction();
        boolean handled = false;

        // Route to handler based on action
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            handled = handler.onLeftClick(player, item);
        } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            handled = handler.onRightClick(player, item);
        }

        // Cancel vanilla behavior if handler processed the event
        if (handled) {
            event.setCancelled(true);
        }
    }
}