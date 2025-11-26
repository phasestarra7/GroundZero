package net.groundzero.listener.player;

import net.groundzero.app.Core;
import net.groundzero.listener.BaseListener;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Pure routing by game phase:
 * - idle
 * - pregame (vote/countdown)
 * - ingame (running; 'ended' kept for future split)
 *
 * All game-specific logic lives in PlayerService and related services.
 */
public final class PlayerLifecycleListener extends BaseListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();

        if (Core.session.state().isPregame()) {
            Core.playerService.onJoinPregame(p);
        } else if (Core.session.state().isIngame()) {
            Core.playerService.onJoinIngame(p);
        } else { // idle or ended → treat as idle
            Core.playerService.onJoinIdle(p);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        String vanillaMsg = event.getDeathMessage(); // original vanilla death message

        // Prevent vanilla from also broadcasting its own death message.
        event.setDeathMessage(null);

        if (Core.session.state().isPregame()) {
            Core.playerService.onDeathPregame(p);
        } else if (Core.session.state().isIngame()) {
            // Score / kill credit + custom death message
            Core.combatOutcomeService.handlePlayerDeath(p, vanillaMsg);
            // Respawn / spectator logic
            Core.playerService.onDeathIngame(p);
        } else { // idle/ended
            Core.playerService.onDeathIdle(p);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();

        if (Core.session.state().isPregame()) {
            Core.playerService.onQuitPregame(p);
        } else if (Core.session.state().isIngame()) {
            Core.playerService.onQuitIngame(p);
        } else { // idle/ended
            Core.playerService.onQuitIdle(p);
        }
    }
}
