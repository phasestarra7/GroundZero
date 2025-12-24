package net.groundzero.listener.player;

import net.groundzero.app.Core;
import net.groundzero.listener.BaseListener;
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

        // Immediate update on slot change
        Core.actionBarService.updateImmediately(event.getPlayer().getUniqueId());
    }
}