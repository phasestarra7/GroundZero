package net.groundzero.listener.debug;

import net.groundzero.app.Core;
import net.groundzero.item.ItemType;
import net.groundzero.listener.BaseListener;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/**
 * DEBUG ONLY - Remove in production
 *
 * Focus: Q-drop / fake left click / creative inventory semantics.
 *
 * Events logged:
 * - ARM_SWING (PlayerAnimationEvent)
 * - INTERACT (PlayerInteractEvent)
 * - DROP (PlayerDropItemEvent)
 * - INV_CLICK (InventoryClickEvent)
 * - INV_CREATIVE (InventoryCreativeEvent)
 *
 * Every log prints:
 * - Tick, player, gamemode
 * - Open inventory view (top/bottom type + title)
 * - Event-specific fields
 * - Item snapshots: eventItem / currentItem / cursor(event) / cursor(player) / mainHand / offHand
 */
public final class InteractionDebugListener extends BaseListener implements Listener {

    private static final String PREFIX = "§e[DEBUG]§f ";
    private static final boolean ENABLED = true;

    /* ==================== ARM SWING ==================== */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onArmSwing(PlayerAnimationEvent event) {
        if (!ENABLED) return;

        Player p = event.getPlayer();
        int tick = Bukkit.getCurrentTick();

        log(tick, "ARM_SWING", p,
                "Anim: " + event.getAnimationType()
        );
    }

