package net.groundzero.game;

import net.groundzero.app.Core;
import net.groundzero.ui.options.MapSizeOption;
import net.groundzero.ui.options.GameModeOption;
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
 * Start → voting → running → end → IDLE
 */
public class GameManager {

    private final GameSession session = new GameSession();
    private static final Random RNG = new Random();

    // getter will be one-liner on your side
    public GameSession session() { return session; }

    public GameManager() {}

    /* =========================================================
       PUBLIC ENTRYPOINT
       ========================================================= */

    /**
     * Start the game flow from IDLE.
     */
    public void start(Player p) {
        GameState st = session.state();

        if (st == GameState.IDLE) {
            if (p != null) startFromIdle(p); // actually performs start
            // text/sound feedback is in startFromIdle;
            return;
        } else if (st.isPregame()) {
            if (p != null)
                Core.notifier.message(p, true, "The game is already starting");
            return;
        }
        if (p != null)
            Core.notifier.message(p, true, "The game is already running");
    }

    /**
     * Soft cancel: called by player quit / command during pre-game.
     * If the game is in pregame, we run cancel();
     * If already running, we just notify "already running".
     */
    public void tryCancel(Player p) {
        GameState st = session.state();

        if (st == GameState.IDLE) {
            if (p != null)
                Core.notifier.message(p, true, "There is no game starting");
            return;
        } else if (st.isPregame()) {
            Core.notifier.broadcast(
                    Bukkit.getOnlinePlayers(),
                    Sound.BLOCK_ANVIL_LAND,
                    Notifier.PitchLevel.LOW,
                    true,
                    "GroundZero canceled by &a" + p.getName());
            cancel(); // actually performs cleanup
            return;
        }

        if (p != null)
            Core.notifier.message(p, true, "The game is already running");
    }

    /**
     * Hard cancel: force stop everything regardless of state.
     * Used by admin command or plugin shutdown.
     */
    public void forceCancel(Player sender) {
        // Stop TickBus-bound services if they were started.
        if (Core.gameRuntimeService != null) Core.gameRuntimeService.stop();
        if (Core.scoreboardService != null) Core.scoreboardService.stop();
        if (Core.combatIdleService != null) Core.combatIdleService.stop();
        if (Core.tickBus != null) Core.tickBus.stop();

        // Cancel all scheduled jobs (respawn timers, vote countdowns, etc.).
        Core.schedulers.cancelAll();

        // Combat last hits and runtime numbers only matter within a single match.
        Core.damageService.clearAllLastHits();
        Core.session.clearRuntimeAndOptions();
        Core.playerService.resetDeathState();

        // Restore world border / gamerules / player state.
        restoreEnvironmentToDefault();

        // Always land back on IDLE, with everyone in spectator lobby state.
        session.resetToAllSpectators();
        session.setState(GameState.IDLE);
    }

    /**
     * Cancel a starting game (pre-game only).
     * This MUST NOT go through endGame(), because pre-game usually has:
     * - no scoreboard
     * - no runtime tick
     * - only scheduled votes / countdowns
     */
    private void cancel() {
        session.setState(GameState.IDLE);

        // Everyone back to spectators in the lobby.
        session.resetToAllSpectators();

        // Stop countdowns / vote timers / GUI-only schedulers.
        Core.schedulers.cancelAll();

        // Close vote/setup GUIs.
        Core.guiService.closeAllGZViews();

        // Clear runtime numbers and options so the next start is clean.
        Core.session.clearRuntimeAndOptions();
    }

