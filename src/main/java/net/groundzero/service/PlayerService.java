package net.groundzero.service;

import net.groundzero.app.Core;
import net.groundzero.util.Notifier;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Handles join/quit and session membership transitions:
 *  - join => spectator
 *  - quit => purge from session
 */

public final class PlayerService {

    public PlayerService() {}

    /** Called from PlayerLifecycleListener.onJoin */
    public void onPlayerJoin(Player p) {
        if (p == null) return;
        Core.session.addSpectator(p.getUniqueId());
        //TODO : message diff by current state : e.g) if the game is running: "You joined as spectator!"
    }

    /** Called from PlayerLifecycleListener.onQuit */
    public void onPlayerQuit(Player p) {
        if (p == null) return;
        Core.game.cancelAll();
        Core.notifier.broadcast(Bukkit.getOnlinePlayers(),Sound.BLOCK_ANVIL_LAND, Notifier.PitchLevel.LOW, true, "Player &a" + p + " &chas left", "Terminating GroundZero");
    }
}
