package net.groundzero.service.player;

import net.groundzero.app.Core;
import net.groundzero.service.Resettable;
import net.groundzero.util.Notifier;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * Player lifecycle handler.
 * Now uses Core.playerStates for all state management.
 */
public final class PlayerService implements Resettable {

    public PlayerService() {}

    @Override
    public void reset() {
        // All state is now in PlayerGameStateService
        // Just cancel any pending tasks
        for (UUID id : Core.session.getParticipantsView()) {
            PlayerGameState state = Core.playerStates.get(id);
            if (state != null) {
                BukkitTask task = state.getRespawnTask();
                if (task != null) {
                    Core.schedulers.cancelTask(task);
                }
            }
        }
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
        PlayerGameState state = Core.playerStates.getOrCreate(id);

        // 1) Player was already dead when they disconnected.
        //    Restart the respawn countdown from scratch.
        if (state.isDead()) {
            // Cancel any old respawn task
            BukkitTask old = state.getRespawnTask();
            if (old != null) {
                Core.schedulers.cancelTask(old);
                state.setRespawnTask(null);
            }

            // Put them into spectator mode, then schedule respawn
            Core.game.setSpectatorAndTeleportToCenter(id);
            scheduleRespawn(p, id);
            return;
        }

        // 2) Already a spectator: do nothing to avoid re-running welcome/TP
        if (Core.session.getSpectatorsView().contains(id)) {
            return;
        }

        // 3) Fresh joiner: add as spectator
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
        Core.game.cancelPregame(p);
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
        PlayerGameState state = Core.playerStates.getOrCreate(id);

        // 1) Spectator quit: do nothing
        if (Core.session.getSpectatorsView().contains(id)) {
            return;
        }

        // 2) Already dead: cancel respawn task
        if (state.isDead()) {
            BukkitTask pending = state.getRespawnTask();
            if (pending != null) {
                Core.schedulers.cancelTask(pending);
                state.setRespawnTask(null);
            }
            // No scoring, already handled
            return;
        }

        // 3) Alive participant logs out: treat as death
        Core.combatOutcomeService.handleLogoutDeath(p);
        state.markDead();

        // Do NOT schedule respawn (offline)
        // Will restart when they rejoin
        // TODO : reset idletimer as dead
    }

    /* ===================== DEATH ===================== */

    public void onDeathIdle(Player p) {
        if (p == null) return;
        // No-op for now
    }

    public void onDeathPregame(Player p) {
        if (p == null) return;

        Core.game.cancelPregame(p);
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
        PlayerGameState state = Core.playerStates.getOrCreate(id);

        // Prevent double death
        if (state.isDead()) {
            return;
        }

        // Death scoring is handled in PlayerLifecycleListener
        // Core.combatOutcomeService.handlePlayerDeath(p, vanillaMsg);

        state.markDead();

        // ✅ NEW: Reset Idle Timer on death (victim only)
        // Use negative grace equal to respawn delay
        int negGrace = -Core.gameConfig.respawnDelayTicks;
        state.resetIdleToGrace(negGrace);

        // Move to spectator
        Core.game.setSpectatorAndTeleportToCenter(id);

        // Schedule respawn
        scheduleRespawn(p, id);
    }

    /* ===================== INTERNAL HELPERS ===================== */

    /**
     * Schedule a respawn for the given player.
     */
    private void scheduleRespawn(Player original, UUID id) {
        PlayerGameState state = Core.playerStates.getOrCreate(id);

        // Cancel any existing respawn task
        BukkitTask existing = state.getRespawnTask();
        if (existing != null) {
            Core.schedulers.cancelTask(existing);
            state.setRespawnTask(null);
        }

        // Inform the player
        if (original != null && original.isOnline()) {
            try {
                original.sendTitle("§cYou died", "§fRespawning in 5 seconds", 10, 60, 10);
            } catch (Throwable ignored) {}

            Core.notifier.message(
                    original,
                    false,
                    "You died",
                    "Respawning in 5 seconds"
            );
        }

        BukkitTask task = Core.schedulers.runLater(() -> {
            // If match ended, bail out
            if (!Core.session.state().isIngame()) {
                state.setRespawnTask(null);
                state.markAlive();
                return;
            }

            Player online = Bukkit.getPlayer(id);
            if (online == null || !online.isOnline()) {
                // Keep marked as dead for rejoin
                state.setRespawnTask(null);
                return;
            }

            // Respawn player
            Core.game.setSurvivalAndTeleportRandomly(id);

            // Mark as alive
            state.resetCombat();
        }, Core.gameConfig.respawnDelayTicks);

        state.setRespawnTask(task);
    }
}