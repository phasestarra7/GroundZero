package net.groundzero.service;

import net.groundzero.app.Core;
import net.groundzero.util.Notifier;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public final class PlayerService {

    public PlayerService() {}

    /* ===================== JOIN ===================== */

    public void onJoinIdle(Player p) {
        if (p == null) return;
        if (Core.session.getSpectatorsView().contains(p.getUniqueId())) return;

        Core.session.addSpectator(p.getUniqueId());
        Core.notifier.message(
                p,
                false,
                "Welcome to GroundZero",
                "To start the game, use &e/groundzero start"
        );
    }

    public void onJoinPregame(Player p) {
        if (p == null) return;
        if (Core.session.getSpectatorsView().contains(p.getUniqueId())) return;

        Core.session.addSpectator(p.getUniqueId());
        Core.notifier.message(
                p,
                false,
                "Welcome to GroundZero",
                "You joined as spectator, you won't be participating this game session"
        );
    }

    public void onJoinIngame(Player p) {
        if (p == null) return;
        if (Core.session.getSpectatorsView().contains(p.getUniqueId())) return;
        // Requested policy: treat joiners as spectators (IDLE-like) until respawn policy is defined
        Core.session.addSpectator(p.getUniqueId());
        Core.notifier.message(
                p,
                false,
                "Welcome to GroundZero",
                "You joined as spectator, you won't be participating this game session"
        );
        Core.game.teleportSpectatorsAndChangeGamemode(p.getUniqueId());
    }

    /* ===================== QUIT ===================== */

    public void onQuitIdle(Player p) {
        if (p == null) return;
        Core.session.removeSpectator(p.getUniqueId());
    }

    public void onQuitPregame(Player p) {
        if (p == null) return;
        Core.session.removeSpectator(p.getUniqueId());
        Core.game.tryCancel(p);
        Core.notifier.broadcast(
                Bukkit.getOnlinePlayers(),
                Sound.BLOCK_ANVIL_LAND, Notifier.PitchLevel.LOW, true,
                p.getName() + " left during setup",
                "Game canceled"
        );
    }

    public void onQuitIngame(Player p) {
        if (p == null) return;
        // TODO: combat-logout grace & forced death policy will be implemented here later.
        // (No scheduling right now per request)
        Core.notifier.broadcast(
                Bukkit.getOnlinePlayers(),
                Sound.BLOCK_NOTE_BLOCK_BASS, Notifier.PitchLevel.LOW, false,
                "§7" + p.getName() + " left the battlefield."
        );
    }

    /* ===================== DEATH ===================== */

    public void onDeathIdle(Player p) {
        if (p == null) return;
        // Probably ignore or send to lobby spawn (no-op for now)
    }

    public void onDeathPregame(Player p) {
        if (p == null) return;
        Core.game.tryCancel(p);
        Core.notifier.broadcast(
                Bukkit.getOnlinePlayers(),
                Sound.BLOCK_ANVIL_LAND, Notifier.PitchLevel.LOW, true,
                p.getName() + " died during setup",
                "Game canceled"
        );
    }

    public void onDeathIngame(Player p) {
        if (p == null) return;
        if (Core.session.getSpectatorsView().contains(p.getUniqueId())) return;

        Core.combatOutcomeService.handlePlayerDeath(p); // TODO
    }
}
