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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/**
 * Protects non-consumable items (4 weapons + console) in ALL situations.
 *
 * Responsibility: Absolute protection of non-consumables
 * - Cannot be dropped (Q/Ctrl+Q)
 * - Cannot be swapped to offhand (F)
 * - Cannot be moved from protected slots
 * - Cannot be lost on death/quit/inventory close
 * - Safe cursor recovery
 *
 * Protected slot layout:
 * - Normal mode: 0,1,2,3,8 (4 weapons + console)
 * - Swapped mode: 27,28,29,30,8 (weapons moved, console stays)
 */
public final class InventoryProtectionListener extends BaseListener implements Listener {

    private static final Set<Integer> PROTECTED_SLOTS_NORMAL = Set.of(0, 1, 2, 3, 8);
    private static final Set<Integer> PROTECTED_SLOTS_SWAPPED = Set.of(27, 28, 29, 30, 8);

    /* ==================== DROP PROTECTION (Q/Ctrl+Q) ==================== */

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

    /* ==================== OFFHAND SWAP PROTECTION (F) ==================== */

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

    /* ==================== INVENTORY CLICK PROTECTION ==================== */

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!Core.session.state().isIngame()) return;

        Inventory clickedInv = event.getClickedInventory();
        int slot = event.getSlot();
        ClickType click = event.getClick();
        InventoryAction action = event.getAction();

        /* ===== GLOBAL: Hotbar number key check ===== */
        // This applies regardless of which inventory was clicked
        if (click.name().contains("HOTBAR") || click == ClickType.NUMBER_KEY) {
            int hotbarButton = event.getHotbarButton();
            if (hotbarButton >= 0 && hotbarButton <= 8) {
                if (isProtectedSlot(hotbarButton, player)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        /* ===== PLAYER INVENTORY ONLY ===== */
        if (clickedInv == null || !clickedInv.equals(player.getInventory())) {
            return; // Not player inventory - ignore (chest, furnace, etc.)
        }

        /* ===== PROTECTED SLOT: Block ALL interactions ===== */
        boolean clickedSlotIsProtected = isProtectedSlot(slot, player);
        if (clickedSlotIsProtected) {
            event.setCancelled(true);
            return;
        }

        /* ===== PROTECTED ITEM DANGER CHECK ===== */
        ItemStack cursor = event.getCursor();
        ItemType cursorType = Core.itemRegistry.getType(cursor);
        boolean cursorIsProtected = (cursorType != null && cursorType.isNonConsumable());

        ItemStack clicked = event.getCurrentItem();
        ItemType clickedType = Core.itemRegistry.getType(clicked);
        boolean clickedItemIsProtected = (clickedType != null && clickedType.isNonConsumable());

        // 1. Cannot place protected item in non-protected slot
        if (cursorIsProtected && !clickedSlotIsProtected) {
            event.setCancelled(true);
            return;
        }

        // 2. Cannot collect protected items with double-click
        if (clickedItemIsProtected &&
                (click == ClickType.DOUBLE_CLICK || action == InventoryAction.COLLECT_TO_CURSOR)) {
            event.setCancelled(true);
            return;
        }

        // 3. Cannot drop protected items from inventory
        if (clickedItemIsProtected &&
                (click == ClickType.DROP || click == ClickType.CONTROL_DROP)) {
            event.setCancelled(true);
            return;
        }

        // 4. Block all other dangerous actions with protected items
        if (clickedItemIsProtected || cursorIsProtected) {
            switch (action) {
                case MOVE_TO_OTHER_INVENTORY:
                case HOTBAR_SWAP:
                case HOTBAR_MOVE_AND_READD:
                case CLONE_STACK:
                case UNKNOWN:
                    event.setCancelled(true);
                    return;
            }
        }
    }

    /* ==================== DRAG PROTECTION ==================== */

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!Core.session.state().isIngame()) return;

        ItemStack dragged = event.getOldCursor();
        ItemType type = Core.itemRegistry.getType(dragged);

        // 1. Cannot drag protected items
        if (type != null && type.isNonConsumable()) {
            event.setCancelled(true);
            return;
        }

        // 2. Cannot drag into protected slots
        Inventory playerInv = player.getInventory();
        for (int rawSlot : event.getRawSlots()) {
            // Convert raw slot to actual inventory slot
            // This is tricky - need to check if it's in player inventory first
            if (rawSlot < 0) continue;

            try {
                // Check if this raw slot corresponds to player inventory
                Inventory inv = event.getView().getInventory(rawSlot);
                if (inv != null && inv.equals(playerInv)) {
                    // Convert to actual slot
                    int actualSlot = event.getView().convertSlot(rawSlot);
                    if (isProtectedSlot(actualSlot, player)) {
                        event.setCancelled(true);
                        return;
                    }
                }
            } catch (Exception ignored) {
                // If conversion fails, be safe and cancel
                event.setCancelled(true);
                return;
            }
        }
    }

    /* ==================== CURSOR RECOVERY ON INVENTORY CLOSE ==================== */

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!Core.session.state().isIngame()) return;

        ItemStack cursor = player.getItemOnCursor();
        if (cursor == null || cursor.getType().isAir()) return;

        ItemType type = Core.itemRegistry.getType(cursor);
        if (type == null || !type.isNonConsumable()) return;

        // Protected item in cursor - must recover
        restoreProtectedItemFromCursor(player, cursor, type);
    }

    /* ==================== DEATH PROTECTION ==================== */

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!Core.session.state().isIngame()) return;

        Player player = event.getEntity();

        // 1. Clear protected item from cursor if present
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && !cursor.getType().isAir()) {
            ItemType cursorType = Core.itemRegistry.getType(cursor);
            if (cursorType != null && cursorType.isNonConsumable()) {
                player.setItemOnCursor(null);
            }
        }

        // 2. Remove protected items from drops
        event.getDrops().removeIf(item -> {
            ItemType type = Core.itemRegistry.getType(item);
            return type != null && type.isNonConsumable();
        });
    }

    /* ==================== QUIT PROTECTION ==================== */

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!Core.session.state().isIngame()) return;

        Player player = event.getPlayer();
        ItemStack cursor = player.getItemOnCursor();
        if (cursor == null || cursor.getType().isAir()) return;

        ItemType type = Core.itemRegistry.getType(cursor);
        if (type == null || !type.isNonConsumable()) return;

        // Protected item in cursor - restore before quit
        restoreProtectedItemFromCursor(player, cursor, type);
    }

    /* ==================== HELPER METHODS ==================== */

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

    /**
     * Restore protected item from cursor to its proper protected slot
     */
    private void restoreProtectedItemFromCursor(Player player, ItemStack cursor, ItemType type) {
        // Clear cursor first
        player.setItemOnCursor(null);

        // Get protected slots based on current state
        PlayerGameState state = Core.playerStates.getOrCreate(player.getUniqueId());
        Set<Integer> protectedSlots = state.isHotbarSwapped()
                ? PROTECTED_SLOTS_SWAPPED
                : PROTECTED_SLOTS_NORMAL;

        Inventory inv = player.getInventory();

        // Strategy 1: Find the slot that should contain this item type
        for (int slot : protectedSlots) {
            ItemStack existing = inv.getItem(slot);

            // Empty slot - place here
            if (existing == null || existing.getType().isAir()) {
                inv.setItem(slot, cursor);
                return;
            }

            // Same type - this is the correct slot
            ItemType existingType = Core.itemRegistry.getType(existing);
            if (existingType != null && existingType == type) {
                inv.setItem(slot, cursor);
                return;
            }
        }

        // Strategy 2: Force place in first protected slot (should never happen)
        int firstSlot = protectedSlots.iterator().next();
        inv.setItem(firstSlot, cursor);
    }
}