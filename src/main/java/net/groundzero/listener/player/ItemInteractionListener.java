package net.groundzero.listener.player;

import net.groundzero.app.Core;
import net.groundzero.item.ItemType;
import net.groundzero.item.handler.ItemHandler;
import net.groundzero.listener.BaseListener;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Routes player item interactions to appropriate handlers.
 *
 * PACKET-LEVEL UNDERSTANDING:
 * - LEFT_CLICK_BLOCK: Uses START_DESTROY_BLOCK packet (accurate)
 * - LEFT_CLICK_AIR: No packet available, reuses ARM_SWING (inaccurate!)
 * - RIGHT_CLICK_BLOCK: Uses USE_ITEM_ON packet (accurate)
 * - RIGHT_CLICK_AIR: Uses USE_ITEM packet (accurate)
 *
 * PROBLEM:
 * Q key in air triggers ARM_SWING → Paper creates fake LEFT_CLICK_AIR
 *
 * SOLUTION:
 * - LEFT_CLICK_BLOCK: Process immediately (accurate)
 * - LEFT_CLICK_AIR: Check DROP flag first (filter fake events)
 * - RIGHT_CLICK_*: Process immediately (accurate)
 */
public class ItemInteractionListener extends BaseListener implements Listener {

    /**
     * Tracks recent DROP events to filter fake LEFT_CLICK_AIR
     * Q key in air: DROP → LEFT_CLICK_AIR (fake)
     */
    private final Set<UUID> recentDrop = new HashSet<>();

    /* ==================== DROP: FLAG SETTER ==================== */

    /**
     * Mark player as recently dropped
     * This prevents fake LEFT_CLICK_AIR from triggering handlers
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        UUID id = event.getPlayer().getUniqueId();

        recentDrop.add(id);

        // Cleanup after 2 ticks (safety margin)
        Core.schedulers.runLater(() -> recentDrop.remove(id), 2L);
    }

    /* ==================== INTERACT: MAIN HANDLER ==================== */

    /**
     * Main interaction handler
     * Differentiates actions based on accuracy
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        Action action = event.getAction();

        // Only process during game
        if (!Core.session.state().isIngame()) return;

        // Item must exist
        if (item == null || item.getType().isAir()) return;

        // Must be a GZ item
        ItemType type = Core.itemRegistry.getType(item);
        if (type == null) return;

        // Get handler
        ItemHandler handler = Core.itemRegistry.getHandler(type);
        if (handler == null) return;

        boolean handled = false;
        UUID id = player.getUniqueId();

        /* ===== LEFT_CLICK_BLOCK: Accurate, process immediately ===== */
        if (action == Action.LEFT_CLICK_BLOCK) {
            handled = handler.onLeftClick(player, item);

            // Only cancel vanilla if item has left-click functionality
            if (handled && type.hasLeftClickAction()) {
                event.setCancelled(true);
            }
        }

        /* ===== LEFT_CLICK_AIR: Inaccurate, check DROP flag ===== */
        else if (action == Action.LEFT_CLICK_AIR) {
            // Filter fake LEFT_CLICK_AIR from Q key
            if (recentDrop.contains(id)) {
                return; // Q key in air, ignore
            }

            handled = handler.onLeftClick(player, item);

            // Only cancel vanilla if item has left-click functionality
            if (handled && type.hasLeftClickAction()) {
                event.setCancelled(true);
            }
        }

        /* ===== RIGHT_CLICK: Always accurate ===== */
        else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            handled = handler.onRightClick(player, item);

            // Always cancel vanilla for right-click (most items have functionality)
            if (handled) {
                event.setCancelled(true);
            }
        }
    }

    /* ==================== ENTITY ATTACK: DIRECT HANDLER ==================== */

    /**
     * Handle entity attacks directly (no fake events here)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!Core.session.state().isIngame()) return;

        // Get item in main hand
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) return;

        // Check if it's a GZ item
        ItemType type = Core.itemRegistry.getType(item);
        if (type == null) return;

        // Get handler
        ItemHandler handler = Core.itemRegistry.getHandler(type);
        if (handler == null) return;

        // Process left-click
        boolean handled = handler.onLeftClick(player, item);

        if (handled && type.hasLeftClickAction()) {
            // Cancel vanilla melee attack
            event.setCancelled(true);
        }
    }
}