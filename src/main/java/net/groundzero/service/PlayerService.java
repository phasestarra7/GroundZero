package net.groundzero.service;

import net.groundzero.app.Core;
import net.groundzero.util.Notifier;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Handles player join/quit/death logic depending on current phase.
 * Keeps GameManager/Listener clean and stateless.
 */
public final class PlayerService {

    public PlayerService() {}

    /* =========================================================
       JOIN
       ========================================================= */

    /** Called from PlayerLifecycleListener.onJoin */
    public void onPlayerJoin(Player p) {
        if (p == null) return;
        Core.session.addSpectator(p.getUniqueId());
        // TODO: check if player was already a spectator / restore state if needed
    }

    /* =========================================================
       QUIT
       ========================================================= */

    /** Normal quit during IDLE or ENDED */
    public void onPlayerQuit(Player p) {
        if (p == null) return;
        Core.session.removeSpectator(p.getUniqueId());
    }

    /** Quit during pre-game (vote or countdown) */
    public void onPlayerQuitPreGame(Player p) {
        if (p == null) return;

        Core.game.tryCancel(p);
        Core.notifier.broadcast(
            Bukkit.getOnlinePlayers(),
            Sound.BLOCK_ANVIL_LAND,
            Notifier.PitchLevel.LOW,
            true,
            p.getName() + " left during setup",
            "Game canceled"
        );
    }

    /** Quit during running game */
    public void onPlayerQuitIngame(Player p) {
        if (p == null) return;

        // Handle combat logging etc.
        Core.damageService.handlePlayerQuitDuringCombat(p);

        // Optional feedback
        Core.notifier.broadcast(
                Bukkit.getOnlinePlayers(),
                Sound.BLOCK_NOTE_BLOCK_BASS,
                Notifier.PitchLevel.LOW,
                false,
                "&7" + p.getName() + " left the battlefield." // TODO
        );
    }

    /* =========================================================
       DEATH
       ========================================================= */

    /** Death during pre-game — cancel whole setup */
    public void onPlayerDeathPreGame(Player p) {
        if (p == null) return;

        Core.game.tryCancel(p);
        Core.notifier.broadcast(
            Bukkit.getOnlinePlayers(),
            Sound.BLOCK_ANVIL_LAND,
            Notifier.PitchLevel.LOW,
            true,
            p.getName() + " died during setup",
            "Game canceled"
        );
    }

    /** Death during running game — delegate to DamageService */
    public void onPlayerDeathIngame(Player p) {
        if (p == null) return;
        Core.damageService.handlePlayerBukkitDeath(p);
    }
}
