package net.groundzero.game;

import net.groundzero.app.Core;
import net.groundzero.ui.options.IncomeOption;
import net.groundzero.ui.options.MapSizeOption;
import net.groundzero.util.Notifier;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
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
        cancel();
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

        restoreEnvironmentToDefault();

        if (Core.gameRuntimeService != null) Core.gameRuntimeService.stop();
        if (Core.scoreboardService != null) Core.scoreboardService.stop();
        if (Core.combatIdleService != null) Core.combatIdleService.stop();
        if (Core.tickBus != null) Core.tickBus.stop();

        Core.schedulers.cancelAll();
        Core.guiService.closeAllGZViews();
    }

    /**
     * Normal match end — calls forceCancel after delay.
     */
    public void endGame() {
        session.setState(GameState.ENDED);
        Core.guiService.closeAllGZViews();

        Core.notifier.broadcast(
                Bukkit.getOnlinePlayers(),
                Sound.ENTITY_ENDER_DRAGON_GROWL,
                Notifier.PitchLevel.MID,
                false,
                "GroundZero ended."
        );
        for (UUID id : session.getParticipantsView()) {
            Core.notifier.broadcast(
                    Bukkit.getOnlinePlayers(),
                    null,
                    null,
                    false,
                    Bukkit.getPlayer(id).getName() + " : " + session.getScoreMap().getOrDefault(id, 0.0)
            );
        }  // testing, without sorting

        if (Core.gameRuntimeService != null) Core.gameRuntimeService.stop();
        if (Core.scoreboardService != null) Core.scoreboardService.stop();
        if (Core.combatIdleService != null) Core.combatIdleService.stop();
        if (Core.tickBus != null) Core.tickBus.stop();

        if (Core.plugin != null && Core.plugin.isEnabled())
            Core.schedulers.runLater(() -> forceCancel(null), 1L);
        else
            forceCancel(null);
    }

    /* =========================================================
       INTERNAL FLOWS
       ========================================================= */

    private void startFromIdle(Player sender) {
        // 1) collect participants
        session.snapshotParticipantsFromSpectators();

        // 2) init per-player runtime
        initRuntimeForParticipants();

        // 3) world/center detect
        if (!session.captureWorldAndCenterFromParticipants()) {
            Core.notifier.broadcast(
                Bukkit.getOnlinePlayers(),
                Sound.BLOCK_ANVIL_LAND,
                Notifier.PitchLevel.LOW,
                true,
                "GroundZero start failed",
                "All players should be in the same world"
            );
            session.setState(GameState.IDLE);
            return;
        }

        // 4) announce players
        Core.notifier.broadcast(
            Bukkit.getOnlinePlayers(),
            null,
            null,
            false,
            "Participants: " + session.namesOfParticipants()
    );

        // 5) go to first phase
        gotoCountdownBeforeVoting();
    }

    /**
     * This is the ONLY place that does full cleanup.
     * It can be called while plugin is disabling.
     */

    private void restoreEnvironmentToDefault() {
        // a) world border back
        Core.game.session().restoreOriginalBorder();

        // b) players to spectator (your session already knows how)
        Set<UUID> online = new HashSet<>();
        for (org.bukkit.entity.Player op : org.bukkit.Bukkit.getOnlinePlayers()) {
            online.add(op.getUniqueId());
        }
        Core.game.session().resetToAllSpectators();

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

        // participants initial state
        for (UUID id : session.getParticipantsView()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;

            p.getInventory().clear();
            p.setExp(0f);
            p.setLevel(0);
            p.setTotalExperience(0);
            p.setFoodLevel(20);
            p.setSaturation(20f);
            p.setExhaustion(0f);
            p.setHealth(p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
            p.setGameMode(GameMode.SURVIVAL);
        }
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

        // set world border
        World w = session.world();
        Location c = session.center();
        if (w != null && c != null) {
            WorldBorder wb = w.getWorldBorder();
            wb.setCenter(c);
            if (session.mapSize() != null) {
                wb.setSize(session.mapSize().size);
            }
        }

        // setup world / players
        setUpGame();

        // give loadouts
        Core.loadoutService.giveInitialLoadouts(session.getParticipantsView());

        // set match time
        session.setRemainingTicks(Core.gameConfig.matchDurationTicks);

        // start services bound to TickBus
        Core.gameRuntimeService.start(session);  // time, income
        Core.scoreboardService.start(session);   // UI-only
        Core.combatIdleService.start(); // subscriber persists
        Core.tickBus.start();

        // random spawn inside border
        for (UUID id : session.getParticipantsView()) {
            teleportParticipantRandomly(id);
        }
        // TODO : teleport spectators
        session.setState(GameState.RUNNING); // ... and change state after everything's done

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
       APPLY VOTE OPTIONS
       ========================================================= */

    public void applyIncomeOptionToParticipants(IncomeOption chosen) {
        if (chosen == null) return;
        double mul = chosen.multiplier;
        for (UUID id : session.getParticipantsView()) {
            double perPlayerIncome = Core.gameConfig.baseIncomePerSecond * mul;
            session.getIncomeMap().put(id, perPlayerIncome);
        }
    }

    /* =========================================================
       HELPERS
       ========================================================= */

    private void setUpGame() {
        World w = session.world();
        if (w == null) return;

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

        // participants initial state
        for (UUID id : session.getParticipantsView()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;

            p.getInventory().clear();
            p.setExp(0f);
            p.setLevel(0);
            p.setTotalExperience(0);
            p.setFoodLevel(20);
            p.setSaturation(20f);
            p.setExhaustion(0f);
            p.setHealth(p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue());
            p.setGameMode(GameMode.SURVIVAL);
        }
    }

    private void initRuntimeForParticipants() {
        double sessionMul = 1.0;
        if (session.income() != null) {
            sessionMul = session.income().multiplier;
        }

        for (UUID id : session.getParticipantsView()) {
            session.getPlasmaMap().put(id, Core.gameConfig.basePlasma);
            double perPlayerIncome = Core.gameConfig.baseIncomePerSecond * sessionMul;
            session.getIncomeMap().put(id, perPlayerIncome);
            session.getScoreMap().put(id, Core.gameConfig.baseScore);
        }
    }

    public void teleportParticipantRandomly(UUID id) {
        World world = session.world();
        Location center = session.center();
        MapSizeOption sizeOpt = session.mapSize();
        if (world == null || center == null || sizeOpt == null) return;

        Player p = Bukkit.getPlayer(id);
        if (p == null || !p.isOnline()) return;

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

    public void teleportSpectatorsAndChangeGamemode(UUID id) {
        World world = session.world();
        Location center = session.center();
        MapSizeOption sizeOpt = session.mapSize();
        if (world == null || center == null || sizeOpt == null) return;

        Player p = Bukkit.getPlayer(id);
        if (p == null || !p.isOnline()) return;

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

        p.setGameMode(GameMode.SPECTATOR);
        p.teleport(dest);
    }
}
