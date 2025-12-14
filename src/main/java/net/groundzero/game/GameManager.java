// game/GameManager.java
package net.groundzero.game;

import net.groundzero.app.Core;
import net.groundzero.ui.options.*;
import net.groundzero.util.Notifier;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;
import java.util.UUID;

/**
 * Central game controller.
 *
 * Lifecycle:
 *   IDLE -> (start) -> PREGAME (voting) -> RUNNING -> ENDED -> IDLE
 *
 * Phase Flow (clearly visible):
 *   1. startFromIdle()
 *   2. → beginVotingFlow()
 *   3.   → voteMapSize()
 *   4.   → voteIncome()
 *   5.   → voteGameMode()
 *   6.   → finalCountdown()
 *   7. → startActualGame()
 *   8.   → (20 min gameplay)
 *   9. → endMatch()
 *  10. → (cleanup) → back to IDLE
 *
 * Cancel/Stop methods:
 *   - cancelPregame(): Cancel during pregame (voting/countdown). Resets pregame-only data.
 *   - endMatch(): Normal end after 20 min. Shows results, then resets all after delay.
 *   - forceStop(): Force stop from any state. Used by /gz test or plugin disable.
 */
public class GameManager {

    private final GameSession session = new GameSession();
    private static final Random RNG = new Random();

    public GameSession session() { return session; }

    public GameManager() {}

    /* =========================================================
       PUBLIC ENTRYPOINTS
       ========================================================= */

    /**
     * Start game flow from IDLE.
     */
    public void start(Player p) {
        GameState st = session.state();

        if (st == GameState.IDLE) {
            if (p != null) startFromIdle(p);
            return;
        }

        if (st.isPregame()) {
            if (p != null) Core.notifier.message(p, true, "The game is already starting");
            return;
        }

        if (p != null) Core.notifier.message(p, true, "The game is already running");
    }

    /**
     * Cancel during pregame. Called by player quit or /gz cancel.
     * Only works during pregame states.
     */
    public void cancelPregame(Player requester) {
        GameState st = session.state();

        if (st == GameState.IDLE) {
            if (requester != null) {
                Core.notifier.message(requester, true, "There is no game to cancel");
            }
            return;
        }

        if (st.isPregame()) {
            if (requester != null) {
                Core.notifier.broadcast(
                        Bukkit.getOnlinePlayers(),
                        Sound.BLOCK_ANVIL_LAND,
                        Notifier.PitchLevel.LOW,
                        true,
                        "GroundZero canceled by &a" + requester.getName()
                );
            }
            doCancelPregame();
            return;
        }

        if (requester != null) {
            Core.notifier.message(requester, true, "Cannot cancel - game is already running");
        }
    }

    /**
     * Normal match end. Called when 20 min timer expires.
     * Shows results, then cleans up after delay.
     */
    public void endMatch() {
        GameState st = session.state();

        if (!st.isIngame()) {
            // Shouldn't happen, but fallback
            forceStop(null);
            return;
        }

        session.setState(GameState.ENDED);

        // Stop tick-based services immediately (no more ticks during ENDED)
        stopIngameServices();

        // Cancel any pending tasks (respawn timers, etc.)
        Core.schedulers.cancelAll();

        // Broadcast game over
        Core.notifier.broadcast(
                Bukkit.getOnlinePlayers(),
                Sound.ENTITY_ENDER_DRAGON_GROWL,
                Notifier.PitchLevel.MID,
                false,
                "Game Over"
        );

        // Put all participants into spectator mode
        for (UUID id : session.getParticipantsView()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;
            p.getInventory().clear();
            p.setExp(0f);
            p.setLevel(0);
            p.setTotalExperience(0);
            p.setGameMode(GameMode.SPECTATOR);
        }

        // Print scores after 1 second
        Core.schedulers.runLater(this::printMatchResults, 20L);

        // Full cleanup after delay
        Core.schedulers.runLater(this::doFullCleanup, Core.gameConfig.matchEndDelayTicks);
    }

