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
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

public final class GuiClickListener extends BaseListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        final InventoryView view = e.getView();
        final Inventory top = view.getTopInventory();
        if (!(top.getHolder() instanceof GroundZeroMenuHolder holder)) return;

        boolean playerInitiated = false;

        final int topSize = top.getSize();
        final int raw = e.getRawSlot();
        final boolean inTop = raw >= 0 && raw < topSize;
        final ClickType click = e.getClick();
        final InventoryAction action = e.getAction();
        final MenuType mt = holder.type();

        // ==== RULES OVERVIEW ====
        // - Top (GUI): allow only LEFT/RIGHT clicks; always cancel default movement.
        // - Bottom (player inv): allow most actions, except ones that can affect the Top:
        //     * DOUBLE_CLICK / COLLECT_TO_CURSOR (pulls matching items from Top) -> cancel
        //     * SHIFT-click move to Top (MOVE_TO_OTHER_INVENTORY) -> cancel

        if (inTop) {
            // Allow only LEFT/RIGHT on GUI; block all others including shift/number-key/double-click/etc.
            boolean allowed = (click == ClickType.LEFT || click == ClickType.RIGHT);
            if (!allowed) {
                e.setCancelled(true);
                return;
            }
            // Prevent vanilla item transfer on GUI slots; we only interpret the click.
            e.setCancelled(true);

            // Route by menu type
            switch (mt) {
                case MAP_SIZE -> {
                    // Cancel button is only active in voting menus
                    for (MapSizeOption opt : MapSizeOption.values()) {
                        if (opt.slot == raw) {
                            Core.voteService.voteMapSize(p.getUniqueId(), opt);
                            break;
                        }
                    }
                    if (raw == 26) {
                        Core.game.cancel(p);
                        p.closeInventory();
                        return;
                    }
                }
                case INCOME_MULTIPLIER -> {
                    for (IncomeOption opt : IncomeOption.values()) {
                        if (opt.slot == raw) {
                            Core.voteService.voteIncome(p.getUniqueId(), opt);
                            break;
                        }
                    }
                    if (raw == 26) {
                        Core.game.cancel(p);
                        p.closeInventory();
                        return;
                    }
                }
                case GAME_MODE -> {
                    for (GameModeOption opt : GameModeOption.values()) {
                        if (opt.slot == raw) {
                            Core.voteService.voteGameMode(p.getUniqueId(), opt);
                            break;
                        }
                    }
                    if (raw == 26) {
                        Core.game.cancel(p);
                        p.closeInventory();
                        return;
                    }
                }
                case SHOP -> {
                    // TODO: shop action routing by slot
                }
                default -> {
                    // For any future GUI types, keep the same default behavior (no movement).
                }
            }
            return;
        }

        // === Bottom (player inventory) area ===
        // Allow normal interactions EXCEPT those that could affect the Top GUI.

        // 1) Block double-click collect behavior that would vacuum items from Top.
        if (click == ClickType.DOUBLE_CLICK || action == InventoryAction.COLLECT_TO_CURSOR) {
            e.setCancelled(true);
            return;
        }

        // 2) Block shift-click "move to other inventory" (would try to move into Top GUI).
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            e.setCancelled(true);
            return;
        }

        // Everything else (regular pickup/place, number key swaps within bottom, drops, etc.) is allowed.
        // NOTE: If you later detect any specific exploits that still move items into Top,
        // add targeted guards here (e.g., HOTBAR_SWAP/HOTBAR_MOVE_AND_READD when raw mapping hits Top).
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        final InventoryView view = e.getView();
        final Inventory top = view.getTopInventory();
        if (!(top.getHolder() instanceof GroundZeroMenuHolder)) return;

        final int topSize = top.getSize();
        // Cancel only if any raw slot touched is in the Top inventory.
        boolean affectsTop = e.getRawSlots().stream().anyMatch(raw -> raw >= 0 && raw < topSize);
        if (affectsTop) {
            e.setCancelled(true);
        }
        // Else: allow drags confined to the bottom inventory.
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        final Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof GroundZeroMenuHolder holder)) return;

        if (e.getReason() != InventoryCloseEvent.Reason.PLAYER) return;

        // Auto-reopen only when voting is active for that specific GUI.
        switch (holder.type()) {
            case MAP_SIZE -> Core.schedulers.runLater(() -> {
                if (Core.voteService.isVotingMapSize()) Core.guiService.openMapSize(p);
            }, 1L);
            case INCOME_MULTIPLIER -> Core.schedulers.runLater(() -> {
                if (Core.voteService.isVotingIncome()) Core.guiService.openIncome(p);
            }, 1L);
            case GAME_MODE -> Core.schedulers.runLater(() -> {
                if (Core.voteService.isVotingGameMode()) Core.guiService.openGameMode(p);
            }, 1L);
            default -> { /* No auto-reopen for other GUI types */ }
        }
    }
}
