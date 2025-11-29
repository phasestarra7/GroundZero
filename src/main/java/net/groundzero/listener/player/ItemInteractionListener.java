/*
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

public class ItemInteractionListener extends BaseListener implements Listener {

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null) return;
        if (!Core.session.state().isIngame()) return;

        // Get item type from PDC
        ItemType type = Core.itemRegistry.getType(item);
        if (type == null) return;

        // Get handler
        ItemHandler handler = Core.itemRegistry.getHandler(type);
        if (handler == null) return;

        Action action = event.getAction();
        boolean handled = false;

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            handled = handler.onLeftClick(player, item);
        } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            handled = handler.onRightClick(player, item);
        }

        if (handled) {
            event.setCancelled(true);
        }
    }
}*/ //TODO : make item- code structure