package net.groundzero.service.player;

import net.groundzero.app.Core;
import net.groundzero.util.Notifier;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import net.groundzero.service.GameService;

import java.util.UUID;

/**
 * Player lifecycle handler.
 * Uses Core.playerStates for all state management.
 */
public final class PlayerService implements GameService {

    public PlayerService() {}

    @Override
    public void reset() {
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
        if (state.isDead()) {
            BukkitTask old = state.getRespawnTask();
            if (old != null) {
                Core.schedulers.cancelTask(old);
                state.setRespawnTask(null);
            }

            Core.game.setSpectatorAndTeleportToCenter(id);
            scheduleRespawn(p, id);
            return;
        }

        // 2) Already a spectator
        if (Core.session.getSpectatorsView().contains(id)) {
            return;
        }

        // 3) Fresh joiner
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

        // 1) Spectator quit - just leave
        if (Core.session.getSpectatorsView().contains(id)) {
            return;
        }

        // 2) Already dead: cancel respawn task, don't reschedule until rejoin
        if (state.isDead()) {
            BukkitTask pending = state.getRespawnTask();
            if (pending != null) {
                Core.schedulers.cancelTask(pending);
                state.setRespawnTask(null);
            }
            // Timer keeps running - they may accumulate penalties
            return;
        }

        // 3) Alive participant logs out: treat as death (no respawn until rejoin)
        Core.combatOutcomeService.handleLogoutDeath(id, p.getName());
        state.markDead();
        Core.combatIdleService.onDeath(id);
        // Note: No scheduleRespawn here - will be scheduled in onJoinIngame when they return
    }

    /* ===================== DEATH ===================== */

    public void onDeathIdle(Player p) {
        if (p == null) return;
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

        // 1. Score / kill credit + custom death message
        Core.combatOutcomeService.handlePlayerDeath(id, p.getName());

        // 2. Mark as dead
        state.markDead();

        // 3. Reset idle timer to -respawnDelayTicks (timer starts at 0 after respawn)
        Core.combatIdleService.onDeath(id);

        // Clear pending recoil recovery
        Core.recoilService.clearPlayer(id);
        // 4. Move to spectator
        Core.game.setSpectatorAndTeleportToCenter(id);

        // 5. Schedule respawn
        scheduleRespawn(p, id);
    }

    /* ===================== INTERNAL HELPERS ===================== */

    private void scheduleRespawn(Player original, UUID id) {
        PlayerGameState state = Core.playerStates.getOrCreate(id);

        BukkitTask existing = state.getRespawnTask();
        if (existing != null) {
            Core.schedulers.cancelTask(existing);
            state.setRespawnTask(null);
        }

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
            if (!Core.session.state().isIngame()) {
                state.setRespawnTask(null);
                state.markAlive();
                return;
            }

            Player online = Bukkit.getPlayer(id);
            if (online == null || !online.isOnline()) {
                state.setRespawnTask(null);
                return;
            }

            Core.game.setSurvivalAndTeleportRandomly(id);

            state.resetCombat();
        }, Core.gameConfig.respawnDelayTicks);

        state.setRespawnTask(task);
    }
}