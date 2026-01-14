package net.groundzero.listener.player;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import net.groundzero.app.Core;
import net.groundzero.item.ItemType;
import net.groundzero.item.handler.ItemHandler;
import net.groundzero.listener.BaseListener;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
 *
 * IMPORTANT : there is a known packet-level paper bug that dropping item triggers left_click_air
 * We are using ServerTickEndEvent as a heuristic, so if multiple inputs are in a single tick, we can't fix it
 * Also, the way "dropping item" acts in SURVIVAL mode and CREATIVE mode is different
 * So we just ignore CREATIVE mode's bug; it doesn't affect normal gameplay
 */
public final class ItemInteractionListener extends BaseListener implements Listener {

    /**
     * Tracks recent DROP events to filter fake LEFT_CLICK_AIR
     * Q key in air: DROP → LEFT_CLICK_AIR (fake)
     */
    private final Set<UUID> recentDrop = new HashSet<>();

    // Defer LEFT_CLICK_AIR to tick end so we can detect "inventory Q-drop" where DROP happens AFTER Interact
    private final Map<UUID, PendingLeftClickAir> pendingLeftClickAir = new HashMap<>();

    // Stores deferred LEFT_CLICK_AIR context until ServerTickEndEvent
    private static final class PendingLeftClickAir {
        final ItemHandler handler;
        final ItemType type;
        final ItemStack itemSnapshot;

        PendingLeftClickAir(ItemHandler handler, ItemType type, ItemStack itemSnapshot) {
            this.handler = handler;
            this.type = type;
            this.itemSnapshot = itemSnapshot;
        }
    }

    // Marks a player as "dropped this tick window" and schedules cleanup
    private void markRecentDrop(Player player) {
        UUID id = player.getUniqueId();
        recentDrop.add(id);

        // Cleanup after 2 ticks (safety margin)
        Core.schedulers.runLater(() -> recentDrop.remove(id), 2L);
    }

    /* ==================== DROP: FLAG SETTER ==================== */

    /**
     * Mark player as recently dropped
     * This prevents fake LEFT_CLICK_AIR from triggering handlers
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        markRecentDrop(event.getPlayer());
    }

    // Inventory Q / Ctrl+Q drops happen via InventoryClickEvent (and may occur after PlayerInteractEvent)
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryDropClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!Core.session.state().isIngame()) return;

        ClickType click = event.getClick();
        InventoryAction action = event.getAction();

        boolean isDrop =
                click == ClickType.DROP ||
                        click == ClickType.CONTROL_DROP ||
                        action == InventoryAction.DROP_ONE_SLOT ||
                        action == InventoryAction.DROP_ALL_SLOT ||
                        action == InventoryAction.DROP_ONE_CURSOR ||
                        action == InventoryAction.DROP_ALL_CURSOR;

        if (isDrop) {
            markRecentDrop(player);
        }
    }

    // Creative inventory has its own event; still mark drops here to be safe
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCreativeDropClick(InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!Core.session.state().isIngame()) return;

        ClickType click = event.getClick();
        InventoryAction action = event.getAction();

        boolean isDrop =
                click == ClickType.DROP ||
                        click == ClickType.CONTROL_DROP ||
                        action == InventoryAction.DROP_ONE_SLOT ||
                        action == InventoryAction.DROP_ALL_SLOT ||
                        action == InventoryAction.DROP_ONE_CURSOR ||
                        action == InventoryAction.DROP_ALL_CURSOR;

        if (isDrop) {
            markRecentDrop(player);
        }
    }

    /* ==================== INTERACT: MAIN HANDLER ==================== */

    /**
     * Main interaction handler
     * Differentiates actions based on accuracy
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // Filter off-hand duplicate calls (1.9+)
        if (event.getHand() != EquipmentSlot.HAND) return;

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
                // event.setCancelled(true);
                // now just don't cancel
            }
        }

        /* ===== LEFT_CLICK_AIR: Inaccurate, check DROP flag ===== */
        else if (action == Action.LEFT_CLICK_AIR) {
            // Filter fake LEFT_CLICK_AIR from Q key
            if (recentDrop.contains(id)) {
                return; // Q key in air, ignore
            }

            // Defer to tick end: inventory Q-drop can set DROP flag AFTER this event
            pendingLeftClickAir.put(id, new PendingLeftClickAir(handler, type, item.clone()));

            // Only cancel vanilla if item has left-click functionality
            if (handled && type.hasLeftClickAction()) {
                // event.setCancelled(true);
                // now just don't cancel
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

    // Finalize deferred LEFT_CLICK_AIR at the end of the same server tick
    @EventHandler
    public void onTickEnd(ServerTickEndEvent event) {
        if (pendingLeftClickAir.isEmpty()) return;

        for (Map.Entry<UUID, PendingLeftClickAir> entry : pendingLeftClickAir.entrySet()) {
            UUID id = entry.getKey();

            // If any kind of drop happened in the same tick window, treat LEFT_CLICK_AIR as fake
            if (recentDrop.contains(id)) {
                continue;
            }

            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) continue;
            if (!Core.session.state().isIngame()) continue;

            PendingLeftClickAir pending = entry.getValue();
            pending.handler.onLeftClick(player, pending.itemSnapshot);
        }

        pendingLeftClickAir.clear();
    }

    /* ==================== ENTITY ATTACK: DIRECT HANDLER ==================== */

    /**
     * Handle entity attacks directly (no fake events here)
     *
     * IMPORTANT: Must check isProcessingDamage to prevent infinite loop!
     * When applyCustomDamage() calls victim.damage(amount, attacker),
     * it triggers a new EntityDamageByEntityEvent with Player as damager.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!Core.session.state().isIngame()) return;

        // CRITICAL: Skip if this is a re-triggered event from applyCustomDamage()
        // Without this check: left-click -> arrow -> hit -> applyCustomDamage ->
        // damage(amount, player) -> new event -> left-click -> infinite loop!
        if (event.getEntity() instanceof LivingEntity victim) {
            if (Core.damageService.isProcessingDamage(victim)) {
                return;
            }
        }

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
            // Cancel vanilla melee attack; was, but not now
            // event.setCancelled(true);
        }
    }

    /* ==================== ENTITY RIGHT CLICK: DIRECT HANDLER ==================== */

    /**
     * Handles right-click on entities (horses, villagers, boats, etc.).
     * PlayerInteractEvent often does NOT cover entity interactions reliably.
     *
     * IMPORTANT:
     * - Fires for both hands on 1.9+; filter to main hand to avoid double handling.
     * - Cancel to block vanilla interactions (mounting, trading, etc.) when handled.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!Core.session.state().isIngame()) return;

        // Filter off-hand duplicate calls
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) return;

        ItemType type = Core.itemRegistry.getType(item);
        if (type == null) return;

        ItemHandler handler = Core.itemRegistry.getHandler(type);
        if (handler == null) return;

        boolean handled = handler.onRightClick(player, item);

        // If handled, block vanilla entity interaction (mount/trade/etc.)
        if (handled) {
            event.setCancelled(true);
        }
    }
}
