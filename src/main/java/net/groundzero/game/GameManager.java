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
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Central game flow controller.
 *
 * NOTE:
 * - We do NOT have forEachParticipant()/forAll() helpers here anymore.
 * - Everywhere we need to tell players, we pass either:
 *      * session.getParticipantsView()
 *      * Bukkit.getOnlinePlayers()
 *   into Core.notify (which now accepts iterables).
 * - Core.ui.closeAllGZViews() is used (typo fixed).
 */
public class GameManager {

    private GameSession session = new GameSession();
    private GameState state = GameState.IDLE;
    private boolean acceptingVotes = false;

    private final Map<MapSizeOption, Integer> mapVotes = new EnumMap<>(MapSizeOption.class);
    private final Map<IncomeOption, Integer> incomeVotes = new EnumMap<>(IncomeOption.class);
    private final Map<GameModeOption, Integer> modeVotes = new EnumMap<>(GameModeOption.class);

    private final Map<UUID, MapSizeOption> votedMapSize = new HashMap<>();
    private final Map<UUID, IncomeOption> votedIncome = new HashMap<>();
    private final Map<UUID, GameModeOption> votedMode = new HashMap<>();

    private static final Random RNG = new Random();

    private int ticksLeft = 0;

    public GameManager() {}

    public GameSession session() { return session; }

    public GameState state() { return state; }

    /* =========================================================
       START
       ========================================================= */

