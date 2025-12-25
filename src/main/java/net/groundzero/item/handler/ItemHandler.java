package net.groundzero.item.handler;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Base interface for all item behaviors
 */
public interface ItemHandler {

    /**
     * Called when player left-clicks with this item
     *
     * @return true if event should be cancelled
     */
    boolean onLeftClick(Player player, ItemStack item);

    /**
     * Called when player right-clicks with this item
     *
     * @return true if event should be cancelled
     */
    boolean onRightClick(Player player, ItemStack item);
}