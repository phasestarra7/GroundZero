package net.groundzero.service;

import net.groundzero.app.Core;
import net.groundzero.game.GameState;
import net.groundzero.service.model.LastHit;
import net.groundzero.util.Notifier;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Handles kill credit and scoring on death.
 * Uses Core.gameConfig.combatLogoutGraceMillis as the shared combat window.
 * (Logout-in-combat grace is TODO and will be implemented in PlayerService later.)
 */
public final class CombatOutcomeService {

    /** Death while INGAME: resolve attacker (if within window), apply score transfers/penalties, and spectatorize. */
    public void handlePlayerDeath(Player victim) {
        if (victim == null || !Core.session.state().isIngame()) return;

        applyDeathScoring(victim.getUniqueId(), victim.getName());

        /*
        // Spectator & TODO-respawn
        try { victim.setGameMode(GameMode.SPECTATOR); } catch (Throwable ignored) {}
        Core.schedulers.runLater(() -> {
            if (!victim.isOnline()) return;
            // TODO: select spawn, reset inventory/effects, SURVIVAL
        }, 60L); // 3s placeholder */
    }

    /* ========== internal scoring ========== */

    private void applyDeathScoring(UUID victimId, String victimName) {
        double vScore = Core.session.getScoreMap().getOrDefault(victimId, 0.0);

        // Resolve attacker within window (environment/mob deaths included)
        LastHit last = Core.damageService.peekLastHit(victimId);
        boolean inWindow = false;
        if (last != null) {
            int nowTicks = Core.session.remainingTicks();
            int dt = last.tick - nowTicks;
            inWindow = (dt >= 0) && (dt < Core.gameConfig.combatWindowTicks);
        }

        UUID aId = (inWindow ? last.attacker : null);

        if (aId != null) {
            // victim loses % of their own score
            double loss = Math.max(0.0, vScore * clamp01(Core.gameConfig.deathPenaltyPercent));

            double aScore = Core.session.getScoreMap().getOrDefault(aId, 0.0);
            // attacker gains % of the VICTIM's score (steal from victim)
            double gain = Math.max(0.0, vScore * clamp01(Core.gameConfig.killStealPercent));

            Core.session.getScoreMap().put(victimId, Math.max(0.0, vScore - loss));
            Core.session.getScoreMap().put(aId, Math.max(0.0, aScore + gain));

            String aName = Bukkit.getPlayer(aId).getName();

            Core.notifier.broadcast(
                    Bukkit.getOnlinePlayers(),
                    Sound.ENTITY_PLAYER_LEVELUP, Notifier.PitchLevel.HIGH, false,
                    "&a" + aName + " §fkilled §c" + victimName
                            + " §7(§6+" + fmt(gain) + "§7 / §c-" + fmt(loss + gain) + "§7)"
            ); // TODO : use weaponId to format nicer
        } else {
            double loss = Math.max(0.0, vScore * clamp01(Core.gameConfig.nonPlayerDeathPenaltyPercent));

            Core.session.getScoreMap().put(victimId, Math.max(0.0, vScore - loss));
            Core.notifier.broadcast(
                    Bukkit.getOnlinePlayers(),
                    Sound.BLOCK_NOTE_BLOCK_BASS, Notifier.PitchLevel.MID, false,
                    "&a" + victimName + " &fdied (penalty &c-" + fmt(loss)
            );
        }
    }

    private static double clamp01(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
    private static String fmt(double v) { return String.format("%.2f", v); }
}
