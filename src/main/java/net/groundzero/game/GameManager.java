package net.groundzero.game;

import net.groundzero.service.GuiService;
import net.groundzero.ui.options.GameModeOption;
import net.groundzero.ui.options.IncomeOption;
import net.groundzero.ui.options.MapSizeOption;
import net.groundzero.util.Notifier;
import net.groundzero.util.Schedulers;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;

public final class GameManager {
    private final Schedulers sched;
    private final Notifier notify;
    private final GuiService ui;
    private final GameConfig config;

    private GameSession session; // current match selections

    // ----- voting tallies & per-player choices -----
    private final EnumMap<MapSizeOption, Integer> mapVotes    = new EnumMap<>(MapSizeOption.class);
    private final EnumMap<IncomeOption, Integer> incomeVotes  = new EnumMap<>(IncomeOption.class);
    private final EnumMap<GameModeOption, Integer> modeVotes  = new EnumMap<>(GameModeOption.class);

    private final Map<UUID, MapSizeOption>   votedMapSize = new HashMap<>();
    private final Map<UUID, IncomeOption>    votedIncome  = new HashMap<>();
    private final Map<UUID, GameModeOption>  votedMode    = new HashMap<>();

    private static final java.util.Random RNG = new java.util.Random();

    public GameManager(Schedulers sched, Notifier notify, GuiService ui, GameConfig config) {
        this.sched = Objects.requireNonNull(sched);
        this.notify = Objects.requireNonNull(notify);
        this.ui = Objects.requireNonNull(ui);
        this.config = Objects.requireNonNull(config);

        this.session = new GameSession();
    }

    private boolean acceptingVotes = false;

    private GameState state = GameState.IDLE;
    public GameState state() { return state; }
    public GameSession session() { return session; }

    /* =================== Commands =================== */

