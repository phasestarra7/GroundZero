package net.groundzero.listener.player;

import net.groundzero.app.Core;
import net.groundzero.game.GameState;
import net.groundzero.listener.BaseListener;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Player join/quit/death listener.
 * Keeps logic minimal — delegates to PlayerService depending on phase.
 */
public final class PlayerLifecycleListener extends BaseListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Core.playerService.onPlayerJoin(e.getPlayer());
        // NOTE: PlayerService decides if rejoin / new spectator / etc.
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (p == null) return;

        GameState state = Core.session.state();

        if (state.isPregame()) {
            Core.playerService.onPlayerQuitPreGame(p);
            return;
        }
        if (state.isIngame()) {
            Core.playerService.onPlayerQuitIngame(p);
            return;
        }

        Core.playerService.onPlayerQuit(p); // idle / ended
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        if (victim == null) return;

        GameState state = Core.session.state();

        if (state.isPregame()) {
            Core.playerService.onPlayerDeathPreGame(victim);
            return;
        }
        if (state.isIngame()) {
            Core.playerService.onPlayerDeathIngame(victim);
            return;
        }
    }
}
