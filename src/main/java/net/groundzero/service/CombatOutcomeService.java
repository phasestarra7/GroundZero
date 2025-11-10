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

    private long combatWindowMs() {
        return Math.max(0L, Core.gameConfig.combatWindowMillis);
    }

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
        long now = System.currentTimeMillis();
        boolean inWindow = (last != null) && (now - last.timestamp) <= combatWindowMs();

        UUID aId = (inWindow ? last.attacker : null);
        if (aId != null) {
            double loss = Math.max(0.0, vScore * clamp01(Core.gameConfig.deathPenaltyPercent));

            double aScore = Core.session.getScoreMap().getOrDefault(aId, 0.0);
            double gain = Math.max(0.0, aScore * clamp01(Core.gameConfig.killStealPercent));

            Core.session.getScoreMap().put(victimId, Math.max(0.0, vScore - loss));
            Core.session.getScoreMap().put(aId, Math.max(0.0, aScore + gain));

            String aName = java.util.Optional
                    .ofNullable(Bukkit.getOfflinePlayer(aId).getName())
                    .orElse(aId.toString().substring(0, 8));

            Core.notifier.broadcast(
                    Bukkit.getOnlinePlayers(),
                    Sound.ENTITY_PLAYER_LEVELUP, Notifier.PitchLevel.HIGH, false,
                    "&a" + aName + " §fkilled §c" + victimName
                            + " §7(§6+" + fmt(gain) + "§7 / §c-" + fmt(loss)
            ); // TODO : use weaponId to format nicely
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
