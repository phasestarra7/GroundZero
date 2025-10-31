package net.groundzero.game;

import net.groundzero.app.Core;
import net.groundzero.ui.options.GameModeOption;
import net.groundzero.ui.options.IncomeOption;
import net.groundzero.ui.options.MapSizeOption;
import net.groundzero.util.Notifier;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;

public class GameManager {

    // core game session (recreated on cancelAll)
    private GameSession session = new GameSession();

    private GameState state = GameState.IDLE;
    private boolean acceptingVotes = false;

    // voting maps
    private final Map<MapSizeOption, Integer> mapVotes = new EnumMap<>(MapSizeOption.class);
    private final Map<IncomeOption, Integer> incomeVotes = new EnumMap<>(IncomeOption.class);
    private final Map<GameModeOption, Integer> modeVotes = new EnumMap<>(GameModeOption.class);

    // who voted what
    private final Map<UUID, MapSizeOption> votedMapSize = new HashMap<>();
    private final Map<UUID, IncomeOption> votedIncome = new HashMap<>();
    private final Map<UUID, GameModeOption> votedMode = new HashMap<>();

    // random picker
    private static final Random RNG = new Random();

    public GameManager() {}

    public GameSession session() {
        return session;
    }

    public GameState state() {
        return state;
    }

    /* =================== Commands / helpers =================== */

