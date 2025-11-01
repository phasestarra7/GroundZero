package net.groundzero.game;

import net.groundzero.app.Core;
import net.groundzero.ui.options.GameModeOption;
import net.groundzero.ui.options.IncomeOption;
import net.groundzero.ui.options.MapSizeOption;
import net.groundzero.util.Notifier;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * Central game flow controller.
 *
 * NOTE:
 * - Voting logic is delegated to VoteService.
 * - We keep method names to show the flow order.
 * - We do NOT use forEachParticipant().
 */
public class GameManager {

    private GameSession session = new GameSession();
    private GameState state = GameState.IDLE;

    private static final Random RNG = new Random();

    private int ticksLeft = 0;

    public GameManager() {}

    public GameSession session() { return session; }

    public GameState state() { return state; }

    public void setState(GameState s) {
        this.state = s;
    }

    /* =========================================================
       START
       ========================================================= */

    public void start(Player p) {
        switch (state) {
            case IDLE -> {
                session.snapshotParticipantsFromSpectators();
                initRuntimeForParticipants();

                if (!captureWorldAndCenterFromParticipants()) {
                    Core.notify.broadcast(
                            Bukkit.getOnlinePlayers(),
                            Sound.BLOCK_ANVIL_LAND,
                            Notifier.PitchLevel.LOW,
                            true,
                            "GroundZero start failed: players are in different worlds."
                    );
                    session = new GameSession();
                    state = GameState.IDLE;
                    return;
                }

                Core.notify.broadcast(
                        Bukkit.getOnlinePlayers(),
                        null,
                        null,
                        false,
                        "Participants : " + namesOf(session.getParticipantsView())
                );

                gotoCountdownBeforeVoting();
            }
            case COUNTDOWN_BEFORE_VOTING,
                 VOTING_MAP_SIZE,
                 VOTING_INCOME_MULTIPLIER,
                 VOTING_GAME_MODE,
                 COUNTDOWN_BEFORE_START -> {
                if (p != null) {
                    Core.notify.messageError(p, "The game is already starting.");
                }
            }
            case RUNNING, ENDED -> {
                if (p != null) {
                    Core.notify.messageError(p, "The game is already running.");
                }
            }
            default -> {}
        }
    }

    private boolean captureWorldAndCenterFromParticipants() {
        Set<UUID> participants = session.getParticipantsView();
        if (participants.isEmpty()) {
            return false;
        }

        World commonWorld = null;
        double sumX = 0;
        double sumZ = 0;
        int count = 0;

        for (UUID id : participants) {
            Player pp = Bukkit.getPlayer(id);
            if (pp == null || !pp.isOnline()) continue;

            Location loc = pp.getLocation();
            if (commonWorld == null) {
                commonWorld = loc.getWorld();
            } else {
                if (!commonWorld.equals(loc.getWorld())) {
                    return false;
                }
            }

            sumX += loc.getX();
            sumZ += loc.getZ();
            count++;
        }

        if (commonWorld == null || count == 0) {
            return false;
        }

        double avgX = sumX / count;
        double avgZ = sumZ / count;

        int highestY = commonWorld.getHighestBlockYAt((int) Math.floor(avgX), (int) Math.floor(avgZ));
        Location center = new Location(commonWorld, avgX, highestY, avgZ);

        session.setWorld(commonWorld);
        session.setCenter(center);
        session.captureOriginalBorder(commonWorld);

        return true;
    }

    public void cancel(Player p) {
        switch (state) {
            case IDLE -> {
                if (p != null) {
                    Core.notify.messageError(p, "There is no game starting.");
                }
            }
            case COUNTDOWN_BEFORE_VOTING,
                 VOTING_MAP_SIZE,
                 VOTING_INCOME_MULTIPLIER,
                 VOTING_GAME_MODE,
                 COUNTDOWN_BEFORE_START -> {
                Core.notify.broadcast(
                        Bukkit.getOnlinePlayers(),
                        Sound.BLOCK_ANVIL_LAND,
                        Notifier.PitchLevel.LOW,
                        true,
                        "GroundZero cancelled by " + (p != null ? p.getDisplayName() : "server")
                );
                cancelAll();
            }
            case RUNNING, ENDED -> {
                if (p != null) {
                    Core.notify.messageError(p, "The game is already running.");
                }
            }
            default -> {}
        }
    }

    public void cancelAll() {
        state = GameState.IDLE;

        session.restoreOriginalBorder();
        Core.schedulers.cancelAll();
        if (Core.scoreboardService != null) {
            Core.scoreboardService.clearAll();
        }

        Core.ui.closeAllGZViews();

        session = new GameSession();

        Set<UUID> online = new HashSet<>();
        for (Player op : Bukkit.getOnlinePlayers()) {
            online.add(op.getUniqueId());
        }
        session.resetToAllSpectators(online);
    }

    /* =========================================================
       display util
       ========================================================= */

