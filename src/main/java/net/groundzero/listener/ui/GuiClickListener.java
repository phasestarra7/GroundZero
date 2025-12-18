package net.groundzero.listener.ui;

import net.groundzero.app.Core;
import net.groundzero.listener.BaseListener;
import net.groundzero.ui.MenuType;
import net.groundzero.ui.holder.GroundZeroMenuHolder;
import net.groundzero.ui.options.GameModeOption;
import net.groundzero.ui.options.IncomeOption;
import net.groundzero.ui.options.MapSizeOption;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

/**
 * Handles interactions for OUR custom GUIs only (voting menus and shop).
 *
 * Responsibility: Protect our GUI integrity
 * - Top inventory: ONLY LEFT/RIGHT clicks
 * - Bottom inventory: Block actions that affect top GUI
 * - Drag: Block any drag involving top inventory
 *
 * Does NOT handle:
 * - Protected slot protection (InventoryProtectionListener)
 * - External GUIs (chests, furnaces, etc.)
 */
public final class GuiClickListener extends BaseListener implements Listener {

    /* ==================== INVENTORY CLICK ==================== */

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        final InventoryView view = e.getView();
        final Inventory top = view.getTopInventory();

        // Only handle our custom GUIs
        if (!(top.getHolder() instanceof GroundZeroMenuHolder holder)) return;

        // Always cancel first - we handle everything manually
        e.setCancelled(true);

        final int topSize = top.getSize();
        final int raw = e.getRawSlot();
        final boolean inTop = raw >= 0 && raw < topSize;
        final ClickType click = e.getClick();
        final InventoryAction action = e.getAction();
        final MenuType mt = holder.type();

        /* ===== TOP INVENTORY (Our GUI) ===== */
        if (inTop) {
            // ONLY allow LEFT and RIGHT clicks
            if (click != ClickType.LEFT && click != ClickType.RIGHT) {
                return; // Block everything else
            }

            // Route to appropriate handler
            switch (mt) {
                case MAP_SIZE -> {
                    for (MapSizeOption opt : MapSizeOption.values()) {
                        if (opt.slot == raw) {
                            Core.voteService.voteMapSize(p.getUniqueId(), opt);
                            return;
                        }
                    }
                    // Cancel button
                    if (raw == 26) {
                        Core.game.cancelPregame(p);
                        p.closeInventory();
                    }
                }
                case INCOME_MULTIPLIER -> {
                    for (IncomeOption opt : IncomeOption.values()) {
                        if (opt.slot == raw) {
                            Core.voteService.voteIncome(p.getUniqueId(), opt);
                            return;
                        }
                    }
                    if (raw == 26) {
                        Core.game.cancelPregame(p);
                        p.closeInventory();
                    }
                }
                case GAME_MODE -> {
                    for (GameModeOption opt : GameModeOption.values()) {
                        if (opt.slot == raw) {
                            Core.voteService.voteGameMode(p.getUniqueId(), opt);
                            return;
                        }
                    }
                    if (raw == 26) {
                        Core.game.cancelPregame(p);
                        p.closeInventory();
                    }
                }
                case SHOP -> {
                    // TODO: Shop click routing
                    // Example: if (raw == WEAPON_CATEGORY_SLOT) { openWeaponShop(p); }
                }
            }
            return;
        }

        /* ===== BOTTOM INVENTORY (Player) ===== */
        // Block actions that can affect top GUI

        // 1. Shift-click (moves to top)
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            return;
        }

        // 2. Double-click (collects from top)
        if (click == ClickType.DOUBLE_CLICK || action == InventoryAction.COLLECT_TO_CURSOR) {
            return;
        }

        // 3. Hotbar swap actions (can affect top)
        if (action == InventoryAction.HOTBAR_SWAP ||
                action == InventoryAction.HOTBAR_MOVE_AND_READD) {
            return;
        }

        // 4. Unknown actions (safety)
        if (action == InventoryAction.UNKNOWN) {
            return;
        }

        // Allow safe bottom-only interactions
        // (PICKUP, PLACE, DROP, SWAP_OFFHAND, etc.)
        switch (action) {
            case PICKUP_ALL:
            case PICKUP_HALF:
            case PICKUP_ONE:
            case PICKUP_SOME:
            case PLACE_ALL:
            case PLACE_ONE:
            case PLACE_SOME:
            case SWAP_WITH_CURSOR:
            case DROP_ALL_CURSOR:
            case DROP_ONE_CURSOR:
            case DROP_ALL_SLOT:
            case DROP_ONE_SLOT:
            case NOTHING:
                // These only affect bottom inventory - safe
                e.setCancelled(false);
                break;
            default:
                // Everything else stays cancelled
                break;
        }
    }

    /* ==================== INVENTORY DRAG ==================== */

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;

        final InventoryView view = e.getView();
        final Inventory top = view.getTopInventory();

        // Only handle our custom GUIs
        if (!(top.getHolder() instanceof GroundZeroMenuHolder)) return;

        final int topSize = top.getSize();

        // Block if ANY dragged slot is in top inventory
        for (int rawSlot : e.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < topSize) {
                e.setCancelled(true);
                return;
            }
        }

        // Allow bottom-only drags
    }

    /* ==================== AUTO-REOPEN FOR VOTING ==================== */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;

        final Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof GroundZeroMenuHolder holder)) return;

        // Only reopen if player manually closed
        if (e.getReason() != InventoryCloseEvent.Reason.PLAYER) return;

        // Auto-reopen only during active voting
        switch (holder.type()) {
            case MAP_SIZE -> Core.schedulers.runLater(() -> {
                if (p.isOnline() && Core.voteService.isVotingMapSize()) {
                    Core.guiService.openMapSize(p);
                }
            }, 1L);

            case INCOME_MULTIPLIER -> Core.schedulers.runLater(() -> {
                if (p.isOnline() && Core.voteService.isVotingIncome()) {
                    Core.guiService.openIncome(p);
                }
            }, 1L);

            case GAME_MODE -> Core.schedulers.runLater(() -> {
                if (p.isOnline() && Core.voteService.isVotingGameMode()) {
                    Core.guiService.openGameMode(p);
                }
            }, 1L);

            case SHOP -> {
                // Shop can be closed freely
            }
        }
    }
}