    /**
     * Force stop from any state. Used by /gz test or plugin disable.
     * Immediately cleans everything.
     */
    public void forceStop(Player sender) {
        // Stop all tick services
        stopIngameServices();

        // Cancel all scheduled tasks
        Core.schedulers.cancelAll();

        // Restore world
        restoreEnvironmentToDefault();

        // Reset all services
        resetAllServices();

        // Back to IDLE
        session.setState(GameState.IDLE);
        session.resetToAllSpectators();

        if (sender != null) {
            Core.notifier.message(sender, false, "Game force stopped");
        }
    }

    /* =========================================================
       INTERNAL CANCEL/CLEANUP METHODS
       ========================================================= */

    /**
     * Cancel pregame only. Resets pregame-specific data.
     */
    private void doCancelPregame() {
        // Cancel vote timers, countdowns
        Core.schedulers.cancelAll();

        // Close vote GUIs
        Core.guiService.closeAllGZViews();

        // Reset pregame services
        Core.voteService.reset();
        Core.session.clearRuntimeAndOptions();

        // Back to IDLE
        session.setState(GameState.IDLE);
        session.resetToAllSpectators();
    }

    /**
     * Stop tick-based services that run during ingame.
     */
    private void stopIngameServices() {
        if (Core.gameRuntimeService != null) Core.gameRuntimeService.stop();
        if (Core.scoreboardService != null) Core.scoreboardService.stop();
        if (Core.combatIdleService != null) Core.combatIdleService.stop();
        if (Core.tickBus != null) Core.tickBus.stop();
    }

    /**
     * Reset all services that hold session data.
     */
    private void resetAllServices() {
        // Pregame services
        Core.voteService.reset();
        Core.guiService.closeAllGZViews();

        // Ingame services
        Core.damageService.reset();
        Core.combatIdleService.reset();
        Core.playerService.reset();
        Core.scoreboardService.reset();

        // Player states (central)
        Core.playerStates.reset();

        // Session data
        Core.session.clearRuntimeAndOptions();
    }

    /**
     * Full cleanup after ENDED state. Returns to IDLE.
     */
    private void doFullCleanup() {
        // Reset all players to survival with full health
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p == null || !p.isOnline()) continue;
            p.setFoodLevel(20);
            p.setSaturation(20f);
            p.setExhaustion(0f);
            p.setHealth(p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
            p.setGameMode(GameMode.SURVIVAL);
        }

        // Restore world settings
        restoreEnvironmentToDefault();

        // Reset all services
        resetAllServices();

