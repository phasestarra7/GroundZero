package net.groundzero.listener.player;

import net.groundzero.app.Core;
import net.groundzero.listener.BaseListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerLifecycleListener extends BaseListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Core.playerService.onPlayerJoin(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Core.playerService.onPlayerQuit(e.getPlayer());
    }
}
