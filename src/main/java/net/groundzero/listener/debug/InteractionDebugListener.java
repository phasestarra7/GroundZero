package net.groundzero.listener.debug;

import net.groundzero.app.Core;
import net.groundzero.item.ItemType;
import net.groundzero.listener.BaseListener;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * DEBUG ONLY - Remove in production
 *
 * Traces all interaction events to diagnose:
 * 1. ARM_SWING → DROP timing issue
 * 2. Protected slot not working in shop GUI
 * 3. Vanilla action conflicts
 */
public class InteractionDebugListener extends BaseListener implements Listener {

    private static final String PREFIX = "§e[DEBUG]§f ";
    private static final boolean ENABLED = true; // Toggle debugging

    /* ==================== ARM_SWING ==================== */

    @EventHandler(priority = EventPriority.LOWEST)
    public void onArmSwing(PlayerAnimationEvent event) {
        if (!ENABLED) return;

        Player player = event.getPlayer();
        int tick = Bukkit.getCurrentTick();
        ItemStack item = player.getInventory().getItemInMainHand();
        ItemType type = Core.itemRegistry.getType(item);

        log(tick, "ARM_SWING",
                "Player: " + player.getName(),
                "Item: " + (type != null ? type.id : "vanilla/" + item.getType()),
                "Priority: LOWEST");
    }

    /* ==================== PLAYER INTERACT ==================== */

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractLowest(PlayerInteractEvent event) {
        if (!ENABLED) return;
        logInteract(event, "LOWEST");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteractNormal(PlayerInteractEvent event) {
        if (!ENABLED) return;
        logInteract(event, "NORMAL");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractHighest(PlayerInteractEvent event) {
        if (!ENABLED) return;
        logInteract(event, "HIGHEST");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteractMonitor(PlayerInteractEvent event) {
        if (!ENABLED) return;
        logInteract(event, "MONITOR");
    }

    private void logInteract(PlayerInteractEvent event, String priority) {
        Player player = event.getPlayer();
        int tick = Bukkit.getCurrentTick();
        Action action = event.getAction();
        ItemStack item = event.getItem();
        ItemType type = item != null ? Core.itemRegistry.getType(item) : null;

        String blockInfo = "";
        if (event.getClickedBlock() != null) {
            blockInfo = "Block: " + event.getClickedBlock().getType();
        }

        log(tick, "PLAYER_INTERACT",
                "Player: " + player.getName(),
                "Action: " + action,
                "Item: " + (type != null ? type.id : (item != null ? "vanilla/" + item.getType() : "null")),
                blockInfo,
                "Cancelled: " + event.isCancelled(),
                "Priority: " + priority);
    }

    /* ==================== DROP ==================== */

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDropLowest(PlayerDropItemEvent event) {
        if (!ENABLED) return;
        logDrop(event, "LOWEST");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDropHighest(PlayerDropItemEvent event) {
        if (!ENABLED) return;
        logDrop(event, "HIGHEST");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDropMonitor(PlayerDropItemEvent event) {
        if (!ENABLED) return;
        logDrop(event, "MONITOR");
    }

    private void logDrop(PlayerDropItemEvent event, String priority) {
        Player player = event.getPlayer();
        int tick = Bukkit.getCurrentTick();
        ItemStack item = event.getItemDrop().getItemStack();
        ItemType type = Core.itemRegistry.getType(item);

        log(tick, "PLAYER_DROP",
                "Player: " + player.getName(),
                "Item: " + (type != null ? type.id : "vanilla/" + item.getType()),
                "Cancelled: " + event.isCancelled(),
                "Priority: " + priority);
    }

    /* ==================== ENTITY DAMAGE ==================== */

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!ENABLED) return;
        if (!(event.getDamager() instanceof Player player)) return;

        int tick = Bukkit.getCurrentTick();
        ItemStack item = player.getInventory().getItemInMainHand();
        ItemType type = Core.itemRegistry.getType(item);

        log(tick, "ENTITY_DAMAGE",
                "Player: " + player.getName(),
                "Target: " + event.getEntity().getType(),
                "Item: " + (type != null ? type.id : "vanilla/" + item.getType()),
                "Cancelled: " + event.isCancelled(),
                "Priority: HIGH");
    }

    /* ==================== INVENTORY CLICK ==================== */

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClickHighest(InventoryClickEvent event) {
        if (!ENABLED) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int tick = Bukkit.getCurrentTick();
        int slot = event.getSlot();
        int rawSlot = event.getRawSlot();
        String clickedInv = event.getClickedInventory() != null
                ? (event.getClickedInventory().equals(player.getInventory()) ? "PLAYER" : "OTHER")
                : "NULL";

        String holderInfo = "";
        if (event.getView().getTopInventory().getHolder() != null) {
            holderInfo = "Holder: " + event.getView().getTopInventory().getHolder().getClass().getSimpleName();
        }

        log(tick, "INVENTORY_CLICK",
                "Player: " + player.getName(),
                "Click: " + event.getClick(),
                "Action: " + event.getAction(),
                "Slot: " + slot,
                "RawSlot: " + rawSlot,
                "Inventory: " + clickedInv,
                holderInfo,
                "Cancelled: " + event.isCancelled(),
                "Priority: HIGHEST");
    }

    /* ==================== HELPER ==================== */

    private void log(int tick, String eventName, String... details) {
        StringBuilder sb = new StringBuilder();
        sb.append(PREFIX);
        sb.append("§6[Tick ").append(tick).append("]§f ");
        sb.append("§b").append(eventName).append("§f");

        for (String detail : details) {
            if (detail != null && !detail.isEmpty()) {
                sb.append("\n  §7→ §f").append(detail);
            }
        }

        Bukkit.getConsoleSender().sendMessage(sb.toString());
    }

    /* ==================== CONTROL ==================== */

    /**
     * Enable/disable debugging via command
     * Usage: /gz debug on|off
     */
    public static void setEnabled(boolean enabled) {
        // TODO: Implement if needed
    }
}