    public void start(Player p) {
        switch (state) {
            case IDLE -> {
                // take current spectators → participants
                session.snapshotParticipantsFromSpectators();
                initRuntimeForParticipants();

                if (!captureWorldAndCenterFromParticipants()) {
                    // fail → cancel
                    Core.notify.broadcast(
                            Bukkit.getOnlinePlayers(),
                            Sound.BLOCK_ANVIL_LAND,
                            Notifier.PitchLevel.LOW,
                            true,
                            "GroundZero start failed: players are in different worlds."
                    );
                    // go back to idle state
                    session = new GameSession(); // clean
                    return;
                }

                // tell everyone online who is playing
                Core.notify.broadcast(
                        Bukkit.getOnlinePlayers(),
                        null,
                        null,
                        false,
                        "Participants: " + namesOf(session.getParticipantsView())
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
            // no participants? treat as fail
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
                // different world → fail
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

        // average
        double avgX = sumX / count;
        double avgZ = sumZ / count;

        // get highest Y at that x,z
        int highestY = commonWorld.getHighestBlockYAt((int) Math.floor(avgX), (int) Math.floor(avgZ));
        Location center = new Location(commonWorld, avgX, highestY, avgZ);

        // save to session
        session.setWorld(commonWorld);
        session.setCenter(center);

        // save original border (for cancel)
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

        // stop tasks
        session.restoreOriginalBorder();
        Core.schedulers.cancelAll();
        Core.scoreboardService.clearAll();

        // close all GZ inventories
        Core.ui.closeAllGZViews();

        // reset session
        session = new GameSession();

        // re-add online players as spectators
        Set<UUID> online = new HashSet<>();
        for (Player op : Bukkit.getOnlinePlayers()) {
            online.add(op.getUniqueId());
        }
        session.resetToAllSpectators(online);
    }

    /* =========================================================
       Util
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
       Phase: countdown before voting
       ========================================================= */

    private void gotoCountdownBeforeVoting() {
        state = GameState.COUNTDOWN_BEFORE_VOTING;
        startCountdown(5, this::gotoVotingMapSize);
    }

    private void startCountdown(int seconds, Runnable onDone) {
        if (seconds <= 0) {
            Core.schedulers.runLater(onDone, 1L);
            return;
        }

        Core.notify.broadcast(
                session.getParticipantsView(),
                Sound.BLOCK_NOTE_BLOCK_PLING,
                Notifier.PitchLevel.MID,
                false,
                "GroundZero starting in " + seconds
        );

        Core.schedulers.runLater(() -> startCountdown(seconds - 1, onDone), 20L);
    }

    /* =========================================================
       Phase: MAP SIZE voting
       ========================================================= */

    private void gotoVotingMapSize() {
        state = GameState.VOTING_MAP_SIZE;
        acceptingVotes = true;
        votedMapSize.clear();
        mapVotes.clear();
        for (MapSizeOption opt : MapSizeOption.values()) {
            mapVotes.put(opt, 0);
        }

        Core.ui.newMapSize();

        // open GUI for participants
        for (UUID id : session.getParticipantsView()) {
            Player pp = Bukkit.getPlayer(id);
            if (pp == null || !pp.isOnline()) continue;
            Core.notify.sound(pp, Sound.UI_BUTTON_CLICK, Notifier.PitchLevel.MID);
            Core.ui.openMapSize(pp);
        }

        // 3-2-1 notice
        Core.schedulers.runLater(() -> Core.notify.broadcast(
                session.getParticipantsView(),
                Sound.BLOCK_NOTE_BLOCK_BELL,
                Notifier.PitchLevel.OK,
                false,
                "Ending vote for map size in §a3"
        ), 7 * 20L);

        Core.schedulers.runLater(() -> Core.notify.broadcast(
                session.getParticipantsView(),
                Sound.BLOCK_NOTE_BLOCK_BELL,
                Notifier.PitchLevel.OK,
                false,
                "Ending vote for map size in §a2"
        ), 8 * 20L);

        Core.schedulers.runLater(() -> Core.notify.broadcast(
                session.getParticipantsView(),
                Sound.BLOCK_NOTE_BLOCK_BELL,
                Notifier.PitchLevel.OK,
                false,
                "Ending vote for map size in §a1"
        ), 9 * 20L);

        Core.schedulers.runLater(this::finishMapSizeVotePhase, 10 * 20L);
    }

    private void finishMapSizeVotePhase() {
        acceptingVotes = false;

        int max = 0;
        for (MapSizeOption o : MapSizeOption.values()) {
            max = Math.max(max, mapVotes.getOrDefault(o, 0));
        }

        List<MapSizeOption> ties = new ArrayList<>();
        for (MapSizeOption o : MapSizeOption.values()) {
            if (mapVotes.getOrDefault(o, 0) == max) {
                ties.add(o);
            }
        }

        // keep only tied options in GUI
        Core.ui.retainOnlyMapSize(ties);

        // after a short delay, pick one
        Core.schedulers.runLater(() -> {
            MapSizeOption chosen = pickRandom(ties);
            if (chosen != null) {
                Core.ui.highlightMapSizeSelected(chosen.label, chosen.slot);
                session.setMapSize(chosen);
                Core.notify.broadcast(
                        session.getParticipantsView(),
                        Sound.ENTITY_PLAYER_LEVELUP,
                        Notifier.PitchLevel.MID,
                        false,
                        "Map size selected : §a" + chosen.label
                );
            }
            Core.schedulers.runLater(this::gotoVotingIncome, 3 * 20L);
        }, 2 * 20L);
    }

    /* =========================================================
       Phase: INCOME voting
       ========================================================= */

    private void gotoVotingIncome() {
        state = GameState.VOTING_INCOME_MULTIPLIER;
        acceptingVotes = true;
        votedIncome.clear();
        incomeVotes.clear();
        for (IncomeOption opt : IncomeOption.values()) {
            incomeVotes.put(opt, 0);
        }

        Core.ui.newIncome();

        for (UUID id : session.getParticipantsView()) {
            Player pp = Bukkit.getPlayer(id);
            if (pp == null || !pp.isOnline()) continue;
            Core.notify.sound(pp, Sound.UI_BUTTON_CLICK, Notifier.PitchLevel.MID);
            Core.ui.openIncome(pp);
        }

        Core.schedulers.runLater(() -> Core.notify.broadcast(
                session.getParticipantsView(),
                Sound.BLOCK_NOTE_BLOCK_BELL,
                Notifier.PitchLevel.OK,
                false,
                "Ending vote for income multiplier in §a3"
        ), 7 * 20L);

        Core.schedulers.runLater(() -> Core.notify.broadcast(
                session.getParticipantsView(),
                Sound.BLOCK_NOTE_BLOCK_BELL,
                Notifier.PitchLevel.OK,
                false,
                "Ending vote for income multiplier in §a2"
        ), 8 * 20L);

        Core.schedulers.runLater(() -> Core.notify.broadcast(
                session.getParticipantsView(),
                Sound.BLOCK_NOTE_BLOCK_BELL,
                Notifier.PitchLevel.OK,
                false,
                "Ending vote for income multiplier in §a1"
        ), 9 * 20L);

        Core.schedulers.runLater(this::finishIncomeVotePhase, 10 * 20L);
    }

    public void endGame() { //TODO : END GAME
        // notify players (optional)
        Core.notify.broadcast(
                Bukkit.getOnlinePlayers(),
                Sound.UI_TOAST_CHALLENGE_COMPLETE,
                Notifier.PitchLevel.OK,
                false,
                "GroundZero ended."
        );

        // go back to idle (same idea as cancelAll but without aggressive cancel of tasks, if you want)
        state = GameState.ENDED;

        // close GUIs
        // restore border to what it was before start
        session.restoreOriginalBorder();
        Core.ui.closeAllGZViews();
        Core.scoreboardService.clearAll();
    }

    private void finishIncomeVotePhase() {
        acceptingVotes = false;

        int max = 0;
        for (IncomeOption o : IncomeOption.values()) {
            max = Math.max(max, incomeVotes.getOrDefault(o, 0));
        }

        List<IncomeOption> ties = new ArrayList<>();
        for (IncomeOption o : IncomeOption.values()) {
            if (incomeVotes.getOrDefault(o, 0) == max) {
                ties.add(o);
            }
        }

        Core.ui.retainOnlyIncome(ties);

        Core.schedulers.runLater(() -> {
            IncomeOption chosen = pickRandom(ties);
            if (chosen != null) {
                Core.ui.highlightIncomeSelected(chosen.label, chosen.slot);
                session.setIncome(chosen);
                Core.notify.broadcast(
                        session.getParticipantsView(),
                        Sound.ENTITY_PLAYER_LEVELUP,
                        Notifier.PitchLevel.MID,
                        false,
                        "Income Multiplier selected : §a" + chosen.label
                );

                // set income multiplier
                double newMul = chosen.multiplier;
                for (UUID id : session.getParticipantsView()) {
                    double perPlayerIncome = Core.config.baseIncome * newMul;
                    session.getIncomeMap().put(id, perPlayerIncome);
                }
            }
            Core.schedulers.runLater(this::gotoVotingGameMode, 3 * 20L);
        }, 2 * 20L);
    }

    /* =========================================================
       Phase: GAME MODE voting
       ========================================================= */

    private void gotoVotingGameMode() {
        state = GameState.VOTING_GAME_MODE;
        acceptingVotes = true;
        votedMode.clear();
        modeVotes.clear();
        for (GameModeOption opt : GameModeOption.values()) {
            modeVotes.put(opt, 0);
        }

        // close previous GZ inventories
        Core.ui.closeAllGZViews();
        Core.ui.newGameMode();

        for (UUID id : session.getParticipantsView()) {
            Player pp = Bukkit.getPlayer(id);
            if (pp == null || !pp.isOnline()) continue;
            Core.notify.sound(pp, Sound.UI_BUTTON_CLICK, Notifier.PitchLevel.MID);
            Core.ui.openGameMode(pp);
        }

        Core.schedulers.runLater(() -> Core.notify.broadcast(
                session.getParticipantsView(),
                Sound.BLOCK_NOTE_BLOCK_BELL,
                Notifier.PitchLevel.OK,
                false,
                "Ending vote for game mode in §a3"
        ), 7 * 20L);

        Core.schedulers.runLater(() -> Core.notify.broadcast(
                session.getParticipantsView(),
                Sound.BLOCK_NOTE_BLOCK_BELL,
                Notifier.PitchLevel.OK,
                false,
                "Ending vote for game mode in §a2"
        ), 8 * 20L);

        Core.schedulers.runLater(() -> Core.notify.broadcast(
                session.getParticipantsView(),
                Sound.BLOCK_NOTE_BLOCK_BELL,
                Notifier.PitchLevel.OK,
                false,
                "Ending vote for game mode in §a1"
        ), 9 * 20L);

        Core.schedulers.runLater(this::finishGameModeVotePhase, 10 * 20L);
    }

    private void finishGameModeVotePhase() {
        acceptingVotes = false;

        int max = 0;
        for (GameModeOption o : GameModeOption.values()) {
            max = Math.max(max, modeVotes.getOrDefault(o, 0));
        }

        List<GameModeOption> ties = new ArrayList<>();
        for (GameModeOption o : GameModeOption.values()) {
            if (modeVotes.getOrDefault(o, 0) == max) {
                ties.add(o);
            }
        }

        Core.ui.retainOnlyGameMode(ties);

        Core.schedulers.runLater(() -> {
            GameModeOption chosen = pickRandom(ties);
            if (chosen != null) {
                Core.ui.highlightGameModeSelected(chosen.label, chosen.slot);
                session.setGameMode(chosen);
                Core.notify.broadcast(
                        session.getParticipantsView(),
                        Sound.ENTITY_PLAYER_LEVELUP,
                        Notifier.PitchLevel.MID,
                        false,
                        "Game Mode selected : §a" + chosen.label
                );
            }
            Core.schedulers.runLater(this::gotoCountdownBeforeStart, 3 * 20L);
        }, 2 * 20L);
    }

    /* =========================================================
       Phase: countdown before start -> running
       ========================================================= */

    private void gotoCountdownBeforeStart() {
        state = GameState.COUNTDOWN_BEFORE_START;
        Core.ui.closeAllGZViews();
        startCountdown(5, this::gotoRunning);
    }

    private void gotoRunning() {
        state = GameState.RUNNING;

        World w = session.getWorld();
        Location c = session.getCenter();
        if (w != null && c != null) {
            WorldBorder wb = w.getWorldBorder();
            wb.setCenter(c);
            wb.setSize(session.mapSize().size);
        }

        setUpGameRules();
        Core.loadoutService.giveInitialLoadouts(session.getParticipantsView());
        // 4) scoreboard show
        if (Core.scoreboardService != null) {
            Core.scoreboardService.showGameBoard(session);
        }

        ticksLeft = Core.config.matchDurationTicks;
        startScoreboardTick();

        for (UUID id : session.getParticipantsView()) {
            teleportPlayerRandomly(id);
        }

        Core.notify.broadcast(
                Bukkit.getOnlinePlayers(),
                Sound.ENTITY_ENDER_DRAGON_GROWL,
                Notifier.PitchLevel.MID,
                false,
                "&9----------------",
                "&eGroundZero Start!",
                "Map Size: &a" + session.mapSize().label,
                "Income: &a" + session.income().label,
                "Game Mode: &a" + session.gameMode().label,
                "&9----------------"
        );

        // TODO: start real game loop
    }

    /* =========================================================
       Voting entry points (from GUI clicks)
       ========================================================= */

    public void voteMapSize(UUID pid, MapSizeOption opt) {
        if (state != GameState.VOTING_MAP_SIZE || !acceptingVotes || opt == null) return;

        MapSizeOption prev = votedMapSize.put(pid, opt);
        if (prev != null) {
            mapVotes.put(prev, Math.max(0, mapVotes.get(prev) - 1));
        }
        mapVotes.put(opt, mapVotes.get(opt) + 1);

        Core.ui.refreshMapSizeVotes(
                mapVotes.get(MapSizeOption.SIZE_50),
                mapVotes.get(MapSizeOption.SIZE_100),
                mapVotes.get(MapSizeOption.SIZE_200),
                mapVotes.get(MapSizeOption.SIZE_400)
        );

        Player p = Bukkit.getPlayer(pid);
        if (p != null) {
            Core.notify.sound(p, Sound.UI_BUTTON_CLICK, Notifier.PitchLevel.HIGH);
        }
    }

    public void voteIncome(UUID pid, IncomeOption opt) {
        if (state != GameState.VOTING_INCOME_MULTIPLIER || !acceptingVotes || opt == null) return;

        IncomeOption prev = votedIncome.put(pid, opt);
        if (prev != null) {
            incomeVotes.put(prev, Math.max(0, incomeVotes.get(prev) - 1));
        }
        incomeVotes.put(opt, incomeVotes.get(opt) + 1);

        Core.ui.refreshIncomeVotes(
                incomeVotes.get(IncomeOption.X0_5),
                incomeVotes.get(IncomeOption.X1_0),
                incomeVotes.get(IncomeOption.X2_0),
                incomeVotes.get(IncomeOption.X4_0)
        );

        Player p = Bukkit.getPlayer(pid);
        if (p != null) {
            Core.notify.sound(p, Sound.UI_BUTTON_CLICK, Notifier.PitchLevel.HIGH);
        }
    }

    public void voteGameMode(UUID pid, GameModeOption opt) {
        if (state != GameState.VOTING_GAME_MODE || !acceptingVotes || opt == null) return;

        GameModeOption prev = votedMode.put(pid, opt);
        if (prev != null) {
            modeVotes.put(prev, Math.max(0, modeVotes.get(prev) - 1));
        }
        modeVotes.put(opt, modeVotes.get(opt) + 1);

        // current UI supports only STANDARD count
        Core.ui.refreshGameModeVotes(
                modeVotes.get(GameModeOption.STANDARD)
        );

        Player p = Bukkit.getPlayer(pid);
        if (p != null) {
            Core.notify.sound(p, Sound.UI_BUTTON_CLICK, Notifier.PitchLevel.HIGH);
        }
    }

    /* =========================================================
       Helpers
       ========================================================= */

    private <T> T pickRandom(List<T> list) {
        if (list == null || list.isEmpty()) return null;
        return list.get(RNG.nextInt(list.size()));
    }

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
        // session.income() 이 투표 전에 없을 수도 있으니까
        // 일단 multiplier는 1.0으로 두고, 투표 끝나고 다시 덮어써도 됨
        double sessionMul = 1.0;
        if (session.income() != null) {
            sessionMul = session.income().multiplier;
        }

        for (UUID id : session.getParticipantsView()) {
            // plasma
            session.getPlasmaMap().put(id, Core.config.basePlasma);

            // income: 기본 = baseIncomePerSecond * session multiplier
            double perPlayerIncome = Core.config.baseIncome * sessionMul;
            session.getIncomeMap().put(id, perPlayerIncome);

            // score
            session.getScoresMap().put(id, Core.config.baseScore);
        }
    }

    // GameManager 안

    private void startScoreboardTick() {
        // every tick
        Core.schedulers.runTimer(() -> {
            if (state != GameState.RUNNING) {
                return;
            }

            // time goes down 1 tick
            if (ticksLeft > 0) {
                ticksLeft -= 1;
            } else {
                endGame();
                return;
            }

            for (UUID id : session.getParticipantsView()) {

                // 1) per-player income (per second)
                double incomePerSec = (Core.scoreboardService != null)
                        ? Core.scoreboardService.getPerPlayerIncome(session, id)
                        : Core.config.baseIncome;

                // 2) convert to per-tick
                double incomePerTick = incomePerSec / 20.0;

                // 3) add to plasma
                double current = session.getPlasmaMap().getOrDefault(id, Core.config.basePlasma);
                double next = current + incomePerTick;
                session.getPlasmaMap().put(id, next);

                // 4) refresh sb this tick
                if (Core.scoreboardService != null) {
                    Core.scoreboardService.refreshFromSession(session, id, ticksLeft);
                }
            }

        }, 1L, 1L); // ← 1 tick delay, 1 tick period
    }

}
