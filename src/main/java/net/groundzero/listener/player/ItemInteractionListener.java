package net.groundzero.listener.player;

import net.groundzero.listener.BaseListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemInteractionListener extends BaseListener implements Listener {

    // For future double-click (not used yet)
    private final ConcurrentHashMap<UUID, Long> lastClickAt = new ConcurrentHashMap<>();
    private static final long DOUBLE_CLICK_MS = 300L; // placeholder

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        var p = e.getPlayer();
        var item = e.getItem();
        Action a = e.getAction();

        boolean left = (a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK);
        boolean right = (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK);
        if (!left && !right) return;

        // TODO: Identify held item (by localizedName or PDC), then delegate:
        // if (isWeaponX(item)) Core.items.get("weaponX").use(...);

        // For now we do not cancel; GUI clicks are handled in GuiClickListener.
        // e.setCancelled(true/false) depending on weapon semantics later.
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        var p = e.getPlayer();
        var stack = e.getItemDrop().getItemStack();
        // TODO: If this item should not be droppable during certain phases, cancel here.
        // if (Core.game.state() != GameState.RUNNING) e.setCancelled(true);
        // TODO: Or if stack is a bound weapon: e.setCancelled(true);
    }

    // private boolean isDoubleClick(UUID id) { ... } // implement later when needed
}