    /* ==================== INTERACT ==================== */

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInteractLowest(PlayerInteractEvent event) {
        if (!ENABLED) return;
        logInteract(event, "LOWEST");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteractHighest(PlayerInteractEvent event) {
        if (!ENABLED) return;
        logInteract(event, "HIGHEST");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInteractMonitor(PlayerInteractEvent event) {
        if (!ENABLED) return;
        logInteract(event, "MONITOR");
    }

    private void logInteract(PlayerInteractEvent event, String prio) {
        Player p = event.getPlayer();
        int tick = Bukkit.getCurrentTick();

        String blockInfo = (event.getClickedBlock() != null)
                ? ("ClickedBlock: " + event.getClickedBlock().getType())
                : "ClickedBlock: null";

        String handInfo = "Hand: " + (event.getHand() != null ? event.getHand() : "null");

        log(tick, "INTERACT", p,
                "Priority: " + prio,
                "Action: " + event.getAction(),
                handInfo,
                blockInfo,
                "UseItemInHand: " + event.useItemInHand(),
                "UseInteractedBlock: " + event.useInteractedBlock(),
                "Cancelled: " + event.isCancelled(),
                // Important: event.getItem() can differ from actual main hand in some cases
                "EventItem: " + fmtItem(event.getItem()),
                "MainHand: " + fmtItem(p.getInventory().getItemInMainHand()),
                "OffHand: " + fmtItem(p.getInventory().getItemInOffHand())
        );
    }

    /* ==================== DROP ==================== */

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onDropLowest(PlayerDropItemEvent event) {
        if (!ENABLED) return;
        logDrop(event, "LOWEST");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDropHighest(PlayerDropItemEvent event) {
        if (!ENABLED) return;
        logDrop(event, "HIGHEST");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onDropMonitor(PlayerDropItemEvent event) {
        if (!ENABLED) return;
        logDrop(event, "MONITOR");
    }

    private void logDrop(PlayerDropItemEvent event, String prio) {
        Player p = event.getPlayer();
        int tick = Bukkit.getCurrentTick();

        log(tick, "DROP", p,
                "Priority: " + prio,
                "DropStack: " + fmtItem(event.getItemDrop() != null ? event.getItemDrop().getItemStack() : null),
                "Cancelled: " + event.isCancelled(),
                "MainHand: " + fmtItem(p.getInventory().getItemInMainHand()),
                "OffHand: " + fmtItem(p.getInventory().getItemInOffHand())
        );
    }

    /* ==================== INVENTORY CLICK ==================== */

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInvClickLowest(InventoryClickEvent event) {
        if (!ENABLED) return;
        logInvClick(event, "LOWEST");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInvClickHighest(InventoryClickEvent event) {
        if (!ENABLED) return;
        logInvClick(event, "HIGHEST");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInvClickMonitor(InventoryClickEvent event) {
        if (!ENABLED) return;
        logInvClick(event, "MONITOR");
    }

    private void logInvClick(InventoryClickEvent event, String prio) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        int tick = Bukkit.getCurrentTick();

        Inventory clickedInv = event.getClickedInventory();
        String clickedInvKind;
        if (clickedInv == null) clickedInvKind = "NULL";
        else if (clickedInv.equals(p.getInventory())) clickedInvKind = "PLAYER";
        else clickedInvKind = "OTHER(" + clickedInv.getType() + ")";

        int raw = event.getRawSlot();
        int slot = event.getSlot();

        String hb = "HotbarButton: " + event.getHotbarButton();
        String hotbarLike = "IsHotbarClick: " + (event.getClick().name().contains("HOTBAR") || event.getClick() == ClickType.NUMBER_KEY);

        log(tick, "INV_CLICK", p,
                "Priority: " + prio,
                "Click: " + event.getClick(),
                "Action: " + event.getAction(),
                "Slot: " + slot + " Raw: " + raw,
                hb,
                hotbarLike,
                "ClickedInv: " + clickedInvKind,
                "Cancelled: " + event.isCancelled(),
                "CurrentItem: " + fmtItem(event.getCurrentItem()),
                "Cursor(event): " + fmtItem(event.getCursor()),
                "Cursor(player): " + fmtItem(p.getItemOnCursor()),
                "MainHand: " + fmtItem(p.getInventory().getItemInMainHand()),
                "OffHand: " + fmtItem(p.getInventory().getItemInOffHand())
        );
    }

    /* ==================== INVENTORY CREATIVE ==================== */

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInvCreativeLowest(InventoryCreativeEvent event) {
        if (!ENABLED) return;
        logInvCreative(event, "LOWEST");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInvCreativeHighest(InventoryCreativeEvent event) {
        if (!ENABLED) return;
        logInvCreative(event, "HIGHEST");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInvCreativeMonitor(InventoryCreativeEvent event) {
        if (!ENABLED) return;
        logInvCreative(event, "MONITOR");
    }

    private void logInvCreative(InventoryCreativeEvent event, String prio) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        int tick = Bukkit.getCurrentTick();

        int raw = event.getRawSlot();
        int slot = event.getSlot();

        log(tick, "INV_CREATIVE", p,
                "Priority: " + prio,
                "Click: " + event.getClick(),
                "Action: " + event.getAction(),
                "Slot: " + slot + " Raw: " + raw,
                "Cancelled: " + event.isCancelled(),
                "CurrentItem: " + fmtItem(event.getCurrentItem()),
                "Cursor(event): " + fmtItem(event.getCursor()),
                "Cursor(player): " + fmtItem(p.getItemOnCursor()),
                "MainHand: " + fmtItem(p.getInventory().getItemInMainHand()),
                "OffHand: " + fmtItem(p.getInventory().getItemInOffHand())
        );
    }

    /* ==================== COMMON LOG ==================== */

    private void log(int tick, String name, Player p, String... details) {
        StringBuilder sb = new StringBuilder(256);
        sb.append(PREFIX).append("§6[Tick ").append(tick).append("]§f ");
        sb.append("§b").append(name).append("§f");
        sb.append("\n  §7→ §fPlayer: ").append(p.getName());
        sb.append("\n  §7→ §fGameMode: ").append(p.getGameMode());

        // Open view snapshot (this is still valuable even if CRAFTING == E)
        // Because TOP inventory type/title will differ for chests/custom GUIs.
        InventoryView view = p.getOpenInventory();
        Inventory top = view != null ? view.getTopInventory() : null;
        Inventory bottom = view != null ? view.getBottomInventory() : null;

        String topType = (top != null ? top.getType().name() : "null");
        String bottomType = (bottom != null ? bottom.getType().name() : "null");
        String title = (view != null ? view.getTitle() : "null");

        sb.append("\n  §7→ §fViewTop: ").append(topType);
        sb.append("\n  §7→ §fViewBottom: ").append(bottomType);
        sb.append("\n  §7→ §fViewTitle: ").append(title);

        for (String d : details) {
            if (d != null && !d.isEmpty()) {
                sb.append("\n  §7→ §f").append(d);
            }
        }

        Bukkit.getConsoleSender().sendMessage(sb.toString());
    }

    private String fmtItem(ItemStack item) {
        if (item == null) return "null";
        if (item.getType().isAir()) return "AIR";

        ItemType gz = Core.itemRegistry.getType(item);
        String gzId = (gz != null ? gz.id : "vanilla");

        // Include amount + material + gzId
        return item.getType() + "x" + item.getAmount() + " (" + gzId + ")";
    }
}
