package net.groundzero.listener.player;

import net.groundzero.app.Core;
import net.groundzero.listener.BaseListener;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;

/**
 * Triggers immediate ActionBar update when player switches hotbar slot.
 * Works with ActionBarService's hybrid update system.
 */
public final class PlayerItemHeldListener extends BaseListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemHeld(PlayerItemHeldEvent event) {
        if (!Core.session.state().isIngame()) return;

        // 1tick delay, item is changed after the event call
        // getItemInMainHand() returns previous item, so update after change
        Player player = event.getPlayer();
        Core.schedulers.runLater(() -> {
            if (player.isOnline() && Core.session.state().isIngame()) {
                Core.actionBarService.updateImmediately(player.getUniqueId());
            }
        }, 1L);
    }
}