    public void forEachParticipant(java.util.function.Consumer<Player> action) {
        session.getParticipantsView().forEach(id -> {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) action.accept(p);
        });
    }

    /** Apply an action to every online spectator. */
    public void forEachSpectator(java.util.function.Consumer<Player> action) {
        session.getSpectatorsView().forEach(id -> {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) action.accept(p);
        });
    }

    /** Apply an action to every online player (global). */
    public void forAll(java.util.function.Consumer<Player> action) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p != null && p.isOnline()) action.accept(p);
        }
    }

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

        if (first) return "<none>";

        return sb.toString();
    }

    public void cancel(Player p) {
        switch(state) {
            case IDLE -> {
                if (p != null) notify.messageError(p, "There is no game starting");
            }
            case COUNTDOWN_BEFORE_VOTING,
                 VOTING_MAP_SIZE,
                 VOTING_INCOME_MULTIPLIER,
                 VOTING_GAME_MODE,
                 COUNTDOWN_BEFORE_START -> {
                notify.broadcastError(Sound.BLOCK_ANVIL_LAND, Notifier.PitchLevel.LOW, "GroundZero cancelled by &a" + p.getDisplayName());
                cancelAll();
            }

            case RUNNING,
                 ENDED -> {
                if (p != null) notify.messageError(p, "The game is already running");
            }

            default -> {}
        }
    }

    public void cancelAll() {
        state = GameState.IDLE;

        sched.cancelAll();

        ui.closeALlGZViews();

        session = new GameSession();
        Set<UUID> online = new java.util.HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            online.add(p.getUniqueId());
        }
        session.resetToAllSpectators(online);
        //TODO : remove others
    }

    /* =================== Phase transitions =================== */

    public void start(Player p) {
        switch(state) {
            case IDLE -> {
                session.snapshotParticipantsFromSpectators();
                notify.broadcastToAll(null, null, "Participants: " + namesOf(session.getParticipantsView()));
                gotoCountdownBeforeVoting(); //broadcast is in here
            }

            case COUNTDOWN_BEFORE_VOTING,
                 VOTING_MAP_SIZE,
                 VOTING_INCOME_MULTIPLIER,
                 VOTING_GAME_MODE,
                 COUNTDOWN_BEFORE_START -> {
                if (p != null) notify.messageError(p, "The game is already starting");
            }

            case RUNNING,
                 ENDED -> {
                if (p != null) notify.messageError(p, "The game is already running");
            }

            default -> {}
        }
    }

    private void gotoCountdownBeforeVoting() {
        state = GameState.COUNTDOWN_BEFORE_VOTING;
        startCountdown(5, this::gotoVotingMapSize);
    }

    private void startCountdown(int seconds, Runnable onDone) {
        if (seconds <= 0) {
            sched.runLater(onDone, 1L); // to next phase
            return;
        }
        notify.broadcast(
                Sound.BLOCK_NOTE_BLOCK_PLING, Notifier.PitchLevel.MID,
                "GroundZero starting in " + seconds
        );
        sched.runLater(() -> startCountdown(seconds - 1, onDone), 20L);
        // -1 second
    }

    /* ===== MapSize Voting ===== */
    private void gotoVotingMapSize() {
        state = GameState.VOTING_MAP_SIZE;
        acceptingVotes = true;

        votedMapSize.clear();
        for (MapSizeOption opt : MapSizeOption.values()) mapVotes.put(opt, 0);

        ui.newMapSize();
        forEachParticipant(p -> {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, Notifier.PitchLevel.MID.v);
            ui.openMapSize(p);
        });

        sched.runLater(() -> notify.broadcast(
                Sound.BLOCK_NOTE_BLOCK_PLING, Notifier.PitchLevel.MID, "Ending vote for map size in &a3"), 7 * 20L);
        sched.runLater(() -> notify.broadcast(
                Sound.BLOCK_NOTE_BLOCK_PLING, Notifier.PitchLevel.MID, "Ending vote for map size in &a2"), 8 * 20L);
        sched.runLater(() -> notify.broadcast(
                Sound.BLOCK_NOTE_BLOCK_PLING, Notifier.PitchLevel.MID, "Ending vote for map size in &a1"), 9 * 20L);

        sched.runLater(this::finishMapSizeVotePhase, 10 * 20L);
    }

    private void finishMapSizeVotePhase() {
        acceptingVotes = false;
        int max = 0;
        for (MapSizeOption o : MapSizeOption.values()) {
            max = Math.max(max, mapVotes.getOrDefault(o, 0));
        }
        java.util.List<MapSizeOption> ties = new java.util.ArrayList<>();
        for (MapSizeOption o : MapSizeOption.values()) {
            if (mapVotes.getOrDefault(o, 0) == max) ties.add(o);
        }

        ui.retainOnlyMapSize(ties);

        sched.runLater(() -> {
            MapSizeOption chosen= pickRandom(ties);
            ui.highlightMapSizeSelected(chosen.label, chosen.slot);
            session.setMapSize(chosen);
            notify.broadcast(Sound.ENTITY_PLAYER_LEVELUP, Notifier.PitchLevel.MID, "Map size selected : &a" + chosen.label);

            sched.runLater(this::gotoVotingIncome, 3 * 20L);
        }, 2 * 20L);
    }

    /* ===== Income Voting ===== */
    private void gotoVotingIncome() {
        state = GameState.VOTING_INCOME_MULTIPLIER;
        acceptingVotes = true;

        votedIncome.clear();
        for (IncomeOption opt : IncomeOption.values()) incomeVotes.put(opt, 0);

        ui.newIncome();
        forEachParticipant(p -> {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, Notifier.PitchLevel.MID.ordinal());
            ui.openIncome(p);
        });

        sched.runLater(() -> notify.broadcast(
                Sound.BLOCK_NOTE_BLOCK_PLING, Notifier.PitchLevel.MID, "Ending vote for income multiplier in &a3"), 7 * 20L);
        sched.runLater(() -> notify.broadcast(
                Sound.BLOCK_NOTE_BLOCK_PLING, Notifier.PitchLevel.MID, "Ending vote for income multiplier in &a2"), 8 * 20L);
        sched.runLater(() -> notify.broadcast(
                Sound.BLOCK_NOTE_BLOCK_PLING, Notifier.PitchLevel.MID, "Ending vote for income multiplier in &a1"), 9 * 20L);

        sched.runLater(this::finishIncomeVotePhase, 10 * 20L);
    }

    private void finishIncomeVotePhase() {
        acceptingVotes = false;
        int max = 0;
        for (IncomeOption o : IncomeOption.values()) {
            max = Math.max(max, incomeVotes.getOrDefault(o, 0));
        }
        java.util.List<IncomeOption> ties = new java.util.ArrayList<>();
        for (IncomeOption o : IncomeOption.values()) {
            if (incomeVotes.getOrDefault(o, 0) == max) ties.add(o);
        }

        ui.retainOnlyIncome(ties);

        sched.runLater(() -> {
            IncomeOption chosen = ties.get(RNG.nextInt(ties.size()));
            ui.highlightIncomeSelected(chosen.label, chosen.slot);
            session.setIncome(chosen);
            notify.broadcast(Sound.ENTITY_PLAYER_LEVELUP, Notifier.PitchLevel.MID, "Income Multiplier selected : &a" + chosen.label);

            sched.runLater(this::gotoVotingGameMode, 3 * 20L);
        }, 2 * 20L);
    }

    /* ===== GameMode Voting ===== */
    private void gotoVotingGameMode() {
        state = GameState.VOTING_GAME_MODE;
        acceptingVotes = true;

        votedMode.clear();
        for (GameModeOption opt : GameModeOption.values()) modeVotes.put(opt, 0);

        ui.closeALlGZViews(); // close income vote
        ui.newGameMode();
        forEachParticipant(p -> {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, Notifier.PitchLevel.MID.ordinal());
            ui.openGameMode(p);
        });

        sched.runLater(() -> notify.broadcast(
                Sound.BLOCK_NOTE_BLOCK_PLING, Notifier.PitchLevel.MID, "Ending vote for game mode in &a3"), 7 * 20L);
        sched.runLater(() -> notify.broadcast(
                Sound.BLOCK_NOTE_BLOCK_PLING, Notifier.PitchLevel.MID, "Ending vote for game mode in &a2"), 8 * 20L);
        sched.runLater(() -> notify.broadcast(
                Sound.BLOCK_NOTE_BLOCK_PLING, Notifier.PitchLevel.MID, "Ending vote for game mode in &a1"), 9 * 20L);

        sched.runLater(this::finishGameModeVotePhase, 10 * 20L);
    }

    private void finishGameModeVotePhase() {
        acceptingVotes = false;
        int max = 0;
        for (GameModeOption o : GameModeOption.values()) {
            max = Math.max(max, modeVotes.getOrDefault(o, 0));
        }
        java.util.List<GameModeOption> ties = new java.util.ArrayList<>();
        for (GameModeOption o : GameModeOption.values()) {
            if (modeVotes.getOrDefault(o, 0) == max) ties.add(o);
        }

        ui.retainOnlyGameMode(ties);

        sched.runLater(() -> {
            GameModeOption chosen = ties.get(RNG.nextInt(ties.size()));
            ui.highlightGameModeSelected(chosen.label, chosen.slot);
            session.setGameMode(chosen);
            notify.broadcast(Sound.ENTITY_PLAYER_LEVELUP, Notifier.PitchLevel.MID, "Game Mode selected : &a" + chosen.label);

            sched.runLater(this::gotoCountdownBeforeStart, 3 * 20L);
        }, 2 * 20L);
    }

    private void gotoCountdownBeforeStart() {
        state = GameState.COUNTDOWN_BEFORE_START;
        ui.closeALlGZViews();
        startCountdown(5, this::gotoRunning);
    }

    private void gotoRunning() {
        state = GameState.RUNNING;

        notify.broadcast(
                Sound.ENTITY_ENDER_DRAGON_GROWL,
                Notifier.PitchLevel.MID,
                "Match is starting!",
                "Map Size: " + session.mapSize(),
                "Income: " + session.income(),
                "Game Mode: " + session.gameMode());

        /* ===================== TODO: real game start =====================
           - Teleport players to battlefield spawn based on map size
           - Apply rules for selected game mode
           - Give loadouts, start match timer, scoreboard, income ticks
           - Enable combat rules
           =============================================================== */
    }

    /* =================== Voting API (from GUI) =================== */

    public void voteMapSize(UUID pid, MapSizeOption opt) {
        if (state != GameState.VOTING_MAP_SIZE || !acceptingVotes || opt == null) return;
        MapSizeOption prev = votedMapSize.put(pid, opt);
        if (prev != null) mapVotes.put(prev, Math.max(0, mapVotes.get(prev) - 1));
        mapVotes.put(opt, mapVotes.get(opt) + 1);
        ui.refreshMapSizeVotes(
                mapVotes.get(MapSizeOption.SIZE_50),
                mapVotes.get(MapSizeOption.SIZE_100),
                mapVotes.get(MapSizeOption.SIZE_200),
                mapVotes.get(MapSizeOption.SIZE_400)
        );
        Bukkit.getPlayer(pid).playSound(Bukkit.getPlayer(pid).getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, Notifier.PitchLevel.HIGH.v);
    }

    public void voteIncome(UUID pid, IncomeOption opt) {
        if (state != GameState.VOTING_INCOME_MULTIPLIER || !acceptingVotes || opt == null) return;
        IncomeOption prev = votedIncome.put(pid, opt);
        if (prev != null) incomeVotes.put(prev, Math.max(0, incomeVotes.get(prev) - 1));
        incomeVotes.put(opt, incomeVotes.get(opt) + 1);
        ui.refreshIncomeVotes(
                incomeVotes.get(IncomeOption.X0_5),
                incomeVotes.get(IncomeOption.X1_0),
                incomeVotes.get(IncomeOption.X2_0),
                incomeVotes.get(IncomeOption.X4_0)
        );
        Bukkit.getPlayer(pid).playSound(Bukkit.getPlayer(pid).getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, Notifier.PitchLevel.HIGH.v);
    }

    public void voteGameMode(UUID pid, GameModeOption opt) {
        if (state != GameState.VOTING_GAME_MODE || !acceptingVotes || opt == null) return;
        GameModeOption prev = votedMode.put(pid, opt);
        if (prev != null) modeVotes.put(prev, Math.max(0, modeVotes.get(prev) - 1));
        modeVotes.put(opt, modeVotes.get(opt) + 1);
        ui.refreshGameModeVotes(modeVotes.get(GameModeOption.STANDARD));
        Bukkit.getPlayer(pid).playSound(Bukkit.getPlayer(pid).getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, Notifier.PitchLevel.HIGH.v);
    }

    private <T> T pickRandom(java.util.List<T> list) {
        if (list == null || list.isEmpty()) return null;
        return list.get(RNG.nextInt(list.size()));
    }
}