    /** Apply an action to every online participant. */
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
        switch (state) {
            case IDLE -> {
                if (p != null) Core.notify.messageError(p, "There is no game starting.");
            }
            case COUNTDOWN_BEFORE_VOTING,
                 VOTING_MAP_SIZE,
                 VOTING_INCOME_MULTIPLIER,
                 VOTING_GAME_MODE,
                 COUNTDOWN_BEFORE_START -> {
                // broadcast error + sound
                Core.notify.broadcastToAllError(Sound.BLOCK_ANVIL_LAND, Notifier.PitchLevel.LOW, "GroundZero cancelled by " + (p != null ? p.getDisplayName() : "server"));
                cancelAll();
            }
            case RUNNING,
                 ENDED -> {
                if (p != null) Core.notify.messageError(p, "The game is already running.");
            }
            default -> {
            }
        }
    }

    public void cancelAll() {
        state = GameState.IDLE;

        // cancel all scheduled tasks related to this plugin
        Core.schedulers.cancelAll();

        // close all open GZ GUI views
        Core.ui.closeALlGZViews();

        // recreate session
        session = new GameSession();

        // re-register online players as spectators
        Set<UUID> online = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            online.add(p.getUniqueId());
        }
        session.resetToAllSpectators(online);
        // TODO: remove others if needed
    }

    /* =================== Phase transitions =================== */

    public void start(Player p) {
        switch (state) {
            case IDLE -> {
                // move all current spectators into participants
                session.snapshotParticipantsFromSpectators();

                // tell everyone
                Core.notify.broadcastToAll(null, null, "Participants: " + namesOf(session.getParticipantsView()));

                gotoCountdownBeforeVoting(); // broadcast is in here
            }

            case COUNTDOWN_BEFORE_VOTING,
                 VOTING_MAP_SIZE,
                 VOTING_INCOME_MULTIPLIER,
                 VOTING_GAME_MODE,
                 COUNTDOWN_BEFORE_START -> {
                if (p != null) Core.notify.messageError(p, "The game is already starting.");
            }

            case RUNNING,
                 ENDED -> {
                if (p != null) Core.notify.messageError(p, "The game is already running.");
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
            Core.schedulers.runLater(onDone, 1L); // to next phase
            return;
        }

        Core.notify.broadcast(Sound.BLOCK_NOTE_BLOCK_PLING, Notifier.PitchLevel.MID,"GroundZero starting in " + seconds);

        // -1 second
        Core.schedulers.runLater(() -> startCountdown(seconds - 1, onDone), 20L);
    }

    /* ===== MapSize Voting ===== */
    private void gotoVotingMapSize() {
        state = GameState.VOTING_MAP_SIZE;
        acceptingVotes = true;

        votedMapSize.clear();
        mapVotes.clear();
        for (MapSizeOption opt : MapSizeOption.values()) mapVotes.put(opt, 0);

        Core.ui.newMapSize();
        forEachParticipant(p -> {
            Core.notify.sound(p, Sound.UI_BUTTON_CLICK, Notifier.PitchLevel.MID);
            Core.ui.openMapSize(p);
        });

        Core.schedulers.runLater(() ->
                        Core.notify.broadcast(Sound.BLOCK_NOTE_BLOCK_BELL, Notifier.PitchLevel.OK,"Ending vote for map size in §a3"),
                7 * 20L);
        Core.schedulers.runLater(() ->
                        Core.notify.broadcast(Sound.BLOCK_NOTE_BLOCK_BELL, Notifier.PitchLevel.OK,"Ending vote for map size in §a2"),
                8 * 20L);
        Core.schedulers.runLater(() ->
                        Core.notify.broadcast(Sound.BLOCK_NOTE_BLOCK_BELL, Notifier.PitchLevel.OK,"Ending vote for map size in §a1"),
                9 * 20L);

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
            if (mapVotes.getOrDefault(o, 0) == max) ties.add(o);
        }

        Core.ui.retainOnlyMapSize(ties);

        Core.schedulers.runLater(() -> {
            MapSizeOption chosen = pickRandom(ties);
            if (chosen != null) {
                Core.ui.highlightMapSizeSelected(chosen.label, chosen.slot);
                session.setMapSize(chosen);
                Core.notify.broadcast(Sound.ENTITY_PLAYER_LEVELUP, Notifier.PitchLevel.MID,"Map size selected : §a" + chosen.label);
            }

            Core.schedulers.runLater(this::gotoVotingIncome, 3 * 20L);
        }, 2 * 20L);
    }

    /* ===== Income Voting ===== */
    private void gotoVotingIncome() {
        state = GameState.VOTING_INCOME_MULTIPLIER;
        acceptingVotes = true;

        votedIncome.clear();
        incomeVotes.clear();
        for (IncomeOption opt : IncomeOption.values()) incomeVotes.put(opt, 0);

        Core.ui.newIncome();
        forEachParticipant(p -> {
            Core.notify.sound(p, Sound.UI_BUTTON_CLICK, Notifier.PitchLevel.MID);
            Core.ui.openIncome(p);
        });

        Core.schedulers.runLater(() ->
                        Core.notify.broadcast(Sound.BLOCK_NOTE_BLOCK_BELL, Notifier.PitchLevel.OK,"Ending vote for income multiplier in §a3"),
                7 * 20L);
        Core.schedulers.runLater(() ->
                        Core.notify.broadcast(Sound.BLOCK_NOTE_BLOCK_BELL, Notifier.PitchLevel.OK,"Ending vote for income multiplier in §a2"),
                8 * 20L);
        Core.schedulers.runLater(() ->
                        Core.notify.broadcast(Sound.BLOCK_NOTE_BLOCK_BELL, Notifier.PitchLevel.OK,"Ending vote for income multiplier in §a1"),
                9 * 20L);

        Core.schedulers.runLater(this::finishIncomeVotePhase, 10 * 20L);
    }

    private void finishIncomeVotePhase() {
        acceptingVotes = false;

        int max = 0;
        for (IncomeOption o : IncomeOption.values()) {
            max = Math.max(max, incomeVotes.getOrDefault(o, 0));
        }
        List<IncomeOption> ties = new ArrayList<>();
        for (IncomeOption o : IncomeOption.values()) {
            if (incomeVotes.getOrDefault(o, 0) == max) ties.add(o);
        }

        Core.ui.retainOnlyIncome(ties);

        Core.schedulers.runLater(() -> {
            IncomeOption chosen = pickRandom(ties);
            if (chosen != null) {
                Core.ui.highlightIncomeSelected(chosen.label, chosen.slot);
                session.setIncome(chosen);
                Core.notify.broadcast(Sound.ENTITY_PLAYER_LEVELUP,Notifier.PitchLevel.MID,"Income Multiplier selected : §a" + chosen.label);
            }

            Core.schedulers.runLater(this::gotoVotingGameMode, 3 * 20L);
        }, 2 * 20L);
    }

    /* ===== GameMode Voting ===== */
    private void gotoVotingGameMode() {
        state = GameState.VOTING_GAME_MODE;
        acceptingVotes = true;

        votedMode.clear();
        modeVotes.clear();
        for (GameModeOption opt : GameModeOption.values()) modeVotes.put(opt, 0);

        // close previous vote GUIs
        Core.ui.closeALlGZViews();
        Core.ui.newGameMode();
        forEachParticipant(p -> {
            Core.notify.sound(p, Sound.UI_BUTTON_CLICK, Notifier.PitchLevel.MID);
            Core.ui.openGameMode(p); // fixed from openIncome
        });

        Core.schedulers.runLater(() ->
                        Core.notify.broadcast(Sound.BLOCK_NOTE_BLOCK_BELL, Notifier.PitchLevel.OK,"Ending vote for game mode in §a3"),
                7 * 20L);
        Core.schedulers.runLater(() ->
                        Core.notify.broadcast(Sound.BLOCK_NOTE_BLOCK_BELL, Notifier.PitchLevel.OK,"Ending vote for game mode in §a2"),
                8 * 20L);
        Core.schedulers.runLater(() ->
                        Core.notify.broadcast(Sound.BLOCK_NOTE_BLOCK_BELL, Notifier.PitchLevel.OK,"Ending vote for game mode in §a1"),
                9 * 20L);

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
            if (modeVotes.getOrDefault(o, 0) == max) ties.add(o);
        }

        Core.ui.retainOnlyGameMode(ties);

        Core.schedulers.runLater(() -> {
            GameModeOption chosen = pickRandom(ties);
            if (chosen != null) {
                Core.ui.highlightGameModeSelected(chosen.label, chosen.slot);
                session.setGameMode(chosen);
                Core.notify.broadcast(Sound.ENTITY_PLAYER_LEVELUP,Notifier.PitchLevel.MID,"Game Mode selected : §a" + chosen.label);
            }

            Core.schedulers.runLater(this::gotoCountdownBeforeStart, 3 * 20L);
        }, 2 * 20L);
    }

    private void gotoCountdownBeforeStart() {
        state = GameState.COUNTDOWN_BEFORE_START;
        Core.ui.closeALlGZViews();
        startCountdown(5, this::gotoRunning);
    }

    private void gotoRunning() {
        state = GameState.RUNNING;

        Core.notify.broadcastToAll(Sound.ENTITY_ENDER_DRAGON_GROWL, Notifier.PitchLevel.MID,
                "&9----------------",
                "&eGroundZero Start!",
                "Map Size: &a" + session.mapSize().label,
                "Income: &a" + session.income().label,
                "Game Mode: &a" + session.gameMode().label,
                "&9----------------"
                );
        // TODO: real game start
    }

    /* =================== Voting API (from GUI) =================== */

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

        // currently only standard in UI, keep call for compatibility
        Core.ui.refreshGameModeVotes(modeVotes.get(GameModeOption.STANDARD));

        Player p = Bukkit.getPlayer(pid);
        if (p != null) {
            Core.notify.sound(p, Sound.UI_BUTTON_CLICK, Notifier.PitchLevel.HIGH);
        }
    }

    private <T> T pickRandom(List<T> list) {
        if (list == null || list.isEmpty()) return null;
        return list.get(RNG.nextInt(list.size()));
    }
}
