package net.groundzero.item.handler;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Base interface for all item behaviors
 */
public interface ItemHandler {

    /**
     * Called when player left-clicks with this item
     * @return true if event should be cancelled
     */
    boolean onLeftClick(Player player, ItemStack item);

    /**
     * Called when player right-clicks with this item
     * @return true if event should be cancelled
     */
    boolean onRightClick(Player player, ItemStack item);

    /**
     * Get ActionBar display for this item.
     *
     * @param player The player holding the item
     * @param item   The item stack
     * @return ActionBar message, null for default, "" to hide
     */
    default String getActionBar(Player player, ItemStack item) {
        return null; // null = use default message from ActionBarService
    }
}