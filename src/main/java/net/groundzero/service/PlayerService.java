package net.groundzero.service;

import net.groundzero.app.Core;
import net.groundzero.util.Notifier;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerService {

    // Tracks players that are logically "dead" and not yet fully respawned.
    // This is per-session state and will be reset when the session resets.
    private final Set<UUID> deadPlayers = ConcurrentHashMap.newKeySet();

    // Tracks scheduled respawn tasks for players who are waiting to respawn.
    private final Map<UUID, BukkitTask> respawnTasks = new ConcurrentHashMap<>();

    public PlayerService() {}

    public void resetDeathState() {
        deadPlayers.clear();
        respawnTasks.clear();
    }

    /* ===================== JOIN ===================== */

    public void onJoinIdle(Player p) {
        if (p == null) return;

        UUID id = p.getUniqueId();

        if (Core.session.getSpectatorsView().contains(id)) return;

        Core.session.addSpectator(id);
        Core.notifier.message(
                p,
                false,
                "Welcome to GroundZero",
                "To start the game, use &e/groundzero start"
        );
    }

    public void onJoinPregame(Player p) {
        if (p == null) return;

        UUID id = p.getUniqueId();

        if (Core.session.getSpectatorsView().contains(id)) return;

        Core.session.addSpectator(id);
        Core.notifier.message(
                p,
                false,
                "Welcome to GroundZero",
                "You joined as spectator, you won't be participating this game session"
        );
    }

    public void onJoinIngame(Player p) {
        if (p == null) return;

        UUID id = p.getUniqueId();

        // 1) Player was already dead when they disconnected (or the server kicked them).
        //    Policy: when they join again during an ongoing match, treat them as dead and
        //    restart the 5-second respawn countdown from scratch.
        if (deadPlayers.contains(id)) {
            // Make sure any old respawn task is cancelled if it still exists.
            BukkitTask old = respawnTasks.remove(id);
            if (old != null) {
                Core.schedulers.cancelTask(old);
            }

            // Put them into spectator mode above the center, then schedule respawn.
            Core.game.setSpectatorAndTeleportToCenter(id);
            scheduleRespawn(p, id);
            return;
        }

        // 2) Normal spectator re-join: if they are already registered as spectator,
        //    we intentionally do nothing. This avoids re-running welcome & TP logic
        //    every time a spectator reconnects.
        if (Core.session.getSpectatorsView().contains(id)) {
            return;
        }

        // 3) Fresh joiner during an ingame match: treat as spectator.
        Core.session.addSpectator(id);

        Core.notifier.message(
                p,
                false,
                "Welcome to GroundZero",
                "You joined as spectator, you won't be participating this game session"
        );

        Core.game.setSpectatorAndTeleportToCenter(id);
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
                Sound.BLOCK_ANVIL_LAND,
                Notifier.PitchLevel.LOW,
                true,
                p.getName() + " left during setup",
                "Game canceled"
        );
    }

    public void onQuitIngame(Player p) {
        if (p == null) return;

        UUID id = p.getUniqueId();

        // 1) If this player is a spectator, we do not touch anything on quit.
        //    - We keep them in the spectator set.
        //    - We avoid re-running spectator welcome / teleport on rejoin.
        if (Core.session.getSpectatorsView().contains(id)) {
            return;
        }

        // 2) Participant case.
        if (deadPlayers.contains(id)) {
            // Already dead: this can happen if the player died and then quit
            // during the 5-second respawn countdown.
            // Policy:
            // - Cancel any ongoing respawn task (so they do NOT auto-respawn while offline).
            // - Keep them marked as dead; when they join again, onJoinIngame(...)
            //   will restart the countdown and respawn flow.
            BukkitTask pending = respawnTasks.remove(id);
            if (pending != null) {
                Core.schedulers.cancelTask(pending);
            }

            // No scoring, because we already handled death once.
            return;
        }

        // 3) Alive participant logs out during the match.
        //    Policy: treat this logout exactly like a normal death in terms of
        //    scoring and kill credit.
        Core.combatOutcomeService.handleLogoutDeath(p);

        // Mark as dead so we do not double-apply death if some other event fires.
        deadPlayers.add(id);

        // We intentionally do NOT schedule a respawn here, because the player is
        // now offline. When they join again during the same match, onJoinIngame(...)
        // will detect deadPlayers.contains(id) and start a fresh 5-second respawn.
    }

    /* ===================== DEATH ===================== */

    public void onDeathIdle(Player p) {
        if (p == null) return;
        // Probably ignore or send to lobby spawn (no-op for now).
    }

    public void onDeathPregame(Player p) {
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

    public void onDeathIngame(Player p) {
        if (p == null) return;

        UUID id = p.getUniqueId();

        // If already considered dead (e.g. due to a previous death or logout),
        // we ignore additional death events to avoid double scoring.
        if (deadPlayers.contains(id)) {
            return;
        }

        // now we handle death in lifecyclelistener
        // Core.combatOutcomeService.handlePlayerDeath(p);

        // Mark as dead so that logout or duplicate death does not double-count.
        deadPlayers.add(id);

        // 2) Immediately move the player to spectator view above the center and
        //    switch their GameMode to SPECTATOR.
        Core.game.setSpectatorAndTeleportToCenter(id);

        // 3) Schedule a 5-second respawn for this player.
        scheduleRespawn(p, id);
    }

    /* ===================== INTERNAL HELPERS ===================== */

    /**
     * Schedule a 5-second respawn for the given player.
     * If the game ends or the player logs out before the timer fires,
     * the respawn will be skipped.
     */
    private void scheduleRespawn(Player original, UUID id) {
        // Cancel any existing respawn task first, to avoid double scheduling.
        BukkitTask existing = respawnTasks.remove(id);
        if (existing != null) {
            Core.schedulers.cancelTask(existing);
        }

        // Inform the player if they are online.
        if (original != null && original.isOnline()) {
            try {
                // Title timing: fadeIn 10, stay 60, fadeOut 10 ticks (can be tuned).
                original.sendTitle("§cYou died", "§fRespawning in 5 seconds", 10, 60, 10);
            } catch (Throwable ignored) {
                // Some servers disable titles; chat message below is the fallback.
            }

            Core.notifier.message(
                    original,
                    false,
                    "You died",
                    "Respawning in 5 seconds"
            );
        }

        BukkitTask task = Core.schedulers.runLater(() -> {
            // If the match is no longer running, bail out.
            if (!Core.session.state().isIngame()) {
                respawnTasks.remove(id);
                deadPlayers.remove(id);
                return;
            }

            Player online = Bukkit.getPlayer(id);
            if (online == null || !online.isOnline()) {
                // Player is not online; keep them marked as dead so that
                // onJoinIngame(...) can restart the respawn flow later.
                respawnTasks.remove(id);
                return;
            }

            // Place the player at a random point inside the border and apply
            // the per-participant survival setup (inventory/xp are preserved,
            // because you moved resets to setUpGame() only).
            Core.game.setSurvivalAndTeleportRandomly(id);

            // Player is now respawned and alive again.
            respawnTasks.remove(id);
            deadPlayers.remove(id);
        }, 5 * 20L);

        // Track the scheduled task so we can cancel it if the player logs out
        // during the 5-second countdown.
        respawnTasks.put(id, task);
    }
}