    /**
     * Normal match end — calls forceCancel after delay.
     */
    public void endGame() {
        GameState st = session.state();

        if (!st.isIngame()) { cancel(); return; } // should not be here btw

        session.setState(GameState.ENDED);
        Core.guiService.closeAllGZViews();

        Core.notifier.broadcast(
                Bukkit.getOnlinePlayers(),
                Sound.ENTITY_ENDER_DRAGON_GROWL,
                Notifier.PitchLevel.MID,
                false,
                "Game Over"
        ); // TODO : send title or smth

        for (UUID id : session.getParticipantsView()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;

            // reset player inv and xp, then make them spectator
            p.getInventory().clear();
            p.setExp(0f);
            p.setLevel(0);
            p.setTotalExperience(0);
            p.setGameMode(GameMode.SPECTATOR);
        }

        // Stop TickBus-bound services that are only meaningful during RUNNING.
        if (Core.gameRuntimeService != null) Core.gameRuntimeService.stop();
        if (Core.scoreboardService != null) Core.scoreboardService.stop();
        if (Core.combatIdleService != null) Core.combatIdleService.stop();
        if (Core.tickBus != null) Core.tickBus.stop();

        // Cancel any remaining scheduled jobs (respawn timers, etc.).
        Core.schedulers.cancelAll();
        // Clear combat last hits – they matter only within a running match.
        Core.damageService.clearAllLastHits();
        // clear death state
        Core.playerService.resetDeathState();

        // print, and then go to IDLE (prep for next game)
        Core.schedulers.runLater(() -> {
            for (UUID id : session.getParticipantsView()) {
                Player p = Bukkit.getPlayer(id);
                if (p == null) continue;

                double score = session.getScoreMap().getOrDefault(id, 0.0);
                Core.notifier.broadcast(
                        Bukkit.getOnlinePlayers(),
                        null, null, false,
                        p.getName() + " : " + String.format("%.1f", score)
                ); // TODO : print / now testing, so not sorted
            }
        }, 20L);
        Core.schedulers.runLater(() -> {
            // print something more if needed
        }, 40L);
        Core.schedulers.runLater(() -> {
            for(Player p : Bukkit.getOnlinePlayers()) {
                if (p == null || !p.isOnline()) continue;

                p.setFoodLevel(20);
                p.setSaturation(20f);
                p.setExhaustion(0f);
                p.setHealth(p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
                p.setGameMode(GameMode.SURVIVAL);
                // don't really care if they die from drops, the game's over
            }
            // Clear per-player runtime values and vote options.
            Core.session.clearRuntimeAndOptions();

            // Finally return to IDLE, ready for the next game.
            restoreEnvironmentToDefault();
            session.setState(GameState.IDLE);
            session.resetToAllSpectators();
        }, Core.gameConfig.delayTicks);
    }

    /* =========================================================
       INTERNAL FLOWS
       ========================================================= */

    private void startFromIdle(Player sender) {
        // 1) collect participants
        session.snapshotParticipantsFromSpectators();

        // 2) world/center detect
        if (!session.captureWorldAndCenterFromParticipants()) {
            Core.notifier.broadcast(
                Bukkit.getOnlinePlayers(),
                Sound.BLOCK_ANVIL_LAND,
                Notifier.PitchLevel.LOW,
                true,
                "GroundZero start failed",
                "All players should be in the same world"
            );
            session.resetToAllSpectators(); // failed to start, reset to null
            return;
        }

        // 3) announce players
        Core.notifier.broadcast(
            Bukkit.getOnlinePlayers(),
            null,
            null,
            false,
            "Participants: " + session.namesOfParticipants()
    );
        // 4) go to first phase
        gotoCountdownBeforeVoting();
    }

    /* =========================================================
       PHASE JUMPS (used by VoteService)
       ========================================================= */

    private void gotoCountdownBeforeVoting() {
        session.setState(GameState.COUNTDOWN_BEFORE_VOTING);
        Core.voteService.startPreVoteCountdown(this::gotoVotingMapSize);
    }

    public void gotoVotingMapSize() {
        session.setState(GameState.VOTING_MAP_SIZE);
        Core.voteService.startMapSizeVote();
    }

    public void gotoVotingIncome() {
        session.setState(GameState.VOTING_INCOME_MULTIPLIER);
        Core.voteService.startIncomeVote();
    }

    public void gotoVotingGameMode() {
        session.setState(GameState.VOTING_GAME_MODE);
        Core.voteService.startGameModeVote();
    }

    public void gotoCountdownBeforeStart() {
        session.setState(GameState.COUNTDOWN_BEFORE_START);
        Core.guiService.closeAllGZViews();
        Core.voteService.startFinalCountdown(this::gotoRunning);
    }

    /* =========================================================
       RUNNING
       ========================================================= */

    private void gotoRunning() {

        // 1) Apply world settings based on chosen option (mapsize)
        applyWorldSettings();

        // 2) Apply player settings based on chosen option (income)
        applyVoteOptionToParticipants();

        // 3) Branch by game mode.
        GameModeOption mode = session.gameMode();
        if (mode == null) {
            // Fallback: treat as STANDARD.
            startStandardMode();
            return;
        }
        switch (mode) {
            case STANDARD:
                startStandardMode();
                break;

            // TODO: add more modes here (HARDCORE, SNIPER_ONLY, etc.)

            default:
                // Unknown mode → fallback to STANDARD behavior for safety.
                startStandardMode();
                break;
        }
    }

    private void startStandardMode() {
        // set match time
        session.setRemainingTicks(Core.gameConfig.matchDurationTicks);

        // start services bound to TickBus
        Core.gameRuntimeService.start(session);  // time, income
        Core.scoreboardService.start(session);   // UI-only
        Core.combatIdleService.start();          // subscriber persists
        Core.tickBus.start();

        // check participants, tp
        for (UUID id : session.getParticipantsView()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;

            // clear inv, xp
            p.getInventory().clear();
            p.setExp(0f);
            p.setLevel(0);
            p.setTotalExperience(0);

            // then spawn them(tp)
            setSurvivalAndTeleportRandomly(id);
        }
        // check spectators, tp
        for (UUID id : session.getSpectatorsView()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;

            // clear inv, xp
            p.getInventory().clear();
            p.setExp(0f);
            p.setLevel(0);
            p.setTotalExperience(0);

            // spawn participants(tp)
            setSpectatorAndTeleportToCenter(id);
        }

        // give loadouts
        Core.loadoutService.giveInitialLoadouts(session.getParticipantsView());

        // and change state after everything's done
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
        // world border
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

        // world rules
        w.setGameRule(GameRule.BLOCK_EXPLOSION_DROP_DECAY, false);
        w.setGameRule(GameRule.DISABLE_ELYTRA_MOVEMENT_CHECK, true);
        w.setGameRule(GameRule.DO_FIRE_TICK, false);
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
        // a) world border back
        Core.game.session().restoreOriginalBorder();

        World w = session.world();
        if (w == null) return;

        // world rules
        w.setGameRule(GameRule.BLOCK_EXPLOSION_DROP_DECAY, true);
        w.setGameRule(GameRule.DISABLE_ELYTRA_MOVEMENT_CHECK, false);
        w.setGameRule(GameRule.DO_FIRE_TICK, true);
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
        // Use final income vote result stored in session.
        double mul = 1.0;
        if (session.income() != null) {
            mul = session.income().multiplier;
        }

        for (UUID id : session.getParticipantsView()) {
            // Initialize base plasma / score for this match.
            session.getPlasmaMap().put(id, Core.gameConfig.basePlasma);
            session.getScoreMap().put(id, Core.gameConfig.baseScore);

            // Initialize per-player income with chosen multiplier.
            double perPlayerIncome = Core.gameConfig.baseIncomePerSecond * mul;
            session.getIncomeMap().put(id, perPlayerIncome);
        }
    }

    public void setSurvivalAndTeleportRandomly(UUID id) {
        // ready participants whatever their gamemode, status is
        Player p = Bukkit.getPlayer(id);
        if (p == null || !p.isOnline()) return;

        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setExhaustion(0f);
        p.setHealth(p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
        p.setGameMode(GameMode.SURVIVAL);

        // tp
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

        Location dest = new Location(
                world,
                targetX + 0.5,
                targetY,
                targetZ + 0.5
        );

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

        Location dest = new Location(
                world,
                targetX + 0.5,
                targetY,
                targetZ + 0.5
        );

        p.teleport(dest);
    }
}