        // Back to IDLE
        session.setState(GameState.IDLE);
        session.resetToAllSpectators();
    }

    /**
     * Print match results (called during ENDED).
     */
    private void printMatchResults() {
        // TODO: Sort by score, format nicely
        for (UUID id : session.getParticipantsView()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            double score = session.getScoreMap().getOrDefault(id, 0.0);
            Core.notifier.broadcast(
                    Bukkit.getOnlinePlayers(),
                    null, null, false,
                    p.getName() + " : " + String.format("%.1f", score)
            );
        }
    }

    /* =========================================================
       PHASE FLOW: Start → Voting → Game Start
       This section shows the complete pregame flow clearly
       ========================================================= */

    /**
     * PHASE 1: Initialize game from IDLE state
     */
    private void startFromIdle(Player sender) {
        // 1) Collect participants from current spectators
        session.snapshotParticipantsFromSpectators();

        if (session.getParticipantsView().isEmpty()) {
            Core.notifier.message(sender, true, "No players to start with");
            session.resetToAllSpectators();
            return;
        }

        // 2) Capture world and center from participants
        if (!session.captureWorldAndCenterFromParticipants()) {
            Core.notifier.message(sender, true, "Players must be in the same world");
            session.resetToAllSpectators();
            return;
        }

        // 3) Notify start
        Core.notifier.broadcast(
                Bukkit.getOnlinePlayers(),
                Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                Notifier.PitchLevel.HIGH,
                false,
                "GroundZero starting",
                "Participants: " + session.namesOfParticipants()
        );

        // 4) Begin voting flow
        session.setState(GameState.COUNTDOWN_BEFORE_VOTING);
        Core.voteService.startCountdownInternal(
                Core.gameConfig.preVoteCountdownTicks / 20,
                this::beginVotingFlow
        );
    }

    /**
     * PHASE 2: Start voting sequence
     * Flow: MapSize → Income → GameMode → FinalCountdown → Game
     */
    private void beginVotingFlow() {
        voteMapSize();
    }

    /**
     * PHASE 3: Vote for map size
     */
    private void voteMapSize() {
        Core.voteService.startMapSizeVote(this::voteIncome);
    }

    /**
     * PHASE 4: Vote for income multiplier
     */
    private void voteIncome() {
        Core.voteService.startIncomeVote(this::voteGameMode);
    }

    /**
     * PHASE 5: Vote for game mode
     */
    private void voteGameMode() {
        Core.voteService.startGameModeVote(this::finalCountdown);
    }

    /**
     * PHASE 6: Final countdown before game starts
     */
    private void finalCountdown() {
        Core.guiService.closeAllGZViews();
        session.setState(GameState.COUNTDOWN_BEFORE_START);
        Core.voteService.startCountdownInternal(
                Core.gameConfig.finalCountdownTicks / 20,
                this::onVotingComplete
        );
    }

    /**
     * PHASE 7: Voting complete, apply options and start game
     */
    private void onVotingComplete() {
        GameState st = session.state();
        if (!st.isPregame()) return;

        // Apply voted options
        applyVoteOptionToParticipants();
        applyWorldSettings();

        // Start actual game
        startActualGame();
    }

    /**
     * PHASE 8: Start the actual game (RUNNING state)
     */
    private void startActualGame() {
        GameState st = session.state();
        if (!st.isPregame()) {
            session.resetToAllSpectators();
            return;
        }

        GameModeOption mode = session.gameMode();
        if (mode == null) mode = GameModeOption.STANDARD;

        switch (mode) {
            case STANDARD:
            default:
                startStandardMode();
                break;
        }
    }

    private void startStandardMode() {
        // Set match time
        session.setRemainingTicks(Core.gameConfig.matchDurationTicks);

        // Start tick-based services
        Core.gameRuntimeService.start(session);
        Core.scoreboardService.start(session);
        Core.combatIdleService.start();
        Core.tickBus.start();

        // Setup participants
        for (UUID id : session.getParticipantsView()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;
            p.getInventory().clear();
            p.setExp(0f);
            p.setLevel(0);
            p.setTotalExperience(0);
            setSurvivalAndTeleportRandomly(id);
        }

        // Setup spectators
        for (UUID id : session.getSpectatorsView()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;
            p.getInventory().clear();
            p.setExp(0f);
            p.setLevel(0);
            p.setTotalExperience(0);
            setSpectatorAndTeleportToCenter(id);
        }

        // Give loadouts
        Core.loadoutService.giveInitialLoadouts(session.getParticipantsView());

        // Change state
        session.setState(GameState.RUNNING);

        Core.notifier.broadcast(
                Bukkit.getOnlinePlayers(),
                Sound.ENTITY_ENDER_DRAGON_GROWL,
                Notifier.PitchLevel.MID,
                false,
                "&9----------------",
                "&eGroundZero Start!",
                "Map Size: &a" + (session.mapSize() != null ? session.mapSize().label : "N/A"),
                "Income: &a" + (session.income() != null ? session.income().label : "N/A"),
                "Game Mode: &a" + (session.gameMode() != null ? session.gameMode().label : "N/A"),
                "&9----------------"
        );
    }

    /* =========================================================
       HELPERS
       ========================================================= */

    private void applyWorldSettings() {
        World w = session.world();
        if (w == null) return;

        Location c = session.center();
        if (c != null) {
            WorldBorder wb = w.getWorldBorder();
            wb.setCenter(c);
            if (session.mapSize() != null) {
                wb.setSize(session.mapSize().size);
            }
        }

        w.setGameRule(GameRule.BLOCK_EXPLOSION_DROP_DECAY, false);
        w.setGameRule(GameRule.DISABLE_ELYTRA_MOVEMENT_CHECK, true);
        w.setGameRule(GameRule.DO_FIRE_TICK, false);
        w.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        w.setGameRule(GameRule.FALL_DAMAGE, false);
        w.setGameRule(GameRule.KEEP_INVENTORY, true);
        w.setGameRule(GameRule.MOB_EXPLOSION_DROP_DECAY, false);
        w.setGameRule(GameRule.PLAYERS_SLEEPING_PERCENTAGE, 101);
        w.setGameRule(GameRule.SHOW_DEATH_MESSAGES, false);
        w.setGameRule(GameRule.SPAWN_CHUNK_RADIUS, 0);
        w.setGameRule(GameRule.SPAWN_RADIUS, 0);
        w.setGameRule(GameRule.TNT_EXPLOSION_DROP_DECAY, false);
        w.setTime(0);
        w.setStorm(false);
        w.setThundering(false);
    }

    private void restoreEnvironmentToDefault() {
        session.restoreOriginalBorder();

        World w = session.world();
        if (w == null) return;

        w.setGameRule(GameRule.BLOCK_EXPLOSION_DROP_DECAY, true);
        w.setGameRule(GameRule.DISABLE_ELYTRA_MOVEMENT_CHECK, false);
        w.setGameRule(GameRule.DO_FIRE_TICK, true);
        w.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, false);
        w.setGameRule(GameRule.DO_WEATHER_CYCLE, true);
        w.setGameRule(GameRule.FALL_DAMAGE, true);
        w.setGameRule(GameRule.KEEP_INVENTORY, false);
        w.setGameRule(GameRule.MOB_EXPLOSION_DROP_DECAY, true);
        w.setGameRule(GameRule.PLAYERS_SLEEPING_PERCENTAGE, 100);
        w.setGameRule(GameRule.SHOW_DEATH_MESSAGES, true);
        w.setGameRule(GameRule.SPAWN_CHUNK_RADIUS, 2);
        w.setGameRule(GameRule.SPAWN_RADIUS, 10);
        w.setGameRule(GameRule.TNT_EXPLOSION_DROP_DECAY, true);
        w.setTime(0);
        w.setStorm(false);
        w.setThundering(false);
    }

    private void applyVoteOptionToParticipants() {
        double mul = 1.0;
        if (session.income() != null) {
            mul = session.income().multiplier;
        }

        for (UUID id : session.getParticipantsView()) {
            session.getPlasmaMap().put(id, Core.gameConfig.basePlasma);
            session.getScoreMap().put(id, Core.gameConfig.baseScore);
            double perPlayerIncome = Core.gameConfig.baseIncomePerSecond * mul;
            session.getIncomeMap().put(id, perPlayerIncome);
        }
    }

    public void setSurvivalAndTeleportRandomly(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p == null || !p.isOnline()) return;

        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setExhaustion(0f);
        p.setHealth(p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
        p.setGameMode(GameMode.SURVIVAL);

        World world = session.world();
        Location center = session.center();
        MapSizeOption sizeOpt = session.mapSize();
        if (world == null || center == null || sizeOpt == null) return;

        double half = sizeOpt.size / 2.0;
        double usable = half * 0.95;

        double dx = (RNG.nextDouble() * 2.0 - 1.0) * usable;
        double dz = (RNG.nextDouble() * 2.0 - 1.0) * usable;

        double targetX = center.getX() + dx;
        double targetZ = center.getZ() + dz;

        int highest = world.getHighestBlockYAt((int) Math.floor(targetX), (int) Math.floor(targetZ));
        double targetY = highest + 100.0;

        Location dest = new Location(world, targetX + 0.5, targetY, targetZ + 0.5);

        p.teleport(dest);
        p.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOW_FALLING,
                10 * 20,
                0,
                false,
                false,
                false
        ));
    }

    public void setSpectatorAndTeleportToCenter(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p == null || !p.isOnline()) return;

        p.setGameMode(GameMode.SPECTATOR);

        World world = session.world();
        Location center = session.center();
        MapSizeOption sizeOpt = session.mapSize();
        if (world == null || center == null || sizeOpt == null) return;

        double targetX = center.getX();
        double targetZ = center.getZ();

        int highest = world.getHighestBlockYAt((int) Math.floor(targetX), (int) Math.floor(targetZ));
        double targetY = highest + 100.0;

        Location dest = new Location(world, targetX + 0.5, targetY, targetZ + 0.5);

        p.teleport(dest);
    }
}