    public String namesOf(Iterable<UUID> ids) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (UUID id : ids) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            if (!first) sb.append(", ");
            sb.append("§a").append(p.getName()).append("§f");
            first = false;
        }
        if (first) return "";
        return sb.toString();
    }

    /* =========================================================
       Flow skeleton (delegates to VoteService)
       ========================================================= */

    private void gotoCountdownBeforeVoting() {
        state = GameState.COUNTDOWN_BEFORE_VOTING;
        Core.votes.startPreVoteCountdown(this::gotoVotingMapSize);
    }

    public void gotoVotingMapSize() {
        state = GameState.VOTING_MAP_SIZE;
        Core.votes.startMapSizeVote();
    }

    public void gotoVotingIncome() {
        state = GameState.VOTING_INCOME_MULTIPLIER;
        Core.votes.startIncomeVote();
    }

    public void gotoVotingGameMode() {
        state = GameState.VOTING_GAME_MODE;
        Core.votes.startGameModeVote();
    }

    public void gotoCountdownBeforeStart() {
        state = GameState.COUNTDOWN_BEFORE_START;
        Core.ui.closeAllGZViews();
        Core.votes.startFinalCountdown(this::gotoRunning);
    }

    /* =========================================================
       Running
       ========================================================= */

    private void gotoRunning() {
        state = GameState.RUNNING;

        World w = session.getWorld();
        Location c = session.getCenter();
        if (w != null && c != null) {
            WorldBorder wb = w.getWorldBorder();
            wb.setCenter(c);
            if (session.mapSize() != null) {
                wb.setSize(session.mapSize().size);
            }
        }

        setUpGameRules();
        Core.loadoutService.giveInitialLoadouts(session.getParticipantsView());

        if (Core.scoreboardService != null) {
            Core.scoreboardService.showGameBoard(session);
        }

        ticksLeft = Core.config.matchDurationTicks;
        startScoreboardTick();

        for (UUID id : session.getParticipantsView()) {
            teleportPlayerRandomly(id);
        }

        // keep format
        Core.notify.broadcast(
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
       Called by VoteService when an income is chosen
       ========================================================= */

    public void applyIncomeOptionToParticipants(IncomeOption chosen) {
        if (chosen == null) return;
        double mul = chosen.multiplier;
        for (UUID id : session.getParticipantsView()) {
            double perPlayerIncome = Core.config.baseIncomePerSecond * mul;
            session.getIncomeMap().put(id, perPlayerIncome);
        }
    }

    /* =========================================================
       End
       ========================================================= */

    public void endGame() {
        Core.notify.broadcast(
                Bukkit.getOnlinePlayers(),
                Sound.UI_TOAST_CHALLENGE_COMPLETE,
                Notifier.PitchLevel.OK,
                false,
                "GroundZero ended."
        );

        // stop all timers (includes per-tick plasma)
        Core.schedulers.cancelAll();

        session.restoreOriginalBorder();
        Core.ui.closeAllGZViews();
        if (Core.scoreboardService != null) {
            Core.scoreboardService.clearAll();
        }

        state = GameState.ENDED;
    }

    /* =========================================================
       per-tick update
       ========================================================= */

    private void startScoreboardTick() {
        Core.schedulers.runTimer(() -> {
            if (state != GameState.RUNNING) {
                return;
            }

            if (ticksLeft > 0) {
                ticksLeft -= 1;
            } else {
                endGame();
                return;
            }

            for (UUID id : session.getParticipantsView()) {
                double incomePerSec = (Core.scoreboardService != null)
                        ? Core.scoreboardService.getPerPlayerIncome(session, id)
                        : Core.config.baseIncomePerSecond;

                double incomePerTick = incomePerSec / 20.0;

                double current = session.getPlasmaMap().getOrDefault(id, Core.config.basePlasma);
                double next = current + incomePerTick;
                session.getPlasmaMap().put(id, next);

                if (Core.scoreboardService != null) {
                    Core.scoreboardService.refreshFromSession(session, id, ticksLeft);
                }
            }

        }, 1L, 1L);
    }

    /* =========================================================
       helpers
       ========================================================= */

    private void teleportPlayerRandomly(UUID id) {
        World world = session.getWorld();
        Location center = session.getCenter();
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

    private void setUpGameRules() {
        World w = session.getWorld();
        if (w == null) return;

        w.setGameRule(GameRule.BLOCK_EXPLOSION_DROP_DECAY, false);
        w.setGameRule(GameRule.DO_FIRE_TICK, false);
        w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        w.setGameRule(GameRule.FALL_DAMAGE, false);
        w.setGameRule(GameRule.KEEP_INVENTORY, true);
        w.setGameRule(GameRule.MOB_EXPLOSION_DROP_DECAY, false);
        w.setGameRule(GameRule.NATURAL_REGENERATION, false);
        w.setGameRule(GameRule.PLAYERS_SLEEPING_PERCENTAGE, 101);
        w.setGameRule(GameRule.SHOW_DEATH_MESSAGES, false);
        w.setGameRule(GameRule.SPAWN_CHUNK_RADIUS, 0);
        w.setGameRule(GameRule.SPAWN_RADIUS, 0);
        w.setGameRule(GameRule.TNT_EXPLOSION_DROP_DECAY, false);

        w.setTime(0);
        w.setStorm(false);
        w.setThundering(false);

        for (UUID id : session.getParticipantsView()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) continue;
            p.getInventory().clear();
            p.setGameMode(GameMode.SURVIVAL);
        }
    }

    private void initRuntimeForParticipants() {
        double sessionMul = 1.0;
        if (session.income() != null) {
            sessionMul = session.income().multiplier;
        }

        for (UUID id : session.getParticipantsView()) {
            session.getPlasmaMap().put(id, Core.config.basePlasma);

            double perPlayerIncome = Core.config.baseIncomePerSecond * sessionMul;
            session.getIncomeMap().put(id, perPlayerIncome);

            session.getScoreMap().put(id, Core.config.baseScore);
        }
    }
}
