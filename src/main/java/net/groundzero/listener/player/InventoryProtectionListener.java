package net.groundzero.listener.player;

import net.groundzero.app.Core;
import net.groundzero.item.ItemType;
import net.groundzero.listener.BaseListener;
import net.groundzero.service.player.PlayerGameState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/**
 * Protects non-consumable items (4 weapons + console) from being:
 * - Dropped
 * - Moved out of designated slots
 * - Put in chests/containers
 * - Lost on death
 *
 * Hotbar layout:
 * - Normal: Slots 0-3 = weapons, Slot 8 = console
 * - Swapped: Slots 27-30 = weapons, Slot 8 = console (console stays in slot 8)
 */
public class InventoryProtectionListener extends BaseListener implements Listener {

    // Protected slots in normal hotbar
    private static final Set<Integer> PROTECTED_SLOTS_NORMAL = Set.of(0, 1, 2, 3, 8);

    // Protected slots when hotbar is swapped
    // Weapons move to 27-30, but console stays at 8
    private static final Set<Integer> PROTECTED_SLOTS_SWAPPED = Set.of(27, 28, 29, 30, 8);

    /**
     * Prevent dropping non-consumable items
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemDrop(PlayerDropItemEvent event) {
        if (!Core.session.state().isIngame()) return;

        ItemStack item = event.getItemDrop().getItemStack();
        ItemType type = Core.itemRegistry.getType(item);

        if (type != null && type.isNonConsumable()) {
            event.setCancelled(true);
            Core.notifier.message(event.getPlayer(), true, "Cannot drop this item");
        }
    }

    /**
     * Prevent swapping non-consumable items to offhand (F key)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (!Core.session.state().isIngame()) return;

        ItemStack mainHand = event.getMainHandItem();
        ItemStack offHand = event.getOffHandItem();

        ItemType mainType = Core.itemRegistry.getType(mainHand);
        ItemType offType = Core.itemRegistry.getType(offHand);

        if ((mainType != null && mainType.isNonConsumable()) ||
                (offType != null && offType.isNonConsumable())) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevent moving/clicking non-consumable items in inventory
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!Core.session.state().isIngame()) return;

        Inventory clickedInv = event.getClickedInventory();
        if (clickedInv == null) return;
        if (!clickedInv.equals(player.getInventory())) return;

        int slot = event.getSlot();
        ClickType click = event.getClick();
        InventoryAction action = event.getAction();

        // Check protection status
        boolean isProtectedSlot = isProtectedSlot(slot, player);

        ItemStack cursor = event.getCursor();
        ItemType cursorType = Core.itemRegistry.getType(cursor);
        boolean cursorIsProtected = (cursorType != null && cursorType.isNonConsumable());

        ItemStack clicked = event.getCurrentItem();
        ItemType clickedType = Core.itemRegistry.getType(clicked);
        boolean clickedIsProtected = (clickedType != null && clickedType.isNonConsumable());

        // Rule 1: Cannot take non-consumable out of protected slot
        if (isProtectedSlot && clickedIsProtected) {
            event.setCancelled(true);
            return;
        }

        // Rule 2: Cannot place non-consumable in non-protected slot
        if (!isProtectedSlot && cursorIsProtected) {
            event.setCancelled(true);
            return;
        }

        // Rule 3: Block number key swaps involving protected slots
        if (click.name().contains("HOTBAR")) {
            int hotbarButton = event.getHotbarButton();
            if (hotbarButton >= 0) {
                boolean targetIsProtected = isProtectedSlot(hotbarButton, player);

                if (targetIsProtected || isProtectedSlot) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        // Rule 4: Block double-click collection
        if (click == ClickType.DOUBLE_CLICK || action == InventoryAction.COLLECT_TO_CURSOR) {
            if (cursorIsProtected || clickedIsProtected) {
                event.setCancelled(true);
                return;
            }
        }

        // Rule 5: Block move-to-other-inventory
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            if (clickedIsProtected) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /**
     * Prevent dragging non-consumable items
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!Core.session.state().isIngame()) return;

        ItemStack dragged = event.getOldCursor();
        ItemType type = Core.itemRegistry.getType(dragged);

        if (type != null && type.isNonConsumable()) {
            event.setCancelled(true);
        }
    }

    /**
     * Keep non-consumables on death
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!Core.session.state().isIngame()) return;

        event.getDrops().removeIf(item -> {
            ItemType type = Core.itemRegistry.getType(item);
            return type != null && type.isNonConsumable();
        });
    }

    /**
     * Check if a slot is protected based on current hotbar swap state
     */
    private boolean isProtectedSlot(int slot, Player player) {
        PlayerGameState state = Core.playerStates.getOrCreate(player.getUniqueId());

        if (state.isHotbarSwapped()) {
            return PROTECTED_SLOTS_SWAPPED.contains(slot);
        } else {
            return PROTECTED_SLOTS_NORMAL.contains(slot);
        }
    }
}