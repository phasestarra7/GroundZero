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

public class GameManager {

    private final GameSession session = new GameSession(); // ← now final, we reuse it

    private static final Random RNG = new Random();

    private int ticksLeft = 0;

    public GameManager() {}

    public GameSession session() { return session; }

    /* =========================================================
       START
       ========================================================= */

    public void start(Player p) {
        switch (session.state()) {
            case IDLE -> {
                session.snapshotParticipantsFromSpectators();
                initRuntimeForParticipants();

                if (!session.captureWorldAndCenterFromParticipants()) {
                    Core.notifier.broadcast(
                            Bukkit.getOnlinePlayers(),
                            Sound.BLOCK_ANVIL_LAND,
                            Notifier.PitchLevel.LOW,
                            true,
                            "GroundZero start failed: players should be in the same world"
                    );
                    session.setState(GameState.IDLE);
                    return;
                }

                Core.notifier.broadcast(
                        Bukkit.getOnlinePlayers(),
                        null,
                        null,
                        false,
                        "Participants : " + session.namesOfParticipants()
                );

                gotoCountdownBeforeVoting();
            }
            case COUNTDOWN_BEFORE_VOTING,
                 VOTING_MAP_SIZE,
                 VOTING_INCOME_MULTIPLIER,
                 VOTING_GAME_MODE,
                 COUNTDOWN_BEFORE_START -> {
                if (p != null) {
                    Core.notifier.messageError(p, "The game is already starting.");
                }
            }
            case RUNNING, ENDED -> {
                if (p != null) {
                    Core.notifier.messageError(p, "The game is already running.");
                }
            }
            default -> {}
        }
    }

    public void cancel(Player p) {
        switch (session.state()) {
            case IDLE -> {
                if (p != null) {
                    Core.notifier.messageError(p, "There is no game starting.");
                }
            }
            case COUNTDOWN_BEFORE_VOTING,
                 VOTING_MAP_SIZE,
                 VOTING_INCOME_MULTIPLIER,
                 VOTING_GAME_MODE,
                 COUNTDOWN_BEFORE_START -> {
                Core.notifier.broadcast(
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
                    Core.notifier.messageError(p, "The game is already running.");
                }
            }
            default -> {}
        }
    }

    public void cancelAll() {
        session.setState(GameState.IDLE);

        session.restoreOriginalBorder();
        Core.schedulers.cancelAll();
        if (Core.scoreboardService != null) {
            Core.scoreboardService.clearAll();
        }

        Core.guiService.closeAllGZViews();

        // re-add online players as spectators
        Set<UUID> online = new HashSet<>();
        for (Player op : Bukkit.getOnlinePlayers()) {
            online.add(op.getUniqueId());
        }
        session.resetToAllSpectators(online);
    }

    /* =========================================================
       Flow skeleton (VoteService drives actual vote)
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
       Running
       ========================================================= */

    private void gotoRunning() {
        session.setState(GameState.RUNNING);

        World w = session.world();
        Location c = session.center();
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

        ticksLeft = Core.gameConfig.matchDurationTicks;
        startScoreboardTick();

        for (UUID id : session.getParticipantsView()) {
            teleportPlayerRandomly(id);
        }

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

    /* called from VoteService when income is chosen */
    public void applyIncomeOptionToParticipants(IncomeOption chosen) {
        if (chosen == null) return;
        double mul = chosen.multiplier;
        for (UUID id : session.getParticipantsView()) {
            double perPlayerIncome = Core.gameConfig.baseIncomePerSecond * mul;
            session.getIncomeMap().put(id, perPlayerIncome);
        }
    }

    public void endGame() {
        Core.notifier.broadcast(
                Bukkit.getOnlinePlayers(),
                Sound.UI_TOAST_CHALLENGE_COMPLETE,
                Notifier.PitchLevel.OK,
                false,
                "GroundZero ended."
        );

        Core.schedulers.cancelAll();
        session.restoreOriginalBorder();
        Core.guiService.closeAllGZViews();
        if (Core.scoreboardService != null) {
            Core.scoreboardService.clearAll();
        }

        session.setState(GameState.ENDED);
    }

    /* =========================================================
       per-tick update
       ========================================================= */

    private void startScoreboardTick() {
        Core.schedulers.runTimer(() -> {
            if (session.state() != GameState.RUNNING) {
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
                        : Core.gameConfig.baseIncomePerSecond;

                double incomePerTick = incomePerSec / 20.0;

                double current = session.getPlasmaMap().getOrDefault(id, Core.gameConfig.basePlasma);
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

    private void setUpGameRules() {
        World w = session.world();
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
            session.getPlasmaMap().put(id, Core.gameConfig.basePlasma);

            double perPlayerIncome = Core.gameConfig.baseIncomePerSecond * sessionMul;
            session.getIncomeMap().put(id, perPlayerIncome);

            session.getScoreMap().put(id, Core.gameConfig.baseScore);
        }
    